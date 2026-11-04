#!/usr/bin/env bash
# qa_vis_c10.sh — WRAPPER de la corrida del arnés visual C10 (gemelo del
# canon C9 D-C9-11; cortes C10: D-C10-13/G5-a).
#
# Contrato:
#   · corre EXCLUSIVAMENTE python3 scripts/qa_vis_c10.py (Playwright sync,
#     chromium headless, ya instalado por pip fuera del repo — sin tocar
#     ningún package.json);
#   · teee el RAW completo a .atl/sdd/C10/qa-log.txt (exit canon C8 passthrough:
#     0 = 33 PASS/PASS-CON-NOTA · 1 = algún FAIL/BLOQUEADO · 2 = pila/PILOTO);
#   · HIGIENE S16 POST-CORRIDA: grep del qa-log RAW buscando prefijos de
#     credencial/token fugados (0 hits exigidos; cualquier hit ⇒ exit 2);
#   · PROHIBIDO exportar o source del .env hacia el shell (los valores de
#     credential/JWT/API-key viven SOLO en el proceso Python — parser dotenv
#     en memoria). Este guion jamás lee los VALORES del .env;
#   · la fase destructiva (si corre) es el arnés: compose stop/up SOLO del
#     servicio `backend` (jamás down -v, jamás parar postgres — higiene C9) y
#     los DELETEs de doble confirmación SOLO contra su siembra efímera
#     qa-c10-<ts>-* (L al final del arnés: guard ^qa-c9-|^qa-c10-).

set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR_QA="$RAIZ/.atl/sdd/C10"
LOG_RAW="$DIR_QA/qa-log.txt"

cd "$RAIZ" || exit 2

# — guardas de entorno (exit 2, jamá falso PASS al arranque) —
for f in compose.yaml .env scripts/qa_vis_c10.py; do
    if [ ! -f "$f" ]; then
        echo "[guard] falta $f en el repo — entorno no disponible" >&2
        exit 2
    fi
done
if ! python3 -c "import playwright" 2>/dev/null; then
    echo "[guard] playwright no importable: 'python3 -m pip install " \
         "playwright && playwright install chromium' — el guion jamás " \
         "instala solo (D-C9-15)" >&2
    exit 2
fi
if [ ! -x "$DIR_QA/evidencia" ] && [ ! -d "$DIR_QA/evidencia" ]; then
    mkdir -p "$DIR_QA/evidencia" || exit 2
fi

echo "[wrapper] corrida del arnés C10: python3 scripts/qa_vis_c10.py " \
     "(RAW → $LOG_RAW)" | tee "$LOG_RAW"

set +e
python3 scripts/qa_vis_c10.py 2>&1 | tee -a "$LOG_RAW"
RC=${PIPESTATUS[0]}
set -e

# — S16 post-corrida: 0 hits de prefijos de credencial/token en el RAW —
HITS="$(grep -Ec 'plk_[A-Za-z0-9_-]+|eyJ[A-Za-z0-9_-]{10,}' "$LOG_RAW" || true)"
if [ "${HITS:-0}" != "0" ]; then
    echo "[S16] FALLO DE HIGIENE: $HITS líneas del qa-log contienen " \
         "prefijos de credencial/token — REESCRIBIENDO exit=2 (R9.5.2/" \
         "D-C10-13)" >&2
    exit 2
fi
echo "[S16] grep del qa-log RAW: 0 hits de prefijos de credencial/token" | \
    tee -a "$LOG_RAW"

exit "$RC"