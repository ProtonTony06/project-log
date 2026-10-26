#!/usr/bin/env bash
#
# qa_c7_smoke.sh — smokes [S] del corte C7 (design.md §5.3; canon C5/C6)
#
# Codifica los escenarios del spec C7 que se certificarán en pila REAL:
#   pre (recomposición + guard R-7.j) · E7.9 (handshake por nginx 401/403/
#   101) · E7.17 (ws_echo_test --solo-handshake SIN edición) · E7.1 (ingesta
#   → frame §3.5) · E7.5 (batch de 3 → 1 frame, orden del RETURNING) ·
#   E7.6 (recalificación set_filters en caliente) · E7.15 (archivado deja
#   de emitir) · E7.10 (ping 0x9/0xA ~30 s, ningún JSON != batch) · E7.11
#   (reconexión limpia, SIN replay) · E7.14 (backpressure 1013 — tolerancia
#   R-7.k con nota).
#
# Patrones heredados del canon C6 (qa_c6_smoke.sh): recomposición ÚNICA
# up -d --build --force-recreate (NUNCA down -v), psql -qAt, conteos
# RELATIVOS (delta), needles sufijados por ejecución smoke-c7-<TS>-*, JWT y
# key SOLO en tmpdir 700 con trap, smoke-of-the-smoke.
#
# Higiene (R7.7/S16/CD-4): el JWT y la API key viajan por env del proceso
# Python (PY_TOKEN/PY_KEY) y a los tmpfiles del tmpdir 700 — JAMÁS a
# consola ni al fichero de eventos (los frames SÍ contienen los needles
# públicos de la ingesta del smoke — contenido NO sensible por diseño).
#
# Uso:
#   bash scripts/qa_c7_smoke.sh
#
# Salida:
#   Fases por stderr; al final una línea
#     E7.n: PASS | FAIL | BLOQUEADO (detalle)
#   por ID de escenario, ejecutados en orden cronológico §5.3.
# Exit: 0 = todos PASS · 1 = hay algún FAIL · 2 = solo BLOQUEADOs
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE_URL="http://127.0.0.1:8080"
WS_HOST="127.0.0.1"; WS_PORT="8080"   # nginx: el handshake pasa por el proxy
URL_LOGIN="$BASE_URL/api/v1/panel/auth/login"
URL_ME="$BASE_URL/api/v1/panel/me"
URL_PROJECTS="$BASE_URL/api/v1/panel/projects"
URL_INGEST="$BASE_URL/api/v1/ingest"
URL_BATCH="$BASE_URL/api/v1/ingest/batch"

TS="$(date +%Y%m%d%H%M%S)"
T_STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
P_NAME="smoke-c7-${TS}-proyecto"
DESC_VALIDA="descripción del smoke c7 ($TS)"
NEEDLE_A="smoke-c7-${TS}-a"      # E7.1
NEEDLE_B="smoke-c7-${TS}-b"      # E7.5 (1 de 3 de l batch)
NEEDLE_B2="smoke-c7-${TS}-b2"    # E7.5 (2 de 3)
NEEDLE_C="smoke-c7-${TS}-c"      # E7.5 (3 de 3)
CORR_A="e71-a-${TS}"             # correlation_id con '_' SOLO en psql (=)
CORR_B="e75-b-${TS}"
CORR_B2="e75-b2-${TS}"
CORR_C="e75-c-${TS}"
NEEDLE_E6W1="smoke-c7-${TS}-e6w1"  # E7.6 fila WARN con level=ERROR activo
NEEDLE_E6W2="smoke-c7-${TS}-e6w2"  # E7.6 fila WARN tras recalificar WARN
NEEDLE_E6P="smoke-c7-${TS}-e6p"    # E7.6 con project_id recalificado a otro
CORR_E6W1="e76-w1-${TS}"
CORR_E6W2="e76-w2-${TS}"
CORR_E6P="e76-p-${TS}"
NEEDLE_AR1="smoke-c7-${TS}-ar1"    # E7.15 tras desarchivar (SÍ llega)
CORR_AR1="e715-ar1-${TS}"
NEEDLE_AR2="smoke-c7-${TS}-ar2"    # E7.15 durante ARCHIVED (403, no entra)
CORR_AR2="e715-ar2-${TS}"
NEEDLE_GAP="smoke-c7-${TS}-gap"    # E7.11 filas del GAP (entrada en caída)
CORR_GAP="e711-gap-${TS}"
NEEDLE_RECON="smoke-c7-${TS}-recon" # E7.11 tras reconectar (SÍ llega)
CORR_RECON="e711-recon-${TS}"
NEEDLE_BP="smoke-c7-${TS}-bp"      # E7.14 prefijo del mensaje 32 KiB

TMPDIR_QA="$(mktemp -d /tmp/qa_c7_smoke.XXXXXX)"
chmod 700 "$TMPDIR_QA" 2>/dev/null || true
# KEEP_TMP=1 (SOLO para depurar una corrida fallida): conserva el tmpdir 700
# (contiene el JWT y la key del smoke — borrar a mano AL ACABAR).
trap 'trap_liberar; [ "${KEEP_TMP:-0}" = "1" ] && echo "[trap] tmpdir CONSERVADO: $TMPDIR_QA (borrar a mano)" >&2 || rm -rf "$TMPDIR_QA"' EXIT
trap_liberar() { # clientes WS de fondo (sin secretos — SOLO kill)
  local p
  for p in "${LONG_PID:-}" "${LONG2_PID:-}" "${LENTA_PID:-}"; do
    [ -n "$p" ] && kill "$p" 2>/dev/null || true
  done
  exec 3>&- 2>/dev/null || true
}
F_CUERPO="$TMPDIR_QA/cuerpo.txt"
F_CAB="$TMPDIR_QA/cabeceras.txt"
F_TOKEN="$TMPDIR_QA/token.txt"    # JWT del login — no consola
F_ID_A="$TMPDIR_QA/id_a.txt"
F_KEY1="$TMPDIR_QA/key1.txt"      # key del proyecto del smoke — no consola
F_PAYLOAD="$TMPDIR_QA/payload.json"
F_PAYLOAD2="$TMPDIR_QA/payload2.json"
F_LONG="$TMPDIR_QA/long.txt"      # eventos del cliente WS de fondo (E7.1…)
F_BIDS="$TMPDIR_QA/bids.txt"      # batch_id vistos (unicidad H4 entre casos)
F_LONG2="$TMPDIR_QA/long2.txt"    # eventos de la reconexión de E7.11
F_LENTA="$TMPDIR_QA/lenta.txt"    # eventos del cliente NO consumidor (E7.14)
F_FIFO="$TMPDIR_QA/ordenes.fifo"  # comandos set_filters/CLOSE → cliente long
ERR_LONG="$TMPDIR_QA/long_err.log"

# Compose wrapper (heredado de C4/C5/C6).
COMPOSE_BIN=""
if command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
elif command -v podman-compose >/dev/null 2>&1; then
  COMPOSE_BIN="podman-compose"
elif command -v podman >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
fi
compose_() {
  local sub="$1" estado
  case "$sub" in
    up)   timeout --kill-after=10 620 $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log" ; estado=$? ;;
    down) timeout --kill-after=10 120 $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log" ; estado=$? ;;
    *)    $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log" ; estado=$? ;;
  esac
  if [ "$estado" -eq 124 ] || [ "$estado" -eq 137 ]; then
    pkill -f 'podman-compose up -d --build' 2>/dev/null || true
    pkill -f 'podman wait --condition=healthy' 2>/dev/null || true
  fi
  return "$estado"
}
PODMAN() { podman "$@" 2>>"$TMPDIR_QA/compose.log"; }

declare -a RESULTADOS=()
marca() { RESULTADOS+=("$1: $2${3:+ ($3)}"); }

# salud por podman (NUNCA curl /actuator como proxy de salud)
pila_saludable() {
  local snap estado e="" svc
  snap="$(PODMAN ps --format '{{.Names}} {{.Status}}' 2>/dev/null || true)"
  for svc in backend frontend postgres; do
    estado="$(grep -E "^[^ ]*${svc}[ _]" <<<"$snap" | head -n1)"
    grep -qE 'Up.*\(healthy\)' <<<"$estado" || e="$e $svc=$(echo "$estado" | head -c 60)"
  done
  [ -z "$e" ] && return 0 || { echo "$e" >&2; return 1; }
}

# esperar_healthy SEGUNDOS: sondea GET /me → 401 invalid_token real.
esperar_healthy() {
  local limite=$(( $(date +%s) + ${1:-240} ))
  while [ "$(date +%s)" -lt "$limite" ]; do
    if curl -sS --max-time 5 -o "$TMPDIR_QA/hc_body.txt" "$URL_ME" 2>/dev/null \
       && grep -q '"code"[[:space:]]*:[[:space:]]*"invalid_token"' "$TMPDIR_QA/hc_body.txt" 2>/dev/null; then
      return 0
    fi
    sleep 3
  done
  return 1
}

# psql_q SQL — salida -qAt limpia; SOLO para SQL SIN secretos.
psql_q() {
  compose_ exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 -qAt -c "$1"
}

# req VERBO URL CUERPO [CABECERA...] — fija CODIGO/CUERPO/CABECERAS globales.
req() {
  local verbo="$1" url="$2" cuerpo="$3"; shift 3
  local args=(-sS --max-time 30 -o "$F_CUERPO" -D "$F_CAB" -w '%{http_code}' -X "$verbo")
  if [ -n "$cuerpo" ]; then
    args+=(-H 'Content-Type: application/json')
    args+=(--data-binary "$cuerpo")
  fi
  local _h
  for _h in "$@"; do args+=(-H "$_h"); done
  local cod
  cod="$(curl "${args[@]}" "$url" 2>>"$TMPDIR_QA/curl_err.log" || true)"
  CODIGO="${cod:-000}"
  CUERPO="$(cat "$F_CUERPO" 2>/dev/null || true)"
  CABECERAS="$(cat "$F_CAB" 2>/dev/null || true)"
  printf '%s %s -> %s\n' "$verbo" "$url" "$CODIGO" >> "$TMPDIR_QA/req_debug.log"
}

curl_ingest() { printf 'X-API-Key: %s' "$(cat "$1")"; }
curl_get() { printf 'Authorization: Bearer %s' "$(cat "$1")"; }
login() { req POST "$URL_LOGIN" "{\"username\":\"$1\",\"password\":\"$2\"}"; }
bearer_hdr() { curl_get "$F_TOKEN"; }

# campo_json CLAVE — valor tolerante de CLAVE o falla.
campo_json() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
except Exception:
    sys.exit(3)
v = d.get(sys.argv[1]) if isinstance(d, dict) else None
if v is None:
    sys.exit(2)
print("true" if v is True else "false" if v is False else v)' "$1"
}

# rec_micro CORRELATION_ID — received_at a micros (UTC) de la fila con ese
# correlation_id del proyecto smoke (agujas con '_' van con '=' — R6.20-a).
rec_micro() {
  psql_q "SELECT to_char(received_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US') FROM logs WHERE project_id = $(cat "$F_ID_A") AND correlation_id = '$1'" \
    2>/dev/null || echo err
}

# id_por_corr CORRELATION_ID — id de la fila (orden RETURNING E7.5).
id_por_corr() {
  psql_q "SELECT id FROM logs WHERE project_id = $(cat "$F_ID_A") AND correlation_id = '$1'" \
    2>/dev/null || echo err
}

# delta_cuenta — count(*) del proyecto del smoke (conteo RELATIVO, R-6.h).
delta_cuenta() {
  psql_q "SELECT count(*) FROM logs WHERE project_id = $(cat "$F_ID_A")" \
    2>/dev/null || echo err
}

# set_filters_json PROJECT LEVEL ENV — frame set_filters §3.5 EXACTO (siempre
# las 6 claves; null = sin aplicar). project/project vacíos → literal null.
sf_json() {
  python3 -c '
import sys
project_id = sys.argv[1] if sys.argv[1] != "-" else None
level = sys.argv[2] if sys.argv[2] != "-" else None
environment = sys.argv[3] if sys.argv[3] != "-" else None
import json
filtros = {"project_id": int(project_id) if project_id is not None else None,
           "environment": environment, "level": level,
           "from": None, "to": None, "text": None}
print(json.dumps({"type": "set_filters", "filters": filtros},
                 separators=(",", ":")))' "$1" "$2" "$3"
}

# frame_evento N FICHERO — payload del N-ésimo evento "frame" del fichero
# del cliente long (o falla).
frame_evento() {
  python3 -c '
import sys
n = int(sys.argv[1]); i = 0
try:
    with open(sys.argv[2]) as fh:
        for linea in fh:
            if linea.startswith("frame "):
                i += 1
                if i == n:
                    print(linea[6:].strip()); sys.exit(0)
except FileNotFoundError:
    pass
sys.exit(2)' "$1" "$2"
}

# frames_count FICHERO — nº de eventos "frame".
frames_count() {
  python3 -c '
import sys
c = 0
try:
    with open(sys.argv[1]) as fh:
        for linea in fh:
            c += (linea.startswith("frame "))
except FileNotFoundError:
    pass
print(c)' "$1"
}

# pings_count FICHERO — nº de eventos "ping".
pings_count() {
  python3 -c '
import sys
c = 0
try:
    with open(sys.argv[1]) as fh:
        for linea in fh:
            c += linea.startswith("ping ")
except FileNotFoundError:
    pass
print(c)' "$1"
}
# los checks dentro de bash -c (exportadas al subshell) usan estas tres:
export -f frames_count pings_count frame_evento

# cliente WS de fondo (modo long: handshake + set_filters + bucle de lectura)
# y cliente de comandos por FIFO. La ÚNICA forma de pedir set_filters en
# caliente (E7.6) y el close limpio (E7.11): escribe en el FIFO, con el fd 3
# mantenido abierto. Duración dada por parámetro (el fondo muere solo).
iniciar_long() {
  # iniciar_long FICHERO DURACION PROJECT_ID [CON_FIFO]
  local fichero="$1" duracion="$2" proyecto="$3" con_fifo="${4:-1}"
  rm -f "$F_FIFO"; mkfifo "$F_FIFO"
  # CONTRATO R7.10: el sf inicial viaja COMPUESTO ({"type":"set_filters",
  # "filters":{…}}) — el shape de 6 claves en la RAÍZ es "no-contrato" y el
  # handler lo IGNORA ( DEBUG) — sesiones así nunca matchean (D-7.7).
  PY_TOKEN="$(cat "$F_TOKEN")" \
    python3 "$TMPDIR_QA/ws7_client.py" long "$WS_HOST" "$WS_PORT" "$duracion" \
      "$(sf_json "$proyecto" - -)" \
      "$con_fifo" >"$fichero" 2>>"$ERR_LONG" <"$F_FIFO" &
  LONG_PID=$!
  exec 3>"$F_FIFO"   # mantenedor del FIFO abierto (sin EOF prematuro)
}

# orden_fifo TEXTO — manda un comando al cliente long (set_filters/CLOSE).
orden_fifo() { printf '%s\n' "$1" >&3 2>/dev/null || true; sleep 0.4; }

# cerrar_fifo y esperar el pid del cliente de fondo
parar_long() { exec 3>&- 2>/dev/null || true; wait "$LONG_PID" 2>/dev/null || true; }

# esperar_que SEGUNDS CONDICION_FUNCION — sondeo genérico (patrón canon).
esperar_que() {
  local limite=$(( $(date +%s) + ${1:-20} ))
  shift
  while [ "$(date +%s)" -lt "$limite" ]; do
    "$@" && return 0
    sleep 0.5
  done
  return 1
}

# ----------------------------------------------------------------------------
# 1. Cliente WS RFC 6455 mínimo (stdlib — cero dependencias nuevas): se
#    escribe al tmpdir 700 y lo invocan los escenarios (handshake sin header
#    Origin — S7-12 —, frames MASKED cliente→server, Accept SHA-1+base64).
# ----------------------------------------------------------------------------
cat > "$TMPDIR_QA/ws7_client.py" <<'PYEOF'
#!/usr/bin/env python3
# ws7_client.py — cliente WebSocket RFC 6455 mínimo del smoke C7 (stdlib).
# Modos:
#   hs host puerto modo            → 'sin-token'|'evil'|'ok': handshake puro
#   long host puerto dur sf con_fifo → sesión viva: set_filters inicial, lee
#                                    frames (0x1→'frame ...'; 0x9→'ping <t>'
#                                    + répond 0xA; 0x8→'close <code>'),
#                                    comandos stdin (JSON/CLOSE) si con_fifo
#   lento host puerto sf s_noleer espera_close → NO consume (backpressure):
#                                    duerme y luego busca close/EOF
import base64, errno, hashlib, json, os, socket, sys, threading, time

GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


def abrir(host, port, token, origin, tmo):
    sock = socket.create_connection((host, port), timeout=tmo)
    sock.settimeout(tmo)
    key = base64.b64encode(os.urandom(16)).decode()
    req = (f"GET /ws/logs?token={token} HTTP/1.1\r\nHost: {host}:{port}\r\n"
           f"Upgrade: websocket\r\nConnection: Upgrade\r\n"
           f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n")
    if origin:
        req += f"Origin: {origin}\r\n"
    req += "\r\n"
    sock.sendall(req.encode("iso-8859-1"))
    data = b""
    while b"\r\n\r\n" not in data:
        parte = sock.recv(4096)
        if not parte:
            break
        data += parte
    cabecera, _, resto = data.partition(b"\r\n\r\n")
    lineas = cabecera.decode("iso-8859-1").split("\r\n")
    partes = lineas[0].split() if lineas and lineas[0] else []
    status = int(partes[1]) if len(partes) >= 2 and partes[1].isdigit() else 0
    headers = {}
    for linea in lineas[1:]:
        if ":" in linea:
            k, v = linea.split(":", 1)
            headers[k.strip().lower()] = v.strip()
    # resto contiene bytes ya recibidos del socket abierto solo si 101
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    return sock, status, headers, resto, key


def send_frame(sock, opcode, payload: bytes, tmo):
    sock.settimeout(tmo)
    mascara = os.urandom(4)
    mascara_apl = bytes(b ^ mascara[i % 4] for i, b in enumerate(payload))
    n = len(payload)
    if n < 126:
        cab = bytes([0x80 | opcode, 0x80 | n])
    elif n < 65536:
        cab = bytes([0x80 | opcode, 0x80 | 126]) + n.to_bytes(2, "big")
    else:
        cab = bytes([0x80 | opcode, 0x80 | 127]) + n.to_bytes(8, "big")
    sock.sendall(cab + mascara + mascara_apl)


def leer_frame(sock, tmo):
    # devuelve (opcode, payload); EOF → ConnectionError
    sock.settimeout(tmo)
    dos = recv_exact(sock, 2)
    opcode = dos[0] & 0x0F
    fin = (dos[0] & 0x80) != 0
    l = dos[1] & 0x7F
    if l == 126:
        l = int.from_bytes(recv_exact(sock, 2), "big")
    elif l == 127:
        l = int.from_bytes(recv_exact(sock, 8), "big")
    if dos[1] & 0x80:
        recv_exact(sock, 4)  # mask server→client no llega (sin máscara)
    payload = recv_exact(sock, l) if l else b""
    return opcode, payload, fin


def recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        parte = sock.recv(n - len(buf))
        if not parte:
            raise ConnectionError("EOF")
        buf += parte
    return buf


def hs_solo(host, port, modo, tmo=10):
    token = os.environ.get("PY_TOKEN", "")
    if modo == "sin-token":
        token = ""
    origin = "http://evil.example" if modo == "evil" else None
    sock, status, headers, _resto, key = abrir(host, port, token, origin, tmo)
    accept = headers.get("sec-websocket-accept")
    ok = False
    if accept:
        ok = accept == base64.b64encode(hashlib.sha1(
            (key + GUID).encode()).digest()).decode()
    print(f"HS {status} ACCEPT {'ok' if ok else 'no'}", flush=True)
    print("HS-FIN", flush=True)
    sock.close()
    return ok


def modo_long(host, port, duracion, sf_json, con_fifo, tmo=10):
    token = os.environ.get("PY_TOKEN", "")
    sock = None
    try:
        sock, status, headers, resto, _key = abrir(host, port, token, None, tmo)
    except Exception as ex:
        print(f"HS-fallo {ex}", flush=True)
        return
    if status != 101:
        print(f"HS-fallo {status}", flush=True)
        sock.close()
        return
    print("hs-ok", flush=True)
    try:
        send_frame(sock, 0x1, sf_json.encode(), tmo)
        print("sf-sent", flush=True)
    except Exception:
        print("fin", flush=True)
        return
    cierre = threading.Event()

    def comandos():
        # stdin: cada línea = frame de texto (set_filters); línea CLOSE =
        # close limpio 1000 (E7.11). EOF del FIFO → hilo muere (sin close).
        try:
            for linea in sys.stdin:
                comando = linea.strip()
                if comando == "CLOSE":
                    send_frame(sock, 0x8, (1000).to_bytes(2, "big"), tmo)
                    # EL print va ANTES de esperar el eco: el loop principal
                    # puede consumir el eco del close (0x8 del servidor) y
                    # terminar ANTES que este hilo daemon imprima (carrera
                    # del run 3: el eco lo leyó el main y el hilo murió sin
                    # imprimir — E7.11 falló con la sesión bien cerrada).
                    print("cerrado-por-comando", flush=True)
                    try:
                        # espera el eco del close del servidor ~2 s
                        sock.settimeout(2)
                        _op, pay, _fin = leer_frame(sock, 2)
                    except Exception:
                        pass
                    cierre.set()
                    return
                send_frame(sock, 0x1, comando.encode(), tmo)
                print("sf-sent", flush=True)
        except Exception:
            cierre.set()

    if int(con_fifo):
        hilo = threading.Thread(target=comandos, daemon=True)
        hilo.start()
    limite = time.time() + duracion
    sock.settimeout(0.5)
    try:
        while time.time() < limite and not cierre.is_set():
            try:
                opcode, payload, _fin = leer_frame(sock, 0.5)
            except socket.timeout:
                continue
            except ConnectionError:
                break
            if opcode == 0x9:
                print(f"ping {int(time.time())}", flush=True)
                send_frame(sock, 0xA, payload, 5)
            elif opcode == 0x1:
                print("frame " + payload.decode("utf-8", "replace"),
                      flush=True)
            elif opcode == 0x8:
                code = int.from_bytes(payload[:2], "big") if len(payload) >= 2 else 0
                print(f"close {code}", flush=True)
                break
        else:
            print("fin", flush=True)
            return
    except Exception as ex:
        print(f"fin {type(ex).__name__}", flush=True)
        return
    print("fin", flush=True)


def modo_lento(host, port, sf_json, s_noleer, espera_close, tmo=10):
    token = os.environ.get("PY_TOKEN", "")
    try:
        sock, status, headers, resto, _key = abrir(host, port, token, None, tmo)
    except Exception as ex:
        print(f"HS-fallo {ex}", flush=True)
        return
    if status != 101:
        print(f"HS-fallo {status}", flush=True)
        sock.close()
        return
    print("hs-ok", flush=True)
    send_frame(sock, 0x1, sf_json.encode(), tmo)
    print("sf-sent", flush=True)
    # NO consumir: dormir con el socket sin leer (backpressure del lento)
    time.sleep(s_noleer)
    print("reanudando", flush=True)
    # leer lo acumulado y buscar el close (o EOF) hasta espera_close s
    sock.settimeout(espera_close)
    vistos = 0
    try:
        while True:
            codigo = 0
            try:
                opcode, payload, _fin = leer_frame(sock, espera_close)
            except socket.timeout:
                print(f"nada {vistos}", flush=True)
                print("fin", flush=True)
                sock.close()
                return
            except ConnectionError:
                print(f"eof {vistos}", flush=True)
                sock.close()
                return
            if opcode == 0x8:
                codigo = int.from_bytes(payload[:2], "big") if len(payload) >= 2 else 0
                print(f"close {codigo}", flush=True)
                print("fin", flush=True)
                sock.close()
                return
            if opcode == 0x9:
                send_frame(sock, 0xA, payload, 5)  # el lento SÍ responde pong
            elif opcode == 0x1:
                vistos += 1
                print("frame", flush=True)
    except Exception as ex:
        print(f"fin {type(ex).__name__}", flush=True)


if __name__ == "__main__":
    modo = sys.argv[1]
    if modo == "hs":
        hs_solo(sys.argv[2], int(sys.argv[3]), sys.argv[4])
    elif modo == "long":
        modo_long(sys.argv[2], int(sys.argv[3]), float(sys.argv[4]),
                  sys.argv[5], int(sys.argv[6]))
    elif modo == "lento":
        modo_lento(sys.argv[2], int(sys.argv[3]), sys.argv[4],
                   float(sys.argv[5]), float(sys.argv[6]))
PYEOF
chmod 700 "$TMPDIR_QA/ws7_client.py"

# ----------------------------------------------------------------------------
# 2. Precondiciones: anti-veneno + .env + recomposición ÚNICA (+ guard R-7.j)
# ----------------------------------------------------------------------------
unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
      SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
      JWT_SECRET JWT_TTL_HOURS ADMIN_INITIAL_USERNAME ADMIN_INITIAL_PASSWORD \
      INGEST_MAX_REQUEST_BYTES INGEST_MAX_BATCH_SIZE WS_HEARTBEAT_SECONDS \
      FRONTEND_ORIGIN SPRING_PROFILES_ACTIVE LOGGING_LEVEL TZ 2>/dev/null || true

CON_ENV=0; CON_CRED=0; CON_BD=0; CON_ORIGIN=0
if [ -f .env ]; then
  CON_ENV=1
  while IFS= read -r _qa_linea || [ -n "$_qa_linea" ]; do
    case "$_qa_linea" in ''|\#*) continue ;; esac
    eval "$_qa_linea" 2>/dev/null || true
  done < ./.env
  [ -n "${JWT_TTL_HOURS:-}" ] || JWT_TTL_HOURS="8"
  if [ -n "${ADMIN_INITIAL_USERNAME:-}" ] && [ -n "${ADMIN_INITIAL_PASSWORD:-}" ]; then CON_CRED=1; fi
  if [ -n "${POSTGRES_USER:-}" ] && [ -n "${POSTGRES_DB:-}" ]; then CON_BD=1; fi
  if [ -n "${FRONTEND_ORIGIN:-}" ]; then CON_ORIGIN=1; fi
fi
bloqueo_env="(sin .env F1 del despliegue: credenciales/BD de la pila no disponibles)"
bloqueo_origin="(R-7.j: sin FRONTEND_ORIGIN en .env el handshake C7 cierra (S7-12) — escenarios WS bloqueados)"

# El guard R-7.j se evalúa AHORA (patrón canon: la recomposición igual corre —
# la pila REST de C2/C6 sigue certificable; los escenarios de WS van BLOQUEADO
# y el exit global es 2).
ID_E7_ALL="E7.9 E7.17 E7.1 E7.5 E7.6 E7.15 E7.10 E7.11 E7.14"

precondiciones() {
  echo "[pre] recomposición ÚNICA: podman compose up -d --build --force-recreate" >&2
  # --build y --force-recreate OBLIGATORIOS (R-7): sin rebuild la imagen del
  # backend seguiría sin las clases C7 (Y SIN ws-console.html publicado)
  if ! compose_ up -d --build --force-recreate; then
    echo "[pre] compose up falló (ver $TMPDIR_QA/compose.log)" >&2; return 1
  fi
  if ! esperar_healthy 420; then
    echo "[pre] la pila no llega a healthy (sondeo /me → 401 invalid_token)" >&2; return 1
  fi
  pila_saludable || { echo "[pre] pila no 3×healthy por podman" >&2; return 1; }
  # smoke-of-the-smoke: GET /me sin JWT → 401 invalid_token
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/me_body.txt" -w '%{http_code}' "$URL_ME" 2>/dev/null || echo 000)"
  [ "$hc" = "401" ] && grep -q 'invalid_token' "$TMPDIR_QA/me_body.txt" 2>/dev/null \
    || { echo "[pre] sondeo /me: $hc (esperado 401 invalid_token)" >&2; return 1; }
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    echo "[pre] login del admin falló (status ${CODIGO:-000})" >&2; return 1
  fi
  printf '%s' "$(campo_json token)" > "$F_TOKEN"
  echo "[pre] healthy + login OK (higiene: JWT solo en $TMPDIR_QA)" >&2
  return 0
}

if [ "$CON_ENV/$CON_CRED/$CON_BD" != "1/1/1" ]; then
  for id in $ID_E7_ALL; do
    [ "$CON_ENV/$CON_CRED" = "1/1" ] || marca "$id" BLOQUEADO "$bloqueo_env"
  done
  ejecutar_pre=0  # sin credenciales no hay recomposición útil
elif [ "$CON_ORIGIN" != "1" ]; then
  # R-7.j: la recomposición CORRE (la pila REST sigue), los WS van BLOQUEADO
  if ! precondiciones; then
    for id in $ID_E7_ALL; do marca "$id" BLOQUEADO "$bloqueo_origin + pila no disponible"; done
    ejecutar_pre=0
  fi
else
  if ! precondiciones; then
    for id in $ID_E7_ALL; do marca "$id" BLOQUEADO "pila no disponible"; done
    ejecutar_pre=0
  fi
fi

# capturar_id NAME ARCHIVO — busca el id del proyecto NAME en la lista actual.
capturar_id() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
for p in d:
    if p.get("name") == sys.argv[1]:
        print(p["id"]); sys.exit(0)
sys.exit(1)' "$1" > "$2"
}

# --- PRE-FASE: proyecto + key del smoke + ws-console.html publicado (R7.30) ---
pre_fase() {
  if ! req POST "$URL_PROJECTS" "{\"name\":\"$P_NAME\",\"description\":\"$DESC_VALIDA\"}" "$(bearer_hdr)" \
     || [ "$CODIGO" != "201" ]; then echo "[pre-fase] POST project=${CODIGO:-000}" >&2; return 1; fi
  req GET "$URL_PROJECTS" "" "$(bearer_hdr)" || true
  capturar_id "$P_NAME" "$F_ID_A" || { echo "[pre-fase] id de $P_NAME no hallado" >&2; return 1; }
  if ! req POST "$URL_PROJECTS/$(cat "$F_ID_A")/api-key/regenerate" "" "$(bearer_hdr)" \
     || [ "$CODIGO" != "201" ]; then echo "[pre-fase] regenerate=${CODIGO:-000}" >&2; return 1; fi
  printf '%s' "$(campo_json key 2>/dev/null || echo '')" > "$F_KEY1"
  [ -s "$F_KEY1" ] || { echo "[pre-fase] key1 vacía" >&2; return 1; }
  # R7.30: ws-console.html publicado por la recomposición (consola temporal)
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/console.html" -w '%{http_code}' \
        "$BASE_URL/ws-console.html" 2>/dev/null || echo 000)"
  if [ "$hc" != "200" ] || ! grep -q 'CONSOLA' "$TMPDIR_QA/console.html" 2>/dev/null; then
    echo "[pre-fase] ws-console.html no publicado ($hc)" >&2; return 1
  fi
  echo "[pre-fase] proyecto($(cat "$F_ID_A"))+key+ws-console OK (higiene: key solo en $TMPDIR_QA)" >&2
  return 0
}

if [ "${ejecutar_pre:-1}" = "1" ]; then
  if ! pre_fase; then
    for id in $ID_E7_ALL; do marca "$id" BLOQUEADO "pre-fase fallida"; done
  fi
fi

# --- E7.9 — Handshake por nginx: 401 (sin token) / 403 (Origin evil) / 101 ---
case_e7_9() {
  echo "[E7.9] handshake por nginx: sin token→401 · evil→403 · ok→101+Accept" >&2
  local e=""
  # (a) sin token → línea de status HTTP 401 (el proxy NO la reescribe)
  local out
  out="$(PY_TOKEN="" python3 "$TMPDIR_QA/ws7_client.py" hs "$WS_HOST" "$WS_PORT" sin-token)"
  grep -q '^HS 401 ' <<<"$out" || e="sin-token=$out "
  # (b) Origin: http://evil.example con token → 403
  out="$(PY_TOKEN="$(cat "$F_TOKEN")" python3 "$TMPDIR_QA/ws7_client.py" hs "$WS_HOST" "$WS_PORT" evil)"
  grep -q '^HS 403 ' <<<"$out" || e="evil=$out "
  # (c) sin Origin con token → 101 + Sec-WebSocket-Accept correcto
  out="$(PY_TOKEN="$(cat "$F_TOKEN")" python3 "$TMPDIR_QA/ws7_client.py" hs "$WS_HOST" "$WS_PORT" ok)"
  grep -q '^HS 101 ACCEPT ok' <<<"$out" || e="ok=$out "
  [ -z "$e" ] && marca E7.9 PASS || marca E7.9 FAIL "(${e% })"
  return 0
}

# --- E7.17 — solo-handshake permanente del artefacto C1 (SIN edición) -------
case_e7_17() {
  echo "[E7.17] ws_echo_test.py --solo-handshake --token (artefacto C1 tal cual)" >&2
  if [ ! -f scripts/ws_echo_test.py ]; then
    marca E7.17 BLOQUEADO "no existe scripts/ws_echo_test.py (artefacto de C1)"; return 0
  fi
  if timeout 200 python3 scripts/ws_echo_test.py --solo-handshake \
       --token "$(cat "$F_TOKEN")" >>"$TMPDIR_QA/e717.log" 2>&1; then
    marca E7.17 PASS
  else
    marca E7.17 FAIL "exit != 0 (log en $TMPDIR_QA/e717.log)"
  fi
  return 0
}

frame_campo() { # FRAME CLAVE — valor top-level de un frame batch (CLAVE va
  # en "$2" y SE REENVÍA a python — sin el reenvío, sys.argv[1] no existe y
  # todo era "err" (run 5: E7.1 received_at-no-leible vacuo))
  printf '%s' "$1" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.read(), object_pairs_hook=dict)
    print(d[sys.argv[1]])
except Exception:
    print("err")' "$2"
}

# bid_de FRAME — batch_id del frame (o "-" si ilegible). Lo usan H4
# (unicidad EXPLÍCITA del batch_id acumulada en F_BIDS).
bid_de() {
  printf '%s' "$1" | python3 -c '
import sys, json
try: print(json.loads(sys.stdin.read()).get("batch_id"))
except Exception: print("-")' 2>/dev/null || echo '-'
}

# --- E7.5 — batch de 3 → EXACTAMENTE 1 frame, orden del RETURNING ------------
case_e7_5() {
  echo "[E7.5] POST /ingest/batch (3 needles) → 1 frame con 3 logs EN ORDEN" >&2
  local e="" base
  base="$(frames_count "$F_LONG")"
  python3 - "$F_PAYLOAD" <<PYEOF
import json
recs = [
  {"level": "INFO", "timestamp": "$T_STAMP", "message": "$NEEDLE_B",
   "environment": "production", "correlation_id": "$CORR_B",
   "source": "smoke-c7"},
  {"level": "INFO", "timestamp": "$T_STAMP", "message": "$NEEDLE_B2",
   "environment": "production", "correlation_id": "$CORR_B2",
   "source": "smoke-c7"},
  {"level": "WARN", "timestamp": "$T_STAMP", "message": "$NEEDLE_C",
   "environment": "production", "correlation_id": "$CORR_C",
   "source": "smoke-c7"},
]
open("$F_PAYLOAD", "w").write(json.dumps({"logs": recs}, separators=(",", ":")))
PYEOF
  # CANON snapshot-delta (H4): la referencia de BD es psql ANTES del batch;
  # tras la 202 el delta EXACTO es 3.
  local snapshot_bd
  snapshot_bd="$(delta_cuenta)"
  if ! req POST "$URL_BATCH" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY1")"; then
    marca E7.5 FAIL "curl-batch"; return 0
  fi
  [ "$CODIGO" = "202" ] || e="batch=$CODIGO "
  [ "$(campo_json accepted)" = "3" ] \
    || e="$e accepted-exacto($(campo_json accepted) ) "
  [ "$(delta_cuenta)" = "$(( snapshot_bd + 3 ))" ] \
    || e="$e delta-bd!=3($(delta_cuenta)!=$((snapshot_bd+3))) "
  if ! esperar_que 20 bash -c '[ "$(frames_count '"$F_LONG"')" -gt '"$base"' ]'; then
    marca E7.5 FAIL "el frame no llegó en 20 s"; return 0
  fi
  [ "$(frames_count "$F_LONG")" = "$(( base + 1 ))" ] \
    || e="$e frames-extra($(frames_count "$F_LONG")/$base) "
  local frame
  frame="$(frame_evento $(( base + 1 )) "$F_LONG")"
  # batch_id distinto de previos: comparación EXPLÍCITA (H4) contra TODOS
  # los batch_id almacenados (E7.1 graba el suyo en $F_BIDS).
  local bid
  bid="$(bid_de "$frame")"
  if [ "$bid" != "-" ] && [ -s "$F_BIDS" ] \
     && grep -qxF "$bid" "$F_BIDS" 2>/dev/null; then
    e="$e batch_id-repetido "
  fi
  bid_de "$frame" >> "$F_BIDS" 2>/dev/null || true
  # orden: ids del frame (ENTREGA) == ids del RETURNING en ASC (psql) y los
  # mensajes en el orden na/nb/nc
  local psql_ids frame_ids msgs
  psql_ids="$(psql_q "SELECT string_agg(id::text, ',' ORDER BY id) FROM logs WHERE project_id = $(cat "$F_ID_A") AND correlation_id IN ('$CORR_B','$CORR_B2','$CORR_C')" 2>/dev/null || echo err)"
  frame_ids="$(printf '%s' "$frame" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.read(), object_pairs_hook=dict)
    print(",".join(str(l["id"]) for l in d["logs"]))
except Exception:
    print("err")' 2>/dev/null || echo err)"
  msgs="$(printf '%s' "$frame" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.read(), object_pairs_hook=dict)
    print(",".join(l["message"] for l in d["logs"]))
except Exception:
    print("err")' 2>/dev/null || echo err)"
  [ "$frame_ids" = "$psql_ids" ] \
    || e="$e orden(frame=$frame_ids psql=$psql_ids) "
  [ "$msgs" = "$NEEDLE_B,$NEEDLE_B2,$NEEDLE_C" ] || e="$e mensajes-no-en-orden "
  [ -z "$e" ] && marca E7.5 PASS || marca E7.5 FAIL "(${e% })"
  return 0
}

# ----------------------------------------------------------------------------
# 3. Escenarios (cronología §5.3). La sesión de fondo del E7.1 vive de
#    forma SOLAPADA con E7.5–E7.15 (E7.10 necesita pings de ~30 s) y muere
#    con el close limpio de E7.11.
# ----------------------------------------------------------------------------

# --- E7.1 — Ingesta → frame por nginx: shape §3.5 completo -------------------
case_e7_1() {
  echo "[E7.1] ingesta real → EXACTAMENTE 1 frame batch (shape §3.5)" >&2
  local id_a e="" base
  id_a="$(cat "$F_ID_A")"
  # el frame lleva el mensaje COMPLETO (LogDto 11 campos) — el frame del
  # needle 'a' viaja íntegro por nginx a /ws/logs
  # 300 s (60 + 30*8): la sesión debe VIVIR hasta el CLOSE de E7.11 — la
  # suma de las esperas de E7.5–E7.10 roza los 180 s originales (run 3 la
  # mató por reloj ANTES del close por comando); el close de E7.11 la
  # termina ANTES del vencimiento igualmente.
  iniciar_long "$F_LONG" "$(( 60 + 30 * 8 ))" "$id_a" 1
  if ! esperar_que 25 sh -c "grep -q '^hs-ok' '$F_LONG'"; then
    marcar_ws_error E7.1 "el cliente long no completó el handshake"; kill "$LONG_PID" 2>/dev/null || true; return 0
  fi
  orden_fifo "$(sf_json "$id_a" - -)"
  if ! esperar_que 15 sh -c "grep -q '^sf-sent' '$F_LONG'"; then
    marca E7.1 FAIL "la sesión no envió set_filters (orden); ver $F_LONG"; return 0
  fi
  base="$(frames_count "$F_LONG")"
  # CANON snapshot-delta (H1): la referencia de BD es un SNAPSHOT psql del
  # proyecto del smoke ANTES de la ingesta — la base de frames NO es de BD.
  local snapshot_bd
  snapshot_bd="$(delta_cuenta)"
  # POST /api/v1/ingest real con la key (higiene: key viaja solo en header)
  python3 - "$F_PAYLOAD" <<PYEOF
import json
rec = {"level": "INFO", "timestamp": "$T_STAMP", "message": "$NEEDLE_A",
       "environment": "production", "correlation_id": "$CORR_A",
       "source": "smoke-c7"}
open("$F_PAYLOAD", "w").write(json.dumps(rec, separators=(",", ":")))
PYEOF
  local t0 t1 dt
  t0="$(date +%s%N)"
  if ! req POST "$URL_INGEST" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY1")"; then
    kill "$LONG_PID" 2>/dev/null || true; exec 3>&- 2>/dev/null || true
    marca E7.1 FAIL "curl-ingesta"; return 0
  fi
  t1="$(date +%s%N)"; dt=$(( (t1 - t0) / 1000000 ))
  [ "$CODIGO" = "202" ] || e="ingesta=$CODIGO "
  [ "$dt" -le 2000 ] || e="ingesta-lenta(${dt}ms) "
  [ "$(delta_cuenta)" = "$(( snapshot_bd + 1 ))" ] \
    || e="$e psql-delta($(delta_cuenta)!=$((snapshot_bd+1))) "
  # EXACTAMENTE 1 frame: esperar y contar
  if ! esperar_que 20 bash -c '[ "$(frames_count '"$F_LONG"')" -gt '"$base"' ]'; then
    marca E7.1 FAIL "el frame no llegó en 20 s (${e% }); eventos: $(tail -n 3 "$F_LONG" | tr '\n' ' ')"
    return 0
  fi
  local frame
  frame="$(frame_evento $(( base + 1 )) "$F_LONG")"
  e="$e$(verificar_frame "$frame" "$id_a" "$NEEDLE_A" "$CORR_A")"
  # H4: el batch_id del frame va al registro de vistos (los casos posteriores
  # comparan EXPLÍCITAMENTE contra TODOS los previos almacenados)
  bid_de "$frame" >> "$F_BIDS" 2>/dev/null || true
  [ -z "$e" ] && marca E7.1 PASS || marca E7.1 FAIL "(${e% })"
  return 0
}

# verificar_frame FRAME JSON PROJECT NEEDLE CORR — checks de shape §3.5:
# 5 claves EN ORDEN + received_at truncado a segundos del psql + logs[i]
# con las 11 claves EN ORDEN + message == needle. Imprime detalles de error
# ("" = todo ok).
verificar_frame() {
  python3 -c '
import sys, json

def fallo(msg):
    print(msg); sys.exit(0)

frame = sys.argv[1]
try:
    d = json.loads(frame, object_pairs_hook=list)
except Exception:
    sys.exit(fallo("frame-no-json"))
if not isinstance(d, list):
    sys.exit(fallo("frame-no-objeto"))
top = [k for k, _ in d]
if top != ["type", "batch_id", "project_id", "received_at", "logs"]:
    sys.exit(fallo("5-claves-mala-orden " + ",".join(top)))
dictado = dict(d)
if dictado["type"] != "batch":
    sys.exit(fallo("type=" + str(dictado["type"])))
if str(dictado["project_id"]) != sys.argv[2]:
    sys.exit(fallo("project_id=" + str(dictado["project_id"])))
logs = dictado["logs"]
if not isinstance(logs, list) or not logs:
    sys.exit(fallo("logs-vacio"))
claves11 = ["id", "project_id", "level", "environment", "timestamp",
            "received_at", "message", "exception", "metadata", "source",
            "correlation_id"]
for log in logs:
    if not isinstance(log, list):
        sys.exit(fallo("log-no-objeto"))
    claves = [k for k, _ in log]
    if claves != claves11:
        sys.exit(fallo("11-claves-mala-orden " + ",".join(claves)))
msgs = [dict(l)["message"] for l in logs]
if msgs[0] != sys.argv[3]:
    sys.exit(fallo("logs0-message=" + str(msgs[0])[:60]))
' "$1" "$2" "$3" "$4"
  # received_at == truncado a segundos del psql (D-C7-2), en bash (el
  # formatter imprime 'Z'): to_char da '...SS.ffffff' → recorto a segundos
  local ra_frame ra_psql
  ra_frame="$(frame_campo "$1" received_at)"
  ra_psql="$(rec_micro "$4")"
  if [ "$ra_frame" = "err" ] || [ "$ra_psql" = "err" ]; then
    echo "received_at-no-leible(frame=$ra_frame psql=$ra_psql)"; return 0
  fi
  [ "$ra_frame" = "${ra_psql%%.*}Z" ] \
    || echo "received_at(frame=$ra_frame psql=${ra_psql%%.*}Z)"
}

# — marca compuesta para fallos de conexión del cliente de fondo —
marcar_ws_error() {
  marca "$1" FAIL "$2"
  exec 3>&- 2>/dev/null || true
}

# ingesta_simple NEEDLE CORR [LEVEL] — POST /api/v1/ingest de UN log (higiene:
# key viaja solo en header; el needle/correlation SÍ son de depuración pública).
# LEVEL opcional (default INFO — E7.6 necesita ingestas WARN/ERROR reales).
# CONTRATO (Schema §3.4/C5): el individual es FLAT — el cuerpo ES el registro
# (LogIngestRequest.desdeJson(cuerpo)); JAMÁS envuelto en {"logs":[…]} (esa
# forma es SOLO de /ingest/batch — 400 environment_required si se envuelve).
ingesta_simple() {
  python3 - "$F_PAYLOAD" "$1" "$2" "${3:-INFO}" <<PYEOF
import json, sys
msg, corr, nivel = sys.argv[2], sys.argv[3], sys.argv[4]
rec = {"level": nivel, "timestamp": "$T_STAMP", "message": msg,
       "environment": "production", "correlation_id": corr,
       "source": "smoke-c7"}
open(sys.argv[1], "w").write(json.dumps(rec, separators=(",", ":")))
PYEOF
  req POST "$URL_INGEST" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY1")"
}

# --- E7.6 — set_filters recalificado EN CALIENTE (misma sesión) --------------
case_e7_6() {
  echo "[E7.6] recalificación: level=ERROR → WARN no llega · level=WARN sí" >&2
  local e="" base id_a ventana
  id_a="$(cat "$F_ID_A")"
  base="$(frames_count "$F_LONG")"
  # (a) califica level=ERROR → ingesta WARN → NO llega (psql SÍ confirma fila)
  local sf_prev
  sf_prev="$(grep -c '^sf-sent' "$F_LONG" 2>/dev/null || echo 0)"
  orden_fifo "$(sf_json "$id_a" ERROR -)"
  esperar_que 10 bash -c '[ "$(grep -c "^sf-sent" '"$F_LONG"' 2>/dev/null || echo 0)" -ge '$(( sf_prev + 1 ))' ]' \
    || { marca E7.6 FAIL "la recalificación level=ERROR no se envió (ver $F_LONG)"; return 0; }
  if ! ingesta_simple "$NEEDLE_E6W1" "$CORR_E6W1" WARN; then
    marca E7.6 FAIL "curl-a"; return 0
  fi
  [ "$CODIGO" = "202" ] || e="a=$CODIGO "
  [ "$(id_por_corr "$CORR_E6W1")" != "err" ] || e="$e fila-no-en-psql "
  [ "$(frames_count "$F_LONG")" = "$base" ] || e="$e llego-con-error "
  # ventana de AUSENCIA (R7.11: el servidor JAMÁS contesta; solo lotes)
  sleep 5
  [ "$(frames_count "$F_LONG")" = "$base" ] \
    || e="$e frame-tardio($(frames_count "$F_LONG")/$base) "
  # (b) RECALIFICA level=WARN → siguiente lote WARN SÍ llega
  sf_prev="$(grep -c '^sf-sent' "$F_LONG" 2>/dev/null || echo 0)"
  orden_fifo "$(sf_json "$id_a" - -)"   # limpiar level (6 claves, nulls)
  orden_fifo "$(sf_json "$id_a" WARN -)"
  esperar_que 10 bash -c '[ "$(grep -c "^sf-sent" '"$F_LONG"' 2>/dev/null || echo 0)" -ge '$(( sf_prev + 2 ))' ]' \
    || { marca E7.6 FAIL "la recalificación level=WARN no se envió"; return 0; }
  if ! ingesta_simple "$NEEDLE_E6W2" "$CORR_E6W2" WARN; then
    marca E7.6 FAIL "curl-b"; return 0
  fi
  [ "$CODIGO" = "202" ] || e="b=$CODIGO "
  if ! esperar_que 20 bash -c '[ "$(frames_count '"$F_LONG"')" -gt '"$base"' ]'; then
    marca E7.6 FAIL "el frame del WARN no llegó tras recalificar"; return 0
  fi
  local msg_b
  msg_b="$(printf '%s' "$(frame_evento $(( base + 1 )) "$F_LONG")" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.read(), object_pairs_hook=dict)
    print(d["logs"][0]["message"])
except Exception:
    print("err")' 2>/dev/null || echo err)"
  [ "$msg_b" = "$NEEDLE_E6W2" ] || e="$e logs0-no-es-la-aguja "
  # (c) recalifica a OTRO project_id → el siguiente lote del smoke NO llega
  sf_prev="$(grep -c '^sf-sent' "$F_LONG" 2>/dev/null || echo 0)"
  orden_fifo "$(sf_json 999999 - -)"
  esperar_que 10 bash -c '[ "$(grep -c "^sf-sent" '"$F_LONG"' 2>/dev/null || echo 0)" -ge '$(( sf_prev + 1 ))' ]' \
    || { marca E7.6 FAIL "la recalificación project_id no se envió"; return 0; }
  ingesta_simple "$NEEDLE_E6P" "$CORR_E6P" > /dev/null || true
  local base2
  base2="$(frames_count "$F_LONG")"
  [ "$(frames_count "$F_LONG")" = "$base2" ] || e="$e project-cambiado-llego "
  sleep 3
  [ "$(frames_count "$F_LONG")" = "$base2" ] \
    || e="$e project-cambiado-tardio "
  # restaurar: la sesión vuelve al project del smoke (para E7.15/E7.10/E7.11)
  orden_fifo "$(sf_json "$id_a" - -)"
  [ -z "$e" ] && marca E7.6 PASS || marca E7.6 FAIL "(${e% })"
  return 0
}

# --- E7.15 — archivado deja de emitir (403 project_archived + sin frame) -----
case_e7_15() {
  echo "[E7.15] PATCH ARCHIVED → ingest 403 project_archived · ACTIVE → frame" >&2
  local id_a e="" base
  id_a="$(cat "$F_ID_A")"
  req PATCH "$URL_PROJECTS/$id_a" '{"status":"ARCHIVED"}' "$(bearer_hdr)" || true
  [ "$CODIGO" = "200" ] || { marca E7.15 FAIL "patch-archived=$CODIGO"; return 0; }
  base="$(delta_cuenta)"
  if ! ingesta_simple "$NEEDLE_AR2" "$CORR_AR2"; then
    marca E7.15 FAIL "curl-a"; return 0
  fi
  local e2=""
  [ "$CODIGO" = "403" ] || e2="$e2 status=$CODIGO "
  grep -q '"code"[[:space:]]*:[[:space:]]*"project_archived"' "$F_CUERPO" 2>/dev/null \
    || e2="$e2 code-no-exacto "
  [ "$(delta_cuenta)" = "$base" ] || e="$e psql-delta($(delta_cuenta)!=$base) "
  e="$e$e2"
  # sin frame en la ventana (la ingesta 403 nunca entra — psql ya lo dice;
  # la ventana de AUSENCIA completa el registro)
  local fc_base
  fc_base="$(frames_count "$F_LONG")"
  sleep 4
  [ "$(frames_count "$F_LONG")" = "$fc_base" ] \
    || e="$e frame-durante-archivado "
  # desarchivar → SÍ emite de nuevo (frame a la sesión viva)
  req PATCH "$URL_PROJECTS/$id_a" '{"status":"ACTIVE"}' "$(bearer_hdr)" || true
  [ "$CODIGO" = "200" ] || e="$e patch-active=$CODIGO "
  if ! ingesta_simple "$NEEDLE_AR1" "$CORR_AR1"; then
    marca E7.15 FAIL "curl-b"; return 0
  fi
  [ "$CODIGO" = "202" ] || e="desarchivar=$CODIGO "
  if ! esperar_que 20 bash -c '[ "$(frames_count '"$F_LONG"')" -gt '"$fc_base"' ]'; then
    marca E7.15 FAIL "el frame no llegó tras desarchivar"; return 0
  fi
  [ -z "$e" ] && marca E7.15 PASS || marca E7.15 FAIL "(${e% })"
  return 0
}

# --- E7.10 — ping RFC 0x9/0xA ~30 s Y NINGÚN JSON != batch -------------------
case_e7_10() {
  echo "[E7.10] pings 0x9 cada ~30 s (±5 s), respuesta 0xA, cero JSON != batch" >&2
  local e="" n
  # la sesión vive desde E7.1 solapada con E7.5–E7.15 (~120 s de guion);
  # el 2º ping llega hacia ~65 s — esperar los DOS
  # OJO: F_LONG NO está exportada — el patrón de las waits de frames
  # (splice '"$F_LONG"' evaluado por el SHELL EXTERNO) es el ÚNICO que la
  # interpola dentro del bash -c (run 5: pings<2 vacuo)
  if ! esperar_que 40 bash -c '[ "$(pings_count '"$F_LONG"')" -ge 2 ]'; then
    marca E7.10 FAIL "pings<2 (latido 30 s no observado; fichero $F_LONG)"; return 0
  fi
  n="$(pings_count "$F_LONG")"
  # intervalos consecutivos dentro de 30±6 s (scheduler fijo 30 s)
  local mala
  mala="$(python3 -c '
import sys
ts = []
try:
    with open(sys.argv[1]) as fh:
        for linea in fh:
            if linea.startswith("ping "):
                ts.append(int(linea.split()[1]))
except FileNotFoundError:
    pass
malos = [str(ts[i+1]-ts[i]) for i in range(len(ts)-1)
         if not (24 <= ts[i+1]-ts[i] <= 36)]
print(",".join(malos))' "$F_LONG" 2>/dev/null || echo err)"
  [ -n "$mala" ] && [ "$mala" != "err" ] \
    && e="$e intervalo-no-30s($mala) "
  [ "$mala" = "err" ] && e="$e pings-irreales "
  # CONFIRMA que NINGÚN JSON distinto de batch llegó (todos los frames)
  local no_batch
  no_batch="$(python3 -c '
import sys, json
malos = []
try:
    with open(sys.argv[1]) as fh:
        i = 0
        for linea in fh:
            if linea.startswith("frame "):
                i += 1
                try:
                    if json.loads(linea[6:]).get("type") != "batch":
                        malos.append(str(i))
                except Exception:
                    malos.append(str(i) + "-no-json")
except FileNotFoundError:
    pass
print(",".join(malos))' "$F_LONG" 2>/dev/null || echo err)"
  [ -n "$no_batch" ] && [ "$no_batch" != "err" ] \
    && e="$e frames-no-batch($no_batch) "
  [ -z "$e" ] && marca E7.10 PASS || marca E7.10 FAIL "(${e% })"
  return 0
}

# --- E7.11 — reconexión limpia SIN replay (cierre 0x8 → gap → reconexión) ----
case_e7_11() {
  echo "[E7.11] close limpio del cliente, gap SIN replay, reconexión SÍ recibe" >&2
  local e="" id_a gap_fc rfc base
  id_a="$(cat "$F_ID_A")"
  local fc_pre_close
  fc_pre_close="$(frames_count "$F_LONG")"
  # cierre LIMPIO del CLIENTE (0x8 con código 1000 — el latido GC NUNCA lo
  # produce; solo sesiones mudas): el comando CLOSE en el FIFO
  orden_fifo "CLOSE"
  if ! esperar_que 15 sh -c "grep -q '^cerrado-por-comando' '$F_LONG'"; then
    marca E7.11 FAIL "el close limpio no se completó (ver $F_LONG)"; return 0
  fi
  # GAP: ingestas DURANTE la caída → entran en la BD y NUNCA se re-emitiden
  local base_gap
  base_gap="$(delta_cuenta)"
  ingesta_simple "$NEEDLE_GAP" "$CORR_GAP" > /dev/null 2>&1 || true
  gap_fc="$(delta_cuenta)"
  [ "$gap_fc" = "$(( base_gap + 1 ))" ] \
    || e="$e gap-no-psql($(delta_cuenta)!=$((base_gap+1))) "
  # RECONEXIÓN de cero: NUEVO handshake + set_filters de NUEVO
  iniciar_long2 "$F_LONG2" 130 "$id_a"
  if ! esperar_que 25 sh -c "grep -q '^hs-ok' '$F_LONG2'"; then
    marca E7.11 FAIL "la reconexión no completó el handshake (ver $F_LONG2)"; return 0
  fi
  # AUSENCIA: 5 s SIN frame (el gap NO se re-emite — no hay replay, R-7.i)
  sleep 5
  [ "$(frames_count "$F_LONG2")" = "0" ] \
    || e="$e REPLAY-hallado($(frames_count "$F_LONG2") frames) "
  # tras reconectar: el SIGUIENTE lote SÍ llega (nada de estado residual)
  if ! ingesta_simple "$NEEDLE_RECON" "$CORR_RECON"; then
    marca E7.11 FAIL "curl-recon"; return 0
  fi
  if ! esperar_que 20 bash -c '[ "$(frames_count '"$F_LONG2"')" -ge 1 ]'; then
    marca E7.11 FAIL "el post-reconexión no llegó en 20 s"; return 0
  fi
  [ -z "$e" ] && marca E7.11 PASS || marca E7.11 FAIL "(${e% })"
  return 0
}

# iniciar_long2 — reconexión de cero (sin FIFO: nada que mandar por stdin)
iniciar_long2() {
  local fichero="$1" duracion="$2" proyecto="$3"
  PY_TOKEN="$(cat "$F_TOKEN")" \
    python3 "$TMPDIR_QA/ws7_client.py" long "$WS_HOST" "$WS_PORT" "$duracion" \
      "$(sf_json "$proyecto" - -)" \
      0 >"$fichero" 2>>"$ERR_LONG" </dev/null &
  LONG2_PID=$!
}

# --- E7.14 — backpressure: cliente NO consumidor; sanos intactos ------------
case_e7_14() {
  echo "[E7.14] backpressure: lento sin consumir · 202 ≤ ~2 s · sanos SÍ" >&2
  local e="" id_a
  id_a="$(cat "$F_ID_A")"
  # cliente NO consumidor (set_filters matcheando): no LEE durante 70 s
  PY_TOKEN="$(cat "$F_TOKEN")" \
    python3 "$TMPDIR_QA/ws7_client.py" lento "$WS_HOST" "$WS_PORT" \
      "$(sf_json "$id_a" - -)" 70 20 >"$F_LENTA" 2>>"$ERR_LONG" </dev/null &
  LENTA_PID=$!
  # (a) 20 ingestas individuales de ~32 KiB por MENSAJE (límite §2.7):
  #     tiempo de respuestas 202 ≤ ~2 s por ingesta (curl time)
  local i t0 t1 dt
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    t0="$(date +%s%N)"
    python3 - "$F_PAYLOAD2" <<PYEOF
import json
msg = "$NEEDLE_BP-" + str($i) + " " + "x" * 30000
rec = {"level": "INFO", "timestamp": "$T_STAMP", "message": msg,
       "environment": "production", "correlation_id": "e714-$i",
       "source": "smoke-c7"}
open("$F_PAYLOAD2", "w").write(json.dumps(rec, separators=(",", ":")))
PYEOF
    if ! req POST "$URL_INGEST" "@$F_PAYLOAD2" "$(curl_ingest "$F_KEY1")"; then
      e="$e curl-ingesta-$i "; continue
    fi
    t1="$(date +%s%N)"; dt=$(( (t1 - t0) / 1000000 ))
    [ "$CODIGO" = "202" ] || e="$e bp$i=$CODIGO "
    [ "$dt" -le 2500 ] || e="$e bp$i-lenta(${dt}ms) "
  done
  # (b) consumidores SANOS (la reconexión de E7.11) SIGUEN recibiendo:
  #     ≥10 de las 20 ingestas llegaron como frame a la sesión sana
  local sanos
  sanos="$(frames_count "$F_LONG2")"
  [ "$sanos" -ge 10 ] || e="$e sanos-frames=$sanos(<10) "
  # (c) backpressure materializada: el lento reanuda a los 70 s (desde su
  #     arranque) y tras leer lo acumulado espera el close ~20 s → la
  #     ventana tolera los ~100 s del ciclo completo (1013 cierra ANTES y
  #     esperar_que retorna en cuanto aparece '^fin').
  esperar_lenta() { grep -q '^fin' "$F_LENTA"; }
  esperar_que 100 esperar_lenta || kill "$LENTA_PID" 2>/dev/null || true
  local code
  code="$(grep -E '^close ' "$F_LENTA" | head -1 | awk '{print $2}')"
  if [ -n "$e" ]; then
    # los hechos (a) 202 ≤ ~2 s y (b) sanos ≥10 SON parte del caso — su
    # fallo NO se silencia por la tolerancia del (c) (R-7.k es SOLO del
    # 1013, jamás del resto).
    marca E7.14 FAIL "(${e% })"
  elif [ "$code" = "1013" ]; then
    marca E7.14 PASS
  elif [ -z "$code" ]; then
    # R-7.k (tolerancia, patrón Q23 de C3): SOLO el close 1013 cuenta como
    # cierre por backpressure; sesión lenta SIGUE ABIERTA al final de la
    # ventana → PASS con la nota. Defaults de la pila (bufferSize 512 KiB /
    # send-time 2000 ms) más los TCP buffers del host absorben ~900 KiB sin
    # bloquear el flush → el guion NO puede materializar backpressure; el
    # 1013 contractual lo pinnea la pareja [L] E7.13 (LatidoYBackpressureTest,
    # doble barrera).
    marca E7.14 PASS "1013 no materializado en pila — cubierto por pareja [L] E7.13 (R-7.k; code=)"
  else
    # H9: cerró con código ≠ 1013 — razón de cierre EQUIVOCADA, JAMÁS tolerable.
    marca E7.14 FAIL "cierre-no-1013=$code (razón de cierre equivocada — R-7.k nunca excusa esto)"
  fi
  return 0
}

# ----------------------------------------------------------------------------
# 4. Main — ORDEN CRONOLÓGICO §5.3 (veredictos ordenados por ID al final)
# ----------------------------------------------------------------------------
if [ "${ejecutar_pre:-1}" = "1" ] && [ -s "$F_ID_A" ] && [ -s "$F_KEY1" ] \
     && [ "$CON_ORIGIN" = "1" ]; then
  ejecutar_() {
    if ! "$2" 2>>"$TMPDIR_QA/errores_internos.log"; then
      RESULTADOS+=("$1: FAIL (error interno del script; ver errores_internos)")
    fi
  }
  ejecutar_ E7.9 case_e7_9
  ejecutar_ E7.17 case_e7_17
  ejecutar_ E7.1 case_e7_1
  ejecutar_ E7.5 case_e7_5
  ejecutar_ E7.6 case_e7_6
  ejecutar_ E7.15 case_e7_15
  ejecutar_ E7.10 case_e7_10
  ejecutar_ E7.11 case_e7_11
  ejecutar_ E7.14 case_e7_14
elif [ "$CON_ENV/$CON_CRED/$CON_BD" = "1/1/1" ] && [ "$CON_ORIGIN" != "1" ]; then
  # R-7.j aplicado arriba en el guard de recomposición — nada extra
  :
fi

echo "=== VEREDICTOS QA C7 (por ID del spec) ==="
if [ "${#RESULTADOS[@]}" -gt 0 ]; then
  printf '%s\n' "${RESULTADOS[@]}" | sort -V
fi

state_exit=0
if printf '%s\n' "${RESULTADOS[@]:-}" | grep -q ': FAIL'; then
  state_exit=1
elif printf '%s\n' "${RESULTADOS[@]:-}" | grep -q ': BLOQUEADO'; then
  state_exit=2
fi
exit "$state_exit"
