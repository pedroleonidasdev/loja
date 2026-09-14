"""
Lógica compartilhada de impressão de etiquetas para a impressora térmica
Goldensky 80mm. Não faz leitura de stdin nem HTTP — isso fica por conta de
quem importa este módulo (goldensky-etiquetas.py para uso manual via CLI,
goldensky-agente.py para o serviço HTTP local que o frontend chama).

Para cada etiqueta:
  1. Monta uma página de PDF no tamanho da etiqueta (nome + preço + código de barras) com reportlab.
  2. Rasteriza essa página em PNG a 203 dpi com pdftoppm (poppler-utils).
  3. Converte o raster em comandos RAW ESC/POS (GS v 0) para a Goldensky.

O resultado é enviado para a fila CUPS "Goldensky-80" via `lp -o raw`.

Dependências (Linux Mint / Ubuntu):
  sudo apt install -y cups-client python3 python3-pil python3-reportlab poppler-utils
"""

import io
import os
import subprocess
import tempfile

from PIL import Image
from reportlab.graphics.barcode import code128
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas


class EtiquetaError(Exception):
    """Erro esperado (dados inválidos, impressora offline, etc) — mensagem é segura para mostrar ao usuário."""


class CupomError(Exception):
    """Erro esperado ao montar/enviar o cupom não fiscal — mensagem é segura para mostrar ao usuário."""


# --- Configuração física da etiqueta -----------------------------------------
# Valores padrão — usados quando o pedido de impressão não informa dimensão
# própria. O lote original usado neste projeto é de etiquetas adesivas
# pré-cortadas de 60 x 30mm. O frontend permite ajustar isso por lote de
# etiquetas diferente, sem precisar mexer no código.
DEFAULT_LABEL_WIDTH_MM = 60
DEFAULT_LABEL_HEIGHT_MM = 30
DEFAULT_GAP_MM = 4  # espaçamento entre uma etiqueta e a próxima, medido fisicamente

DPI = 203  # resolução nativa da cabeça de impressão da Goldensky 80mm

# limites de sanidade — a cabeça de impressão da Goldensky-80 imprime no
# máximo ~80mm de largura; abaixo disso é só para evitar valor absurdo digitado
LARGURA_MIN_MM, LARGURA_MAX_MM = 20, 80
ALTURA_MIN_MM, ALTURA_MAX_MM = 10, 150
ESPACAMENTO_MIN_MM, ESPACAMENTO_MAX_MM = 0, 30

CUPS_QUEUE = os.environ.get("GOLDENSKY_CUPS_QUEUE", "Goldensky-80")
DRY_RUN = os.environ.get("GOLDENSKY_DRY_RUN") == "1"
DRY_RUN_OUTPUT = os.environ.get("GOLDENSKY_DRY_RUN_OUTPUT", "/tmp/goldensky-etiquetas-teste.bin")

MAX_ETIQUETAS_POR_LOTE = 100

# --- Configuração do cupom não fiscal (recibo de venda) ----------------------
NOME_LOJA = os.environ.get("GOLDENSKY_NOME_LOJA", "Presente de Deus")
TELEFONE_LOJA = os.environ.get("GOLDENSKY_TELEFONE_LOJA", "(61) 3264-0078")
ENDERECO_LOJA = os.environ.get("GOLDENSKY_ENDERECO_LOJA", "CLN 7 Bloco B Lote 1 Loja 4")
INSTAGRAM_LOJA = os.environ.get("GOLDENSKY_INSTAGRAM_LOJA", "@presentedeusartigoscatolicos")
LARGURA_CUPOM_COLS = int(os.environ.get("GOLDENSKY_CUPOM_COLUNAS", "48"))  # bobina 80mm, fonte padrão
CODEPAGE_CUPOM = "cp850"  # tabela de caracteres com acentuação em português
DRY_RUN_OUTPUT_CUPOM = os.environ.get(
    "GOLDENSKY_DRY_RUN_OUTPUT_CUPOM", "/tmp/goldensky-cupom-teste.bin"
)

_ESC_INIT = "\x1b\x40"
_ESC_TABELA_CP850 = "\x1b\x74\x02"
_ESC_ALINHAR_ESQUERDA = "\x1b\x61\x00"
_ESC_NEGRITO_ON = "\x1b\x45\x01"
_ESC_NEGRITO_OFF = "\x1b\x45\x00"
_ESC_AVANCAR_E_CORTAR = "\x1b\x64\x04" + "\x1d\x56\x01"  # avança 4 linhas, corte parcial


def _validar_dimensoes(largura_mm: float, altura_mm: float, espacamento_mm: float) -> None:
    if not (LARGURA_MIN_MM <= largura_mm <= LARGURA_MAX_MM):
        raise EtiquetaError(
            f"Largura da etiqueta deve estar entre {LARGURA_MIN_MM}mm e {LARGURA_MAX_MM}mm "
            f"(a Goldensky-80 imprime no máximo {LARGURA_MAX_MM}mm de largura)"
        )
    if not (ALTURA_MIN_MM <= altura_mm <= ALTURA_MAX_MM):
        raise EtiquetaError(f"Altura da etiqueta deve estar entre {ALTURA_MIN_MM}mm e {ALTURA_MAX_MM}mm")
    if not (ESPACAMENTO_MIN_MM <= espacamento_mm <= ESPACAMENTO_MAX_MM):
        raise EtiquetaError(f"Espaçamento entre etiquetas deve estar entre {ESPACAMENTO_MIN_MM}mm e {ESPACAMENTO_MAX_MM}mm")


def formatar_preco(valor) -> str:
    try:
        valor = float(valor)
    except (TypeError, ValueError):
        return "R$ 0,00"
    texto = f"{valor:,.2f}"
    # 12,345.67 -> 12.345,67 (formato brasileiro)
    texto = texto.replace(",", "X").replace(".", ",").replace("X", ".")
    return f"R$ {texto}"


def montar_pdf_etiqueta(
        nome: str,
        preco_formatado: str,
        codigo_barras: str,
        largura_mm: float = DEFAULT_LABEL_WIDTH_MM,
        altura_mm: float = DEFAULT_LABEL_HEIGHT_MM,
) -> bytes:
    """Gera um PDF de uma página no tamanho exato da etiqueta.
    O layout (posições, fontes, código de barras) foi calibrado para 60x30mm,
    compacto e com o código de barras fino, para caber bem na bobina de
    etiquetas de preço; para outras dimensões, tudo escala proporcionalmente
    a partir desse ponto."""
    buffer = io.BytesIO()
    largura = largura_mm * mm
    altura = altura_mm * mm
    c = canvas.Canvas(buffer, pagesize=(largura, altura))

    escala_v = altura_mm / DEFAULT_LABEL_HEIGHT_MM
    escala_h = largura_mm / DEFAULT_LABEL_WIDTH_MM

    fonte_nome = max(6, round(8 * escala_v))
    fonte_preco = max(7, round(9 * escala_v))
    fonte_codigo = max(5, round(6 * escala_v))

    # Nome do produto — quebra em até 2 linhas se for muito comprido
    c.setFont("Helvetica-Bold", fonte_nome)
    max_chars = max(10, round(30 * escala_h))
    linha1, linha2 = nome[:max_chars], nome[max_chars:max_chars * 2]
    c.drawCentredString(largura / 2, altura - 4.2 * mm * escala_v, linha1)
    if linha2:
        c.drawCentredString(largura / 2, altura - 7.4 * mm * escala_v, linha2)

    # Preço
    c.setFont("Helvetica-Bold", fonte_preco)
    c.drawCentredString(largura / 2, altura - 11 * mm * escala_v, preco_formatado)

    # Código de barras (Code128) fino e compacto — proporção parecida com
    # etiquetas pequenas de barrinha de chocolate. Atenção: a impressora
    # Goldensky-80 imprime a 203dpi (~0,125mm por ponto), então módulos abaixo
    # de ~0,20mm ficam sujeitos a arredondamento de 1-2 pontos na rasterização;
    # teste a leitura no leitor de código de barras da loja antes de imprimir
    # um lote grande — se o scanner não ler bem, volte para 0.22mm.
    barcode = code128.Code128(codigo_barras, barHeight=8 * mm * escala_v, barWidth=0.18 * mm * escala_h)
    barcode_largura = barcode.width
    barcode.drawOn(c, (largura - barcode_largura) / 2, 2 * mm * escala_v)

    c.setFont("Helvetica", fonte_codigo)
    c.drawCentredString(largura / 2, 0.6 * mm * escala_v, codigo_barras)

    c.showPage()
    c.save()
    return buffer.getvalue()


def rasterizar_pdf(pdf_bytes: bytes) -> Image.Image:
    """Converte a página única do PDF em um bitmap 1-bit via pdftoppm (poppler-utils)."""
    with tempfile.TemporaryDirectory() as tmp:
        pdf_path = os.path.join(tmp, "etiqueta.pdf")
        png_prefix = os.path.join(tmp, "etiqueta")
        with open(pdf_path, "wb") as f:
            f.write(pdf_bytes)

        resultado = subprocess.run(
            ["pdftoppm", "-png", "-r", str(DPI), pdf_path, png_prefix],
            capture_output=True,
        )
        if resultado.returncode != 0:
            raise EtiquetaError(
                f"Falha ao rasterizar etiqueta (pdftoppm): {resultado.stderr.decode(errors='ignore')}"
            )

        png_path = f"{png_prefix}-1.png"
        if not os.path.exists(png_path):
            # em algumas versões o pdftoppm não usa o sufixo "-1" para página única
            candidatos = [f for f in os.listdir(tmp) if f.endswith(".png")]
            if not candidatos:
                raise EtiquetaError("pdftoppm não gerou nenhuma imagem para a etiqueta")
            png_path = os.path.join(tmp, candidatos[0])

        imagem = Image.open(png_path).convert("L")
        # limiar simples de preto/branco — nome, preço e código de barras são
        # traços sólidos, então um threshold fixo funciona bem aqui
        imagem = imagem.point(lambda p: 255 if p > 160 else 0, mode="1")
        return imagem


def imagem_para_escpos(imagem: Image.Image) -> bytes:
    """Converte um bitmap 1-bit em comandos RAW ESC/POS (GS v 0 - raster bit image)."""
    largura, altura = imagem.size
    bytes_por_linha = (largura + 7) // 8

    dados = bytearray()
    pixels = imagem.load()
    for y in range(altura):
        linha = bytearray(bytes_por_linha)
        for x in range(largura):
            # 0 = preto no modo "1" do Pillow após o threshold acima
            if pixels[x, y] == 0:
                linha[x // 8] |= 0x80 >> (x % 8)
        dados.extend(linha)

    xl = bytes_por_linha & 0xFF
    xh = (bytes_por_linha >> 8) & 0xFF
    yl = altura & 0xFF
    yh = (altura >> 8) & 0xFF

    comando = bytearray()
    comando += b"\x1d\x76\x30\x00"  # GS v 0 m=0 (modo normal)
    comando += bytes([xl, xh, yl, yh])
    comando += dados
    return bytes(comando)


def montar_stream_escpos(
        etiquetas: list,
        largura_mm: float = DEFAULT_LABEL_WIDTH_MM,
        altura_mm: float = DEFAULT_LABEL_HEIGHT_MM,
        espacamento_mm: float = DEFAULT_GAP_MM,
) -> bytes:
    if not etiquetas:
        raise EtiquetaError("Nenhuma etiqueta informada")
    if len(etiquetas) > MAX_ETIQUETAS_POR_LOTE:
        raise EtiquetaError(f"O lote aceita no máximo {MAX_ETIQUETAS_POR_LOTE} etiquetas por requisição")

    gap_dots = round(espacamento_mm * DPI / 25.4)

    stream = bytearray()
    stream += b"\x1b\x40"  # ESC @ — inicializa a impressora

    for i, item in enumerate(etiquetas):
        nome = str(item.get("nome", "")).strip()
        codigo_barras = str(item.get("codigoBarras", "")).strip()
        preco_formatado = formatar_preco(item.get("precoVenda"))

        if not nome or not codigo_barras:
            raise EtiquetaError(f"Etiqueta {i + 1}: nome e código de barras são obrigatórios")

        pdf_bytes = montar_pdf_etiqueta(nome, preco_formatado, codigo_barras, largura_mm, altura_mm)
        imagem = rasterizar_pdf(pdf_bytes)
        stream += imagem_para_escpos(imagem)

        # avanço de papel entre etiquetas (não aplica depois da última)
        if i < len(etiquetas) - 1:
            linhas_de_avanco = max(1, gap_dots // 24)
            stream += bytes([0x1b, 0x64, linhas_de_avanco])  # ESC d n — feed n linhas

    stream += bytes([0x1b, 0x64, 3])  # folga final antes do corte manual
    return bytes(stream)


def enviar_para_cups(dados: bytes) -> None:
    with tempfile.NamedTemporaryFile(delete=False, suffix=".bin") as tmp:
        tmp.write(dados)
        tmp_path = tmp.name

    try:
        resultado = subprocess.run(
            ["lp", "-d", CUPS_QUEUE, "-o", "raw", tmp_path],
            capture_output=True,
        )
        if resultado.returncode != 0:
            raise EtiquetaError(
                f"Falha ao enviar para a fila CUPS '{CUPS_QUEUE}': "
                f"{resultado.stderr.decode(errors='ignore')}"
            )
    finally:
        os.unlink(tmp_path)


def imprimir_etiquetas(
        etiquetas: list,
        largura_mm: float = None,
        altura_mm: float = None,
        espacamento_mm: float = None,
) -> int:
    """Monta e envia o lote de etiquetas. Retorna a quantidade impressa.
    Lança EtiquetaError com uma mensagem segura para mostrar ao usuário."""
    largura_mm = largura_mm if largura_mm is not None else DEFAULT_LABEL_WIDTH_MM
    altura_mm = altura_mm if altura_mm is not None else DEFAULT_LABEL_HEIGHT_MM
    espacamento_mm = espacamento_mm if espacamento_mm is not None else DEFAULT_GAP_MM

    _validar_dimensoes(largura_mm, altura_mm, espacamento_mm)

    dados = montar_stream_escpos(etiquetas, largura_mm, altura_mm, espacamento_mm)

    if DRY_RUN:
        with open(DRY_RUN_OUTPUT, "wb") as f:
            f.write(dados)
        return len(etiquetas)

    enviar_para_cups(dados)
    return len(etiquetas)


# --- Impressão do cupom não fiscal (recibo de venda) -------------------------

def _linha_cupom(esquerda: str, direita: str = "", largura: int = LARGURA_CUPOM_COLS) -> str:
    """Uma linha do cupom com um texto à esquerda e outro à direita, preenchendo
    o espaço entre eles; trunca a esquerda se não couber os dois na largura."""
    espaco = largura - len(esquerda) - len(direita)
    if espaco < 1:
        esquerda = esquerda[: max(0, largura - len(direita) - 1)]
        espaco = largura - len(esquerda) - len(direita)
    return f"{esquerda}{' ' * espaco}{direita}"


def _quebrar_texto_cupom(texto: str, largura: int) -> list:
    """Quebra uma linha longa (nome de produto, por exemplo) em várias linhas
    sem estourar a largura da bobina."""
    palavras = texto.split()
    linhas, atual = [], ""
    for palavra in palavras:
        candidato = f"{atual} {palavra}".strip()
        if len(candidato) > largura and atual:
            linhas.append(atual)
            atual = palavra
        else:
            atual = candidato
    if atual:
        linhas.append(atual)
    return linhas or [""]


def _formatar_data_hora_cupom(valor) -> str:
    """Aceita a data/hora em ISO 8601 (formato enviado pelo frontend) e
    devolve dd/mm/aaaa hh:mm; se não conseguir interpretar, devolve o
    valor original em vez de falhar a impressão por causa disso."""
    if not valor:
        return ""
    from datetime import datetime

    try:
        dt = datetime.fromisoformat(str(valor).replace("Z", "+00:00"))
        return dt.strftime("%d/%m/%Y %H:%M")
    except ValueError:
        return str(valor)


def montar_stream_cupom(venda: dict) -> bytes:
    """Monta o cupom não fiscal (recibo em texto) de uma venda e devolve os
    bytes RAW ESC/POS prontos para a Goldensky. Lança CupomError com
    mensagem segura para mostrar ao usuário quando os dados são inválidos."""
    if not isinstance(venda, dict) or not venda:
        raise CupomError("Dados da venda ausentes ou inválidos")

    itens = venda.get("itens") or []
    if not itens:
        raise CupomError("A venda não possui itens para imprimir")

    largura = LARGURA_CUPOM_COLS
    linhas = []

    linhas.append(NOME_LOJA.upper().center(largura))
    if ENDERECO_LOJA:
        linhas.append(ENDERECO_LOJA.center(largura))
    if TELEFONE_LOJA:
        linhas.append(f"WhatsApp: {TELEFONE_LOJA}".center(largura))
    linhas.append("Cupom não fiscal".center(largura))
    linhas.append("-" * largura)

    venda_id = venda.get("id")
    cabecalho_esq = f"Venda #{venda_id}" if venda_id not in (None, "") else ""
    linhas.append(_linha_cupom(cabecalho_esq, _formatar_data_hora_cupom(venda.get("dataHora")), largura))

    operador = str(venda.get("usuarioNome", "")).strip()
    if operador:
        linhas.append(f"Operador: {operador}")

    linhas.append("-" * largura)

    for item in itens:
        nome = str(item.get("produtoNome", "")).strip() or "Produto"
        quantidade = item.get("quantidade", 0)
        try:
            quantidade_fmt = f"{float(quantidade):g}"
        except (TypeError, ValueError):
            quantidade_fmt = str(quantidade)

        preco_unitario = formatar_preco(item.get("precoUnitario"))
        subtotal_item = formatar_preco(item.get("subtotal"))

        texto_item = _quebrar_texto_cupom(f"{quantidade_fmt}x {nome}", largura - 1)
        for linha_extra in texto_item[:-1]:
            linhas.append(linha_extra)
        linhas.append(_linha_cupom(texto_item[-1], subtotal_item, largura))
        linhas.append(f"   (unit. {preco_unitario})")

    linhas.append("-" * largura)

    subtotal = venda.get("subtotal")
    if subtotal is not None:
        linhas.append(_linha_cupom("Subtotal", formatar_preco(subtotal), largura))

    valor_desconto = venda.get("valorDesconto")
    try:
        tem_desconto = float(valor_desconto) > 0
    except (TypeError, ValueError):
        tem_desconto = False
    if tem_desconto:
        percentual = venda.get("percentualDesconto")
        rotulo = "Desconto"
        try:
            if percentual not in (None, ""):
                rotulo = f"Desconto ({float(percentual):g}%)"
        except (TypeError, ValueError):
            pass
        linhas.append(_linha_cupom(rotulo, f"-{formatar_preco(valor_desconto)}", largura))

    total = venda.get("total", subtotal)
    linhas.append(_ESC_NEGRITO_ON + _linha_cupom("TOTAL", formatar_preco(total), largura) + _ESC_NEGRITO_OFF)
    linhas.append("")

    pagamentos = venda.get("pagamentos") or []
    if pagamentos:
        linhas.append("Pagamentos:")
        for pagamento in pagamentos:
            forma = str(pagamento.get("forma") or pagamento.get("formaPagamento") or "-").strip()
            linhas.append(_linha_cupom(forma, formatar_preco(pagamento.get("valor")), largura))
    elif venda.get("formaPagamento"):
        linhas.append(_linha_cupom(str(venda.get("formaPagamento")), formatar_preco(total), largura))

    linhas.append("")
    linhas.append("Obrigado pela preferência!".center(largura))
    if INSTAGRAM_LOJA:
        linhas.append(f"Instagram: {INSTAGRAM_LOJA}".center(largura))
    linhas.append("")

    texto = _ESC_INIT + _ESC_TABELA_CP850 + _ESC_ALINHAR_ESQUERDA + "\n".join(linhas) + "\n"
    return texto.encode(CODEPAGE_CUPOM, errors="replace") + _ESC_AVANCAR_E_CORTAR.encode(CODEPAGE_CUPOM)


def imprimir_cupom(venda: dict) -> None:
    """Monta e envia o cupom não fiscal de uma venda para a Goldensky.
    Lança CupomError com mensagem segura para mostrar ao usuário em caso
    de falha esperada (dados inválidos, impressora offline, etc)."""
    dados = montar_stream_cupom(venda)

    if DRY_RUN:
        with open(DRY_RUN_OUTPUT_CUPOM, "wb") as f:
            f.write(dados)
        return

    enviar_para_cups(dados)
