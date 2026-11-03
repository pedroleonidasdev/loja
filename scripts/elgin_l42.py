"""
Driver de etiquetas para a Elgin/Bematech L42 Pro Full (ZPL II).

A L42 Pro é uma impressora de etiquetas de verdade (203 dpi, até 108 mm de
largura, térmica direta ou transferência térmica) e entende ZPL/EPL/PPLA/PPLB
nativamente. Por isso aqui NÃO há rasterização nem PDF: geramos texto ZPL
puro (nome, preço e Code128 desenhados pela própria impressora), o que fica
mais nítido, mais rápido e deixa o código de barras sempre legível.

Este módulo não depende de PIL/reportlab/poppler e não importa goldensky_core;
quem decide usá-lo é o goldensky_core (variável ETIQUETA_DRIVER=zpl).

Configuração por variáveis de ambiente:
  ELGIN_TRANSPORTE   "cups" (padrão) ou "tcp"
  ELGIN_CUPS_QUEUE   nome da fila CUPS RAW (padrão "Elgin-L42") — transporte cups, USB
  ELGIN_HOST         IP da impressora na rede                    — transporte tcp, Ethernet
  ELGIN_PORT         porta RAW (padrão 9100)                     — transporte tcp
  ELGIN_ACENTOS      "utf8" (padrão, usa ^CI28) ou "ascii" (remove acentos)
                     — se saírem caracteres estranhos, troque para ascii
  ELGIN_MIDIA        "gap" (padrão, etiqueta com espaço entre elas),
                     "marca" (marca preta) ou "continua" (bobina sem corte)
  ELGIN_VELOCIDADE   1-8 (polegadas/s, padrão 4)
  ELGIN_DENSIDADE    0-30 (escurecimento, padrão 20)
"""

import os
import socket
import subprocess
import tempfile
import unicodedata

DPI = 203
DOTS_POR_MM = DPI / 25.4  # ~8 dots/mm

TRANSPORTE = os.environ.get("ELGIN_TRANSPORTE", "cups").lower()
CUPS_QUEUE = os.environ.get("ELGIN_CUPS_QUEUE", "Elgin-L42")
HOST = os.environ.get("ELGIN_HOST", "")
PORT = int(os.environ.get("ELGIN_PORT", "9100"))
ACENTOS = os.environ.get("ELGIN_ACENTOS", "utf8").lower()
MIDIA = os.environ.get("ELGIN_MIDIA", "gap").lower()
VELOCIDADE = int(os.environ.get("ELGIN_VELOCIDADE", "4"))
DENSIDADE = int(os.environ.get("ELGIN_DENSIDADE", "20"))

LARGURA_MAX_MM = 108  # largura máxima de impressão da L42 Pro
MAX_NOME_CHARS = 60
TIMEOUT_TCP_SEGUNDOS = 10


class ElginError(Exception):
    """Erro esperado (config ausente, impressora offline...) — mensagem segura para mostrar ao usuário."""


def _dots(mm: float) -> int:
    return max(1, round(mm * DOTS_POR_MM))


def _sem_acentos(texto: str) -> str:
    base = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in base if not unicodedata.combining(c))


def _escapar_zpl(texto: str) -> str:
    """Prepara texto para ^FD com ^FH: tudo que não é ASCII imprimível simples
    (e os caracteres reservados ^ ~ _ \\) vira _XX em hexadecimal, byte a byte.
    Assim nome de produto nunca consegue injetar comando ZPL, e acentos
    trafegam em UTF-8 sem depender da codificação do transporte."""
    if ACENTOS == "ascii":
        texto = _sem_acentos(texto)
    saida = []
    for b in texto.encode("utf-8"):
        c = chr(b)
        if 0x20 <= b < 0x7F and c not in "^~_\\":
            saida.append(c)
        else:
            saida.append(f"_{b:02X}")
    return "".join(saida)


def formatar_preco(valor) -> str:
    try:
        valor = float(valor)
    except (TypeError, ValueError):
        return "R$ 0,00"
    texto = f"{valor:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")
    return f"R$ {texto}"


def _modulos_code128(codigo: str) -> int:
    """Estimativa da largura do Code128 em módulos (barras finas), para centralizar.
    Só dígitos → subconjunto C (2 dígitos por símbolo); senão subconjunto B."""
    n = len(codigo)
    if codigo.isdigit():
        simbolos = n // 2 + (2 if n % 2 else 0)  # dígito sobrando custa ~2 símbolos (troca de subconjunto)
    else:
        simbolos = n
    return 11 * (1 + simbolos + 1) + 13  # start + dados + check + stop(13)


def _montar_etiqueta(nome: str, preco: str, codigo: str, qtd: int, largura_mm: float, altura_mm: float) -> str:
    W, H = _dots(largura_mm), _dots(altura_mm)
    margem = max(6, round(W * 0.02))
    util = W - 2 * margem

    # módulo do código de barras: 2 dots (~0,25 mm) quando cabe, senão 1
    bw = 2 if _modulos_code128(codigo) * 2 <= util else 1
    barcode_w = _modulos_code128(codigo) * bw
    barcode_x = max(margem, (W - barcode_w) // 2)

    y_nome, fonte_nome = round(H * 0.04), max(18, round(H * 0.11))
    y_preco, fonte_preco = round(H * 0.34), max(24, round(H * 0.17))
    y_barras, altura_barras = round(H * 0.56), max(24, round(H * 0.22))

    return "\n".join([
        "^XA",
        f"^FO{margem},{y_nome}^A0N,{fonte_nome},{fonte_nome}^FB{util},2,0,C,0^FH^FD{_escapar_zpl(nome)}^FS",
        f"^FO{margem},{y_preco}^A0N,{fonte_preco},{fonte_preco}^FB{util},1,0,C,0^FH^FD{_escapar_zpl(preco)}^FS",
        f"^BY{bw},3,{altura_barras}",
        f"^FO{barcode_x},{y_barras}^BCN,{altura_barras},Y,N,N^FH^FD{_escapar_zpl(codigo)}^FS",
        f"^PQ{qtd},0,1,N",
        "^XZ",
    ])


def montar_zpl(etiquetas: list, largura_mm: float, altura_mm: float) -> bytes:
    """Monta o ZPL do lote. Cada item: {"nome","precoVenda","codigoBarras","quantidade"(opcional)}."""
    if not etiquetas:
        raise ElginError("Nenhuma etiqueta informada")
    if largura_mm > LARGURA_MAX_MM:
        raise ElginError(f"A L42 Pro imprime no máximo {LARGURA_MAX_MM}mm de largura")

    W, H = _dots(largura_mm), _dots(altura_mm)
    tipo_midia = {"gap": "^MNY", "marca": "^MNM", "continua": "^MNN"}.get(MIDIA, "^MNY")

    # cabeçalho aplicado uma vez por lote
    partes = [
        f"~SD{max(0, min(30, DENSIDADE))}",  # comando imediato: fica fora do bloco ^XA..^XZ
        "^XA",
        "^CI28" if ACENTOS != "ascii" else "^CI0",  # UTF-8
        f"^PW{W}",
        f"^LL{H}",
        "^LH0,0",
        tipo_midia,
        "^MTD",  # térmica direta (etiqueta térmica); use ^MTT se usar ribbon
        f"^PR{max(1, min(8, VELOCIDADE))}",
        "^XZ",
    ]

    for i, item in enumerate(etiquetas):
        nome = str(item.get("nome", "")).strip()[:MAX_NOME_CHARS]
        codigo = str(item.get("codigoBarras", "")).strip()
        if not nome or not codigo:
            raise ElginError(f"Etiqueta {i + 1}: nome e código de barras são obrigatórios")
        try:
            qtd = int(item.get("quantidade") or 1)
        except (TypeError, ValueError):
            raise ElginError(f"Etiqueta {i + 1}: quantidade inválida")
        if not (1 <= qtd <= 100):
            raise ElginError(f"Etiqueta {i + 1}: quantidade deve estar entre 1 e 100")

        partes.append(_montar_etiqueta(nome, formatar_preco(item.get("precoVenda")), codigo, qtd, largura_mm, altura_mm))

    return ("\n".join(partes) + "\n").encode("ascii")  # tudo escapado em _XX, então é ASCII puro


def enviar(dados: bytes) -> None:
    if TRANSPORTE == "tcp":
        _enviar_tcp(dados)
    elif TRANSPORTE == "cups":
        _enviar_cups(dados)
    else:
        raise ElginError(f"ELGIN_TRANSPORTE inválido: '{TRANSPORTE}' (use 'cups' ou 'tcp')")


def _enviar_tcp(dados: bytes) -> None:
    if not HOST:
        raise ElginError("Defina ELGIN_HOST com o IP da impressora (transporte tcp)")
    try:
        with socket.create_connection((HOST, PORT), timeout=TIMEOUT_TCP_SEGUNDOS) as s:
            s.sendall(dados)
    except OSError as e:
        raise ElginError(f"Não consegui falar com a impressora em {HOST}:{PORT} ({e.strerror or e})")


def _enviar_cups(dados: bytes) -> None:
    with tempfile.NamedTemporaryFile(delete=False, suffix=".zpl") as tmp:
        tmp.write(dados)
        caminho = tmp.name
    try:
        r = subprocess.run(["lp", "-d", CUPS_QUEUE, "-o", "raw", caminho], capture_output=True)
        if r.returncode != 0:
            raise ElginError(
                f"Falha ao enviar para a fila CUPS '{CUPS_QUEUE}': {r.stderr.decode(errors='ignore').strip()}"
            )
    finally:
        os.unlink(caminho)
