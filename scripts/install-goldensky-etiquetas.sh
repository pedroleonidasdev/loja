#!/usr/bin/env bash
# Instala o agente goldensky-etiquetas.py no computador que tem a impressora
# térmica Goldensky-80 conectada via USB e a fila CUPS configurada.
#
# Uso (no computador com a impressora, dentro da pasta loja-main):
#   sudo ./scripts/install-goldensky-etiquetas.sh

set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Este instalador precisa rodar como root (sudo)." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DESTINO="/usr/local/bin/goldensky-etiquetas.py"

echo "Verificando dependências..."
FALTANDO=()
command -v python3 >/dev/null 2>&1 || FALTANDO+=("python3")
command -v pdftoppm >/dev/null 2>&1 || FALTANDO+=("poppler-utils")
command -v lp >/dev/null 2>&1 || FALTANDO+=("cups-client")
python3 -c "import PIL" >/dev/null 2>&1 || FALTANDO+=("python3-pil")
python3 -c "import reportlab" >/dev/null 2>&1 || FALTANDO+=("python3-reportlab")

if [ ${#FALTANDO[@]} -gt 0 ]; then
  echo "Faltando: ${FALTANDO[*]}"
  echo "Instale com: sudo apt update && sudo apt install -y cups-client python3 python3-pil python3-reportlab poppler-utils"
  exit 1
fi

echo "Copiando agente para ${DESTINO}..."
cp "${SCRIPT_DIR}/goldensky-etiquetas.py" "${DESTINO}"
chmod 755 "${DESTINO}"

echo "Verificando fila CUPS Goldensky-80..."
if lpstat -p Goldensky-80 >/dev/null 2>&1; then
  echo "Fila Goldensky-80 encontrada."
else
  echo "AVISO: a fila 'Goldensky-80' ainda não existe ou não está ativa."
  echo "Configure a impressora no CUPS (http://localhost:631) com esse nome exato antes de imprimir."
fi

echo ""
echo "Instalação concluída."
echo "Teste sem imprimir com:"
echo "  GOLDENSKY_DRY_RUN=1 ${DESTINO} < ${SCRIPT_DIR}/exemplo-etiquetas.json"
