#!/usr/bin/env python3
"""
Agente local de impressão de etiquetas para a impressora térmica Goldensky 80mm.

Lê um JSON pela stdin no formato:
  {"etiquetas": [{"nome": "...", "precoVenda": 12.5, "codigoBarras": "789..."}]}

Para cada etiqueta:
  1. Monta uma página de PDF de 60 x 30mm (nome + preço + código de barras) com reportlab.
  2. Rasteriza essa página em PNG a 203 dpi com pdftoppm (poppler-utils).
  3. Converte o raster em comandos RAW ESC/POS (GS v 0) para a Goldensky.

O resultado é enviado para a fila CUPS "Goldensky-80" via `lp -o raw`.

Modo de teste (sem impressora):
  GOLDENSKY_DRY_RUN=1 ./goldensky-etiquetas.py < scripts/exemplo-etiquetas.json
  Gera /tmp/goldensky-etiquetas-teste.bin com os bytes RAW e não aciona o CUPS.

Dependências (Linux Mint / Ubuntu):
  sudo apt install -y cups-client python3 python3-pil python3-reportlab poppler-utils
"""

import io
import json
import os
import subprocess
import sys
import tempfile

from PIL import Image
from reportlab.graphics.barcode import code128
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

# --- Configuração física da etiqueta -----------------------------------------
# Lote de etiquetas adesivas pré-cortadas de 60 x 30mm usado neste projeto.
# Se o lote físico mudar de tamanho, recalibre estas constantes.
LABEL_WIDTH_MM = 60
LABEL_HEIGHT_MM = 30
DPI = 203  # resolução nativa da cabeça de impressão da Goldensky 80mm

CUPS_QUEUE = os.environ.get("GOLDENSKY_CUPS_QUEUE", "Goldensky-80")
DRY_RUN = os.environ.get("GOLDENSKY_DRY_RUN") == "1"
DRY_RUN_OUTPUT = os.environ.get("GOLDENSKY_DRY_RUN_OUTPUT", "/tmp/goldensky-etiquetas-teste.bin")

# Avanço de papel (em pontos ESC/POS, múltiplos de 1 linha) aplicado entre uma
# etiqueta e a próxima, para dar folga física ao corte. Ajustado para representar
# ~8mm na resolução de 203 dpi (203 / 25.4 ≈ 8 dots/mm -> 8mm ≈ 64 dots).
GAP_ENTRE_ETIQUETAS_MM = 8
GAP_ENTRE_ETIQUETAS_DOTS = round(GAP_ENTRE_ETIQUETAS_MM * DPI / 25.4)

MAX_ETIQUETAS_POR_LOTE = 100


def erro(mensagem: str, codigo: int = 1) -> None:
    sys.stderr.write(mensagem.strip() + "\n")
    sys.exit(codigo)


def formatar_preco(valor) -> str:
    try:
        valor = float(valor)
    except (TypeError, ValueError):
        return "R$ 0,00"
    texto = f"{valor:,.2f}"
    # 12,345.67 -> 12.345,67 (formato brasileiro)
    texto = texto.replace(",", "X").replace(".", ",").replace("X", ".")
    return f"R$ {texto}"


def montar_pdf_etiqueta(nome: str, preco_formatado: str, codigo_barras: str) -> bytes:
    """Gera um PDF de uma página no tamanho exato da etiqueta (60 x 30mm)."""
    buffer = io.BytesIO()
    largura = LABEL_WIDTH_MM * mm
    altura = LABEL_HEIGHT_MM * mm
    c = canvas.Canvas(buffer, pagesize=(largura, altura))

    # Nome do produto — quebra em até 2 linhas se for muito comprido
    c.setFont("Helvetica-Bold", 8)
    max_chars = 30
    linha1, linha2 = nome[:max_chars], nome[max_chars:max_chars * 2]
    c.drawCentredString(largura / 2, altura - 5 * mm, linha1)
    if linha2:
        c.drawCentredString(largura / 2, altura - 8.5 * mm, linha2)

    # Preço
    c.setFont("Helvetica-Bold", 9)
    c.drawCentredString(largura / 2, altura - 12.5 * mm, preco_formatado)

    # Código de barras (Code128), centralizado na parte inferior da etiqueta
    barcode = code128.Code128(codigo_barras, barHeight=10 * mm, barWidth=0.28 * mm)
    barcode_largura = barcode.width
    barcode.drawOn(c, (largura - barcode_largura) / 2, 2.5 * mm)

    c.setFont("Helvetica", 6)
    c.drawCentredString(largura / 2, 1 * mm, codigo_barras)

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
            erro(f"Falha ao rasterizar etiqueta (pdftoppm): {resultado.stderr.decode(errors='ignore')}")

        png_path = f"{png_prefix}-1.png"
        if not os.path.exists(png_path):
            # em algumas versões o pdftoppm não usa o sufixo "-1" para página única
            candidatos = [f for f in os.listdir(tmp) if f.endswith(".png")]
            if not candidatos:
                erro("pdftoppm não gerou nenhuma imagem para a etiqueta")
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


def montar_stream_escpos(etiquetas: list) -> bytes:
    stream = bytearray()
    stream += b"\x1b\x40"  # ESC @ — inicializa a impressora

    for i, item in enumerate(etiquetas):
        nome = str(item.get("nome", "")).strip()
        codigo_barras = str(item.get("codigoBarras", "")).strip()
        preco_formatado = formatar_preco(item.get("precoVenda"))

        if not nome or not codigo_barras:
            erro(f"Etiqueta {i + 1}: nome e código de barras são obrigatórios")

        pdf_bytes = montar_pdf_etiqueta(nome, preco_formatado, codigo_barras)
        imagem = rasterizar_pdf(pdf_bytes)
        stream += imagem_para_escpos(imagem)

        # avanço de papel entre etiquetas (não aplica depois da última)
        if i < len(etiquetas) - 1:
            linhas_de_avanco = max(1, GAP_ENTRE_ETIQUETAS_DOTS // 24)
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
            erro(
                f"Falha ao enviar para a fila CUPS '{CUPS_QUEUE}': "
                f"{resultado.stderr.decode(errors='ignore')}"
            )
    finally:
        os.unlink(tmp_path)


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        erro("JSON inválido recebido na entrada padrão")
        return

    etiquetas = payload.get("etiquetas", [])
    if not etiquetas:
        erro("Nenhuma etiqueta informada")
        return
    if len(etiquetas) > MAX_ETIQUETAS_POR_LOTE:
        erro(f"O lote aceita no máximo {MAX_ETIQUETAS_POR_LOTE} etiquetas por requisição")
        return

    dados = montar_stream_escpos(etiquetas)

    if DRY_RUN:
        with open(DRY_RUN_OUTPUT, "wb") as f:
            f.write(dados)
        sys.stdout.write(
            f"[dry-run] {len(etiquetas)} etiqueta(s), {len(dados)} bytes gerados em {DRY_RUN_OUTPUT}\n"
        )
        return

    enviar_para_cups(dados)
    sys.stdout.write(f"{len(etiquetas)} etiqueta(s) enviada(s) para {CUPS_QUEUE}\n")


if __name__ == "__main__":
    main()
