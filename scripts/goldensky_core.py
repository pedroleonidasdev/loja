"""
Lógica compartilhada de impressão da loja. Não faz leitura de stdin nem HTTP — isso
fica por conta de quem importa este módulo (goldensky-etiquetas.py para uso manual via
CLI, goldensky-agente.py para o serviço HTTP local que o frontend chama).

Divisão das impressoras:
  - Goldensky 80mm  -> SOMENTE cupom não fiscal (ESC/POS, fila CUPS "Goldensky-80")
  - Elgin L42 Pro Full -> SOMENTE etiquetas de preço (ZPL II, veja elgin_l42.py)

Dependências (Linux Mint / Ubuntu):
  sudo apt install -y cups-client python3
"""

import os
import subprocess
import tempfile


class EtiquetaError(Exception):
    """Erro esperado (dados inválidos, impressora offline, etc) — mensagem é segura para mostrar ao usuário."""


class CupomError(Exception):
    """Erro esperado ao montar/enviar o cupom não fiscal — mensagem é segura para mostrar ao usuário."""


# --- Configuração das etiquetas (Elgin L42 Pro Full, ZPL) ------------------
# Valores padrão — usados quando o pedido de impressão não informa dimensão própria.
DEFAULT_LABEL_WIDTH_MM = 60
DEFAULT_LABEL_HEIGHT_MM = 30

# A Elgin imprime no máximo 108mm de largura.
LARGURA_MIN_MM, LARGURA_MAX_MM = 20, 108
ALTURA_MIN_MM, ALTURA_MAX_MM = 10, 150

# Fila CUPS da Goldensky (usada só para o cupom não fiscal).
CUPS_QUEUE = os.environ.get("GOLDENSKY_CUPS_QUEUE", "Goldensky-80")
DRY_RUN = os.environ.get("GOLDENSKY_DRY_RUN") == "1"
DRY_RUN_OUTPUT = os.environ.get("GOLDENSKY_DRY_RUN_OUTPUT", "/tmp/goldensky-etiquetas-teste.bin")

MAX_ETIQUETAS_POR_LOTE = 100

# --- Configuração do cupom não fiscal (recibo de venda) ----------------------
NOME_LOJA = os.environ.get("GOLDENSKY_NOME_LOJA", "Presente de Deus")
TELEFONE_LOJA = os.environ.get("GOLDENSKY_TELEFONE_LOJA", "(61) 3264-0087")
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


def _validar_dimensoes(largura_mm: float, altura_mm: float) -> None:
    if not (LARGURA_MIN_MM <= largura_mm <= LARGURA_MAX_MM):
        raise EtiquetaError(
            f"Largura da etiqueta deve estar entre {LARGURA_MIN_MM}mm e {LARGURA_MAX_MM}mm "
            f"(a impressora imprime no máximo {LARGURA_MAX_MM}mm de largura)"
        )
    if not (ALTURA_MIN_MM <= altura_mm <= ALTURA_MAX_MM):
        raise EtiquetaError(f"Altura da etiqueta deve estar entre {ALTURA_MIN_MM}mm e {ALTURA_MAX_MM}mm")


def formatar_preco(valor) -> str:
    try:
        valor = float(valor)
    except (TypeError, ValueError):
        return "R$ 0,00"
    texto = f"{valor:,.2f}"
    # 12,345.67 -> 12.345,67 (formato brasileiro)
    texto = texto.replace(",", "X").replace(".", ",").replace("X", ".")
    return f"R$ {texto}"


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
        espacamento_mm: float = None,  # aceito por compatibilidade; a Elgin detecta o gap sozinha
) -> int:
    """Monta e envia o lote de etiquetas para a Elgin. Retorna a quantidade impressa.
    Lança EtiquetaError com uma mensagem segura para mostrar ao usuário."""
    largura_mm = largura_mm if largura_mm is not None else DEFAULT_LABEL_WIDTH_MM
    altura_mm = altura_mm if altura_mm is not None else DEFAULT_LABEL_HEIGHT_MM

    _validar_dimensoes(largura_mm, altura_mm)
    return _imprimir_etiquetas_zpl(etiquetas, largura_mm, altura_mm)


def _imprimir_etiquetas_zpl(etiquetas: list, largura_mm: float, altura_mm: float) -> int:
    """Caminho da Elgin L42 Pro Full: gera ZPL (sem rasterizar) e envia por CUPS raw ou TCP 9100.
    O espaçamento entre etiquetas não é usado: a própria impressora detecta o gap."""
    import elgin_l42

    if len(etiquetas) > MAX_ETIQUETAS_POR_LOTE:
        raise EtiquetaError(f"O lote aceita no máximo {MAX_ETIQUETAS_POR_LOTE} etiquetas por requisição")
    try:
        dados = elgin_l42.montar_zpl(etiquetas, largura_mm, altura_mm)
        if DRY_RUN:
            with open(DRY_RUN_OUTPUT, "wb") as f:
                f.write(dados)
        else:
            elgin_l42.enviar(dados)
    except elgin_l42.ElginError as e:
        raise EtiquetaError(str(e))
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
