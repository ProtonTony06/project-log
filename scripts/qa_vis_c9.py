#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""qa_vis_c9.py — ARNÉS visual Playwright headless del corte C9 (Fase A).

Canon normativo (CITADO, jamás redefinido):
  · spec C9 §0/§1 (R9.1.1…R9.6.5, escenarios E9.1–E9.26 — límite EXACTO de
    G2: 14 parejas + 9 extras + 3 criterios del 06; QP-8.11/8.31/8.38/8.37
    NO EXISTEN como checks), design C9 §1 (secciones (a)–(i)) y §2 (orden de
    corrida B0→B8, D-C9-8/D-C9-9/D-C9-10), higiene S16 (R9.5.2/D-C9-6) y
    regla dura de no fingir PASS (R9.1.4/D-C9-1).
  · Patrón del repro funcional /tmp/qa_c9/repro_detalle.py (login por UI,
    chromium headless, wait_for_url).
  · Exit canon C8 (qa_c8_smoke.sh): 0 = 26 PASS (PASS-CON-NOTA cuentan),
    1 = algún FAIL (o veredicto BLOQUEADO de check), 2 = higiene S16 fallada
    / PILOTO CAÍDO / pila-entorno no disponible.

DIF-0 de producto: este guion JAMÁS toca frontend/** ni backend/**; no hay
entrada de Playwright en ningún package.json (R9.1.3/QP-8.54 — Playwright
vivo por pip fuera de la frontera Vitest).

HIGIENE S16 DURO (R9.5.2/D-C9-6):
  · credenciales/JWT/API-key prefijos NUNCA impresos ni en PNG;
  · parser dotenv EN PROCESO (jamá a os.environ, argv o stdout);
  · TODO page.evaluate devuelve SOLO booleanos/números (matcheos internos);
  · frames WS al log SOLO por type/length;
  · shots de login SIEMPRE con campos VACÍOS; shot de la revelación con la
    línea de la key REDACTADA por overlay inyectado JUSTO para el shot;
  · los literales "plk_" y "eyJ" jamás aparecen en este stdout (el wrapper
    grepea 0 hits sobre el RAW — ni siquiera como texto suelto).

DESTRUCTIVO (R9.1.5/D-C9-5): UNA ventana de kill por corrida, SIEMPRE
última fase: `stop` del servicio `backend` + restauración `up -d backend`
dentro de try/finally + bucle de health con timeout 180 s (señal real de
backend vivo vía el ÚNICO puerto expuesto — ver nota H-C9-SALUD más abajo).
JAMÁS down -v, SQL directo, ni acceso a postgres/volúmenes.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Callable, Optional

# ────────────────────────────────────────────────────────────────────────────
# Config del arnés (paths fijos de la frontera — design §4 File Changes)
# ────────────────────────────────────────────────────────────────────────────

RAIZ = Path(__file__).resolve().parents[1]
DIR_QA = RAIZ / ".atl" / "sdd" / "C9"
DIR_EVID = DIR_QA / "evidencia"
BASE = "http://127.0.0.1:8080"          # ÚNICO puerto del host (ADR-24)
RUTA_ENV = RAIZ / ".env"
RUTA_COMPOSE = RAIZ / "compose.yaml"

TS = time.strftime("%Y%m%d%H%M%S")


def needle(caso: str) -> str:
    """Needle idempotente (R9.5.4): qa-c9-<ts>-<caso> en MESSAGE."""
    return f"qa-c9-{TS}-{caso}"


NOMBRE_PROYECTO = needle("e45")          # proyecto C9 nace por UI en E9.21
NEEDLE_E35 = needle("e35")               # E9.13 acumulado (≥60)
NEEDLE_E25 = needle("e25")               # ERROR production + exception
NEEDLE_E25_STACK = needle("e25-stack")   # needle INTERNO del stacktrace
NEEDLE_E17B = needle("e17b")             # log SIN exception/metadata
NEEDLE_CORR_E25 = needle("corr-e25")
NEEDLE_E10 = needle("e10")               # filtro de E9.10 (sin semilla)
NEEDLE_VACIO = needle("vacio-noexiste")  # E9.12-b estado vacío

LIT_ROJA = f"qa-c9-{TS}-ROJA"            # credenciales malas no-secretas

# ── NOTA H-C9-SALUD ────────────────────────────────────────────────────────
# design §1/(a) y T9.9 citan `GET /actuator/health` como señal. Por el ÚNICO
# puerto expuesto (8080) esa ruta NO llega al backend: nginx solo enruta
# /api/v1/** y /ws/logs (frontend/nginx.conf) y el SPA-fallback responde
# index.html 200 HASTA con el backend caído — como guard única sería una
# señal trivial. El arnés la registra (anotación literal) y pina como señal
# DURO-positiva: GET /api/v1/panel/me SIN token ⇒ HTTP 401 (JSON del
# backend real a través del proxy). La señal 200-de-contenedor-independiente
# sigue siendo responsabilidad del healthcheck del compose (interna).
SALUD_BACKEND = "/api/v1/panel/me"       # espera 401 (no 404/502 → pila caída)

# ────────────────────────────────────────────────────────────────────────────
# Sección (f) — veredicto (estilo canon C8: `id: VEREDICTO (nota)`)
# ────────────────────────────────────────────────────────────────────────────

RESULTADOS: list[str] = []
MARCADOS: set[str] = set()


class Pareja:
    """Estado de la pareja de check en curso.

    Regla dura ESTRUCTURAL (D-C9-1/R9.1.4): `pantalla_vista` se setea SOLO
    por `vista()` (navegación verificada + shot sobre el DOM vivo). Un
    `passO` sin esa flag se auto-FAIL: "falso PASS bloqueado por el arnés".
    """

    def __init__(self, id_check: str) -> None:
        self.id = id_check
        self.pantalla_vista = False
        self.notas: list[str] = []
        self.marcada = False
        self.shots = 0


_ACTUAL: list[Optional[Pareja]] = [None]


def marca(id_check: str, veredicto: str, nota: Optional[str] = None) -> None:
    linea = f"{id_check}: {veredicto}" + (f" ({nota})" if nota else "")
    RESULTADOS.append(linea)
    MARCADOS.add(id_check)
    print(linea, flush=True)


def pareja(id_check: str) -> Pareja:
    p = Pareja(id_check)
    _ACTUAL[0] = p
    print(f"[check] {id_check} …", flush=True)
    return p


def passO(nota: Optional[str] = None) -> None:
    """PASS / PASS-CON-NOTA de la pareja ACTUAL — exige pantalla_vista.
    Idempotente por pareja: si ya hay veredicto, la info se añade como
    sub-nota (una única línea `E9.N: …` — canon G2)."""
    p = _ACTUAL[0]
    if p is None:
        raise RuntimeError("passO() sin pareja activa (defecto del arnés)")
    notas = list(p.notas) + ([nota] if nota else [])
    if not p.pantalla_vista:
        if p.marcada:
            return
        p.marcada = True
        marca(
            p.id,
            "FAIL",
            "falso PASS bloqueado por el arnés: sin pantalla real "
            "observada (navegación+snapshot+shot) — R9.1.4/D-C9-1",
        )
        return
    if p.marcada:
        p.notas.extend(notas)
        return
    p.marcada = True
    if notas:
        marca(p.id, "PASS-CON-NOTA", " · ".join(n for n in notas if n))
    else:
        marca(p.id, "PASS")


def conNota(p: Pareja, texto: str) -> None:
    p.notas.append(texto)


def fail(id_check: Optional[str] = None, motivo: str = "") -> None:
    p = _ACTUAL[0]
    destino = id_check or (p.id if p else "?")
    if p is not None and p.marcada and p.id == destino:
        p.notas.append(f"incidencia adicional: {motivo}")
        return
    if p is not None and p.id == destino:
        p.marcada = True
    elif destino in MARCADOS:
        # veredicto cruzado a una pareja ya marcada (p. ej. sello E9.11):
        RESULTADOS.append(f"{destino}: FAIL ({motivo})")
        return
    marca(destino, "FAIL", motivo)


def bloqueado(detalle: str) -> None:
    """BLOQUEADO honesto (R9.6.4) de la pareja ACTUAL."""
    p = _ACTUAL[0]
    if p is None:
        raise RuntimeError("bloqueado() sin pareja activa")
    marca(p.id, "BLOQUEADO", detalle)


# E9.11 (R9.2.11) tiene 3 partes repartidas entre B6 (a)/(c) y B7 (b, en la
# Misma ventana de kill — D-C9-5): se OBSERVA en cada parte y se SELLA al
# final de B7 con las notas acumuladas (una única línea `E9.11: …` — G2).
E911_STATE: dict = {"notas": [], "error": "", "vista": False, "b_obs": False}


def sellarE911() -> None:
    if "E9.11" in MARCADOS:
        return
    p = pareja("E9.11")
    p.pantalla_vista = bool(E911_STATE["vista"])
    p.notas = list(E911_STATE["notas"])
    if E911_STATE["error"]:
        marca("E9.11", "FAIL", E911_STATE["error"])
    elif not E911_STATE["b_obs"]:
        marca("E9.11", "FAIL", "sin parte (b) observada (ventana de kill "
                               "no completada — defecto del arnés)")
    else:
        passO()


def salirPorVeredictos() -> int:
    """Tabla final ordenada (canon C8) + exit 0/1."""
    print("\n== VEREDICTOS E9 (orden por ID; el orden de corrida NO es el "
          "numérico — D-C9-8) ==", flush=True)
    def orden(linea: str) -> tuple:
        m = re.match(r"[Ee]9\.(\d+)", linea)
        return (int(m.group(1)) if m else 999,)

    fails = 0
    bloqueados = 0
    for r in sorted(RESULTADOS, key=orden):
        print(r)
        if ": FAIL" in r:
            fails += 1
        elif ": BLOQUEADO" in r:
            bloqueados += 1
    n = len(RESULTADOS)
    print(f"\nresumen: {n} marcas · {fails} FAIL · {bloqueados} BLOQUEADO",
          flush=True)
    if n != 26 or len(MARCADOS) != 26:
        print(f"[guard] límite EXACTO de G2 = 26 checks; marcados {n} "
              "(defecto del arnés — R9.6.1)", flush=True)
        return 1
    if fails > 0 or bloqueados > 0:
        return 1
    return 0


# ────────────────────────────────────────────────────────────────────────────
# Salida segura / URL sanitizada (S16)
# ────────────────────────────────────────────────────────────────────────────

def urlSegura(url: str) -> str:
    """Jamás imprime query con token (el handshake del WS la lleva)."""
    u = re.sub(r"token=[^&]+", "token=REDACTADO", url)
    if len(u) > 170:
        u = u[:167] + "..."
    return u


def fase(titulo: str) -> None:
    print(f"\n== {titulo} ==", flush=True)


def notaRun(texto: str) -> None:
    print(f"[nota] {texto}", flush=True)


def cifra(linea: str) -> None:
    print(f"[cifra] {linea}", flush=True)


# ────────────────────────────────────────────────────────────────────────────
# Sección (a) — infra/env: dotenv EN PROCESO + verificarPila + entorno
# ────────────────────────────────────────────────────────────────────────────

def cargarEnvProceso(ruta: Path) -> dict[str, str]:
    """Parser dotenv mínimo en proceso (R9.1.2): valores SOLO a variables
    internas del proceso Python — jamás a os.environ exportado, a argv ni
    impresos. Sin eco de valores: solo nombres de claves. PROHIBIDO en
    el wrapper shell (D-C9-11)."""
    valores: dict[str, str] = {}
    if not ruta.exists():
        print("[guard] .env no encontrado — credenciales de admin "
              "indisponibles (R9.1.2)", flush=True)
        sys.exit(2)
    for cruda in ruta.read_text(encoding="utf-8").splitlines():
        linea = cruda.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        clave, valor = linea.split("=", 1)
        clave = clave.strip()
        valor = valor.strip().strip('"').strip("'")
        valores[clave] = valor
    return valores


ENV = {}  # rellenado en B0 (jamá a os.environ)


def httpGet(ruta: str, timeout: float = 5.0) -> tuple[int, str]:
    url = BASE + ruta
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, ""
    except urllib.error.HTTPError as e:          # 4xx/5xx: status basta
        return e.code, ""
    except Exception as e:                        # conexión caída
        return 0, type(e).__name__


def httpPost(ruta: str, cuerpo: dict, cabeceras: Optional[dict] = None,
             timeout: float = 30.0) -> tuple[int, dict]:
    data = json.dumps(cuerpo).encode("utf-8")
    cab = {"Content-Type": "application/json"}
    if cabeceras:
        cab.update(cabeceras)
    req = urllib.request.Request(BASE + ruta, data=data, method="POST",
                                 headers=cab)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            crudo = r.read().decode("utf-8", "replace")
            try:
                return r.status, json.loads(crudo) if crudo.strip() else {}
            except json.JSONDecodeError:
                return r.status, {}
    except urllib.error.HTTPError as e:
        crudo = e.read().decode("utf-8", "replace") if e.fp is not None else ""
        try:
            return e.code, json.loads(crudo) if crudo.strip() else {}
        except json.JSONDecodeError:
            # cuerpo no-JSON (HTML del proxy): DEVUELVE el status tal cual —
            # jamás decae a 'éxito' ni a vacío fingido.
            return e.code, {}


def verificarPila() -> None:
    """B0 — jamá falso PASS al arranque (R9.6.2): pila/entorno no disponible
    ⇒ exit 2 ANTES de abrir el navegador."""
    fase("B0 · pre-comprobaciones (sin veredicto)")
    estado, _ = httpGet("/")
    if estado != 200:
        print(f"[guard] GET / == {estado} (esperado 200) — pila no "
              "disponible (R9.6.2)", flush=True)
        sys.exit(2)
    print("[b0] GET / 200 (SPA servida por el proxy)", flush=True)
    estado_health, _ = httpGet("/actuator/health")
    # Anotación literal (ver H-C9-SALUD): el SPA-fallback responde 200 incluso
    # con backend caído — NO es señal de salud backend.
    print(f"[b0] GET /actuator/health = {estado_health} "
          "(SPA-fallback del proxy — señal trivial, anotada)", flush=True)
    estado_me, _ = httpGet(SALUD_BACKEND)
    if estado_me != 401:
        print(f"[guard] GET {SALUD_BACKEND} == {estado_me} (esperado 401: "
              "backend vivo respondiendo por el proxy) — pila no disponible "
              "(R9.6.2)", flush=True)
        sys.exit(2)
    print(f"[b0] GET {SALUD_BACKEND} = 401 (backend vivo — señal "
          "de salud pineda, ver H-C9-SALUD)", flush=True)
    if not RUTA_COMPOSE.exists() or not RUTA_ENV.exists():
        print("[guard] compose.yaml/.env ausentes en el repo — entorno no "
              "disponible (R9.6.2)", flush=True)
        sys.exit(2)


def verificarEntorno() -> str:
    """D-C9-15: playwright importable + binario chromium-headless-shell +
    nombre EXACTO del evento WS contra la 1.64.1 instalada. Jamás instala
    (si falta ⇒ exit 2 con instrucción)."""
    try:
        import playwright  # noqa: F401
        from playwright.sync_api import sync_playwright  # noqa: F401
    except Exception:
        print("[guard] playwright importable NO disponible: instalar por "
              "pip 'python3 -m pip install playwright' y "
              "'playwright install chromium' — el guion jamás instala "
              "solo (D-C9-15)", flush=True)
        sys.exit(2)
    import importlib.metadata as metadatos
    try:
        version = metadatos.version("playwright")
    except Exception:
        version = "desconocida"
    cache_ms = Path.home() / ".cache" / "ms-playwright"
    bins = sorted(p.name for p in cache_ms.glob("chromium*")) if cache_ms.exists() else []
    if not bins:
        print("[guard] binario chromium ausente en ~/.cache/ms-playwright — "
              "instalar con 'playwright install chromium' (jamás instala "
              "solo, D-C9-15)", flush=True)
        sys.exit(2)
    nombre_evento = None
    try:
        from playwright._impl._network import WebSocket as WsImpl
        nombre_evento = str(WsImpl.Events.FrameSent)
    except Exception:
        nombre_evento = None
    if nombre_evento != "framesent":
        print(f"[guard] evento de frames enviados no es 'framesent' en la "
              f"versión instalada ({nombre_evento!r}) — PLAN B D-C9-4 "
              "(conteo de handshakes) sería la vía; abortando en B0 por "
              "entorno inesperado", flush=True)
        sys.exit(2)
    print(f"[b0] playwright {version} importable · binarios ms-playwright: "
          f"{', '.join(bins)} · evento WS pinedo: 'framesent' (D-C9-15)",
          flush=True)
    return version


def detectarComposeBin() -> list[str]:
    """SOLO para la fase destructiva del servicio `backend` (R9.1.5): docker
    compose si existe; si no, podman compose (canon C4–C7 — el binario se
    anota en el qa-log). Jamás down -v, jamás SQL, jamás postgres."""
    if shutil_which("docker"):
        rc = subprocess.run(["docker", "compose", "version"], capture_output=True)
        if rc.returncode == 0:
            return ["docker", "compose"]
    if shutil_which("podman"):
        rc = subprocess.run(["podman", "compose", "version"], capture_output=True)
        if rc.returncode == 0:
            return ["podman", "compose"]
    if shutil_which("podman-compose"):
        return ["podman-compose"]
    print("[guard] binario compose no disponible — no es posible la "
          "fase destructiva acotada (R9.1.5)", flush=True)
    sys.exit(2)


def shutil_which(binario: str) -> Optional[str]:
    from shutil import which
    return which(binario)


# ────────────────────────────────────────────────────────────────────────────
# Sección (e) — evidencia: shots E9n_*.png + shotRedactado + regla dura
# ────────────────────────────────────────────────────────────────────────────

def shot(p: Pareja, page, etiqueta: str, completa: bool = False) -> None:
    """Screenshot a evidencia/ con naming D-C9-7: E9n_<idx>_<etiqueta>.png.
    Setea pista de navegación+observación (la pareja YA navegó — `vista()`
    es quien certifica URL)."""
    p.shots += 1
    nombre = f"{p.id}_{p.shots:02d}_{etiqueta}.png"
    ruta = DIR_EVID / nombre
    page.screenshot(path=str(ruta), full_page=completa)
    print(f"[evidencia] {nombre}", flush=True)


def vista(p: Pareja, page, etiqueta: str, patron: str = "",
          completa: bool = False) -> None:
    """Regla dura D-C9-1 materializada: certifica navegación (URL) +
    screenshot sobre el DOM vivo. Sin esto, `passO` auto-FAIL."""
    url_actual = urlSegura(page.url)
    if patron and patron not in page.url:
        raise AssertionError(
            f"pantalla esperada ({patron}) no coincide con {url_actual}")
    shot(p, page, etiqueta, completa=completa)
    p.pantalla_vista = True
    print(f"[pantalla] {p.id} · {url_actual}", flush=True)


def shotRedactado(p: Pareja, page, localizador, etiqueta: str,
                  patron: str = "") -> None:
    """E9.23 — overlay opaco inyectado JUSTO para el shot (la key jamás
    existe en el fichero, R9.5.2/D-C9-6) y retirado después."""
    caja = localizador.evaluate(
        "el => { const r = el.getBoundingClientRect();"
        " return {x: r.x, y: r.y, w: r.width, h: r.height}; }")
    page.evaluate(
        "([c]) => {"
        " const o = document.createElement('div');"
        " o.id = 'qa-c9-redaccion-s16';"
        " o.style.cssText = 'position:fixed;left:'+c.x+'px;top:'+c.y+'px;"
        "width:'+c.w+'px;height:'+c.h+'px;background:#1c1917;color:#e7e5e4;"
        "font:600 12px sans-serif;display:flex;align-items:center;"
        "justify-content:center;z-index:99999;border-radius:4px;';"
        " o.textContent = 'REDACTADO (S16)';"
        " document.body.appendChild(o);"
        " }", [caja])
    try:
        vista(p, page, etiqueta, patron=patron)
    finally:
        page.evaluate(
            "() => { const o = document.getElementById"
            "('qa-c9-redaccion-s16'); if (o) o.remove(); }")


def vaciarInputsLogin(page, vaciado: bool = True) -> None:
    """S16: shots de login SIEMPRE con campos VACÍOS (R9.5.2)."""
    if vaciado:
        page.fill("#user", "")
        page.fill("#pass", "")


# ────────────────────────────────────────────────────────────────────────────
# Sección (d) — snapshot SEMÁNTICO (D-C9-2: literales+roles+clases; píxeles NO)
# ────────────────────────────────────────────────────────────────────────────

def textoVisible(page, literal: str, exacto: bool = False) -> bool:
    return page.get_by_text(literal, exact=exacto).count() > 0


def esperarQue(condición: Callable[[], bool], page, timeout_s: float = 10.0,
               intervalo_ms: int = 200) -> bool:
    """Polling honesto — el paso de eventos de Playwright ocurre en los
    wait_for_timeout del bucle (contadores/frames se actualizan aquí)."""
    t0 = time.monotonic()
    while time.monotonic() - t0 < timeout_s:
        try:
            if condición():
                return True
        except Exception:
            pass
        page.wait_for_timeout(intervalo_ms)
    return condición()


def claseComputada(page, localizador, propiedad: str = "backgroundColor") -> str:
    return localizador.evaluate(
        "(el, prop) => getComputedStyle(el)[prop]", propiedad)


def filasTabla(page) -> list[str]:
    """Claves de filas (timestamp|mensaje) del DOM vivo — para dedup/delta
    (E9.13/E9.26). Extracción por locators (S16: sin evaluate crudo)."""
    filas = page.locator("table tbody tr")
    n = filas.count()
    claves: list[str] = []
    for i in range(n):
        fila = filas.nth(i)
        celdas = fila.locator("td")
        ts = celdas.nth(1).inner_text() if celdas.count() > 1 else ""
        msg = celdas.nth(5).inner_text() if celdas.count() > 5 else ""
        claves.append(f"{ts}|{msg}")
    return claves


def indicadorEstado(page) -> str:
    """Literal REAL cosechado en vivo (R9.2.11): el span del slot del TopBar."""
    slot = page.locator("#indicador-conexion")
    if slot.count() == 0:
        return ""
    return slot.inner_text().strip()


def bandaEnVivoTexto(page) -> str:
    banda = page.locator("section", has_text="En vivo").first
    if banda.count() == 0:
        return ""
    return banda.inner_text()


# ────────────────────────────────────────────────────────────────────────────
# Sección (c) — observación: contadores de red + WS (frames SOLO type/length)
# ────────────────────────────────────────────────────────────────────────────

class Contadores:
    """TODA la asertación temporal (0/1/2 peticiones, orden) nace AQUÍ —
    de contadores, nunca de 'esperar y mirar' (design §1/(c))."""

    PATRONES = (
        ("logs", "/api/v1/panel/logs"),
        ("projects", "/api/v1/panel/projects"),
        ("me", "/api/v1/panel/me"),
        ("auth", "/api/v1/panel/auth/login"),
        ("ingest", "/api/v1/ingest"),
    )

    def __init__(self) -> None:
        self.requests: list[tuple[str, str]] = []      # (método, URL segura)
        self.responses: list[tuple[str, int]] = []     # (patrón, status)
        self.hora_response: dict[str, float] = {}


    def adjuntar(self, page) -> None:
        page.on("request", self._req)
        page.on("response", self._resp)

    def _req(self, req) -> None:
        self.requests.append((req.method, urlSegura(req.url)))

    def _resp(self, resp) -> None:
        url = resp.url
        for patron, prefijo in self.PATRONES:
            if prefijo in url:
                self.responses.append((patron, resp.status))
                self.hora_response.setdefault(
                    f"{patron}:{resp.status}", time.monotonic())
                return

    def reiniciar(self) -> None:
        self.requests.clear()
        self.responses.clear()

    def reqCon(self, patron: str, contiene: Optional[str] = None,
              desde: int = 0) -> list[tuple[str, str]]:
        prefijos = dict(self.PATRONES)
        if patron not in prefijos:
            raise RuntimeError(f"patrón {patron} no registrado")
        prefijo = prefijos[patron]
        fuera = []
        for metodo, url in self.requests[desde:]:
            if prefijo in url and (contiene is None or contiene in url):
                fuera.append((metodo, url))
        return fuera

    def respCuenta(self, patron: str, status: Optional[int] = None,
                   desde: int = 0) -> int:
        prefijos = dict(self.PATRONES)
        prefijo = prefijos.get(patron, "")
        return sum(
            1 for p, s in self.responses[desde:]
            if p == patron and (status is None or s == status))

    def marcaIndice(self) -> int:
        return len(self.responses)


class WsContador:
    """Frames WS del cliente: contadores por type/longitud — el CONTENIDO
    del frame jamá llega al log (S16). `set_filters` (6 claves con nulls,
    SIN cursor — S8-8) se aserta por parseo INTERNO (keys/booleanos).
    PLAN B D-C9-4: si el evento no resultara observable (nombre pinedo en
    B0), el sub-aserto se degrada a BLOQUEADO-annotado con handshakes."""

    def __init__(self) -> None:
        self.on = 0            # handshakes (page.on websocket)
        self.cerrado = 0       # cierres de socket
        self.set_filters = 0   # frames enviados del tipo set_filters
        self.batch_recibidos = 0
        self.otros_enviados = 0
        self.ultimas_claves: Optional[tuple] = None   # claves de filters
        self.ultimo_con_cursor: bool = False
        self.observado_framesent = False
        # PLAN B
        self.plan_b_usado = False


    def adjuntar(self, page) -> None:
        page.on("websocket", self._ws)

    def _ws(self, ws) -> None:
        self.on += 1
        try:
            ws.on("framesent", self._sent)
            ws.on("framereceived", self._recv)
        except Exception:
            self.plan_b_usado = True
        ws.on("close", self._close)

    def _sent(self, marco) -> None:
        self.observado_framesent = True
        payload = marco if isinstance(marco, str) else str(marco)
        forma = {}
        try:
            forma = json.loads(payload)
        except Exception:
            forma = {}
        tipo = forma.get("type") if isinstance(forma, dict) else None
        if tipo == "set_filters":
            self.set_filters += 1
            filtros = forma.get("filters", {})
            if isinstance(filtros, dict):
                self.ultimas_claves = tuple(sorted(filtros.keys()))
                self.ultimo_con_cursor = "cursor" in forma or "limit" in forma
        else:
            self.otros_enviados += 1
        # S16: SOLO tipo y longitud — jamá contenido.
        print(f"[ws] frame enviado · type={tipo} · len={len(payload)}",
              flush=True)

    def _recv(self, marco) -> None:
        payload = marco if isinstance(marco, str) else str(marco)
        forma = {}
        try:
            forma = json.loads(payload)
        except Exception:
            forma = {}
        tipo = forma.get("type") if isinstance(forma, dict) else None
        if tipo == "batch":
            self.batch_recibidos += 1
        print(f"[ws] frame recibido · type={tipo} · len={len(payload)}",
              flush=True)

    def _close(self) -> None:
        self.cerrado += 1

    def reiniciar(self) -> None:
        self.set_filters = 0
        self.batch_recibidos = 0
        self.otros_enviados = 0


# ────────────────────────────────────────────────────────────────────────────
# Sección (b) — navegación/login: sesión REAL por UI (R9.1.2/R9.2.2-var)
# ────────────────────────────────────────────────────────────────────────────

def loginPorUI(page, usuario: str, password: str,
               destino_patron: str = "**/logs") -> None:
    """Login REAL por UI (inputs + Entrar + wait_for_url) — el token JAMÁS
    se setea a mano (excepción: E9.3-variante/E9.7-B con string BASURA)."""
    page.goto(BASE + "/login", wait_until="domcontentloaded")
    page.fill("#user", usuario)
    page.fill("#pass", password)
    page.locator('button[type="submit"]').click()
    page.wait_for_url(destino_patron, timeout=30000)
    page.wait_for_timeout(600)


def contextoLimpio(browser, storage_basura: bool = False) -> tuple:
    """Contexto NUEVO con storage limpio (E9.2/E9.3-v/E9.4-b). Si
    `storage_basura`, pre-sembrado (incluso tras storage bloqueado) del
    STRING BASURA — jamá un JWT real de nadie."""
    ctx = browser.new_context()
    page = ctx.new_page()
    if storage_basura:
        basura = f"qa-c9-token-basura-{TS}-no-jwt"
        page.goto(BASE + "/login", wait_until="domcontentloaded")
        page.evaluate(
            "([clave, valor]) => { try { window.sessionStorage.setItem"
            "(clave, valor); } catch (e) {} }",
            ["projectlog.panel.token", basura])
    return ctx, page


def contextoStorageBloqueado(browser) -> tuple:
    """E9.7-B: add_init_script que LANZA sobre window.sessionStorage — la
    app degrada el storage a memoria de pestaña (D4 del hook). Si la
    emulación no resulta ⇒ BLOQUEADO honesto (jamá falso PASS)."""
    ctx = browser.new_context()
    ctx.add_init_script(
        "Object.defineProperty(window, 'sessionStorage', {"
        " configurable: true,"
        " get() { throw new Error('qa-c9: sessionStorage bloqueado'); }"
        "});")
    page = ctx.new_page()
    return ctx, page


# ────────────────────────────────────────────────────────────────────────────
# Sección (g) — intercepts: SOLO DATOS simulados (D-C9-3), pila real lo demás
# ────────────────────────────────────────────────────────────────────────────

def interceptar(page, patron_glob: str, respuesta: Optional[dict] = None,
                retardo: float = 0.0, limite: int = 1) -> dict:
    """Envoltura de page.route con uso RESTRICTO (D-C9-3):
      · con `respuesta` (json) → route.fulfill (solo E9.14: DATOS simulados
        — pantalla REAL, nota G1 obligatoria en el veredicto);
      · con `retardo`         → route.continue_() tras sleep (E9.6 y
        E9.12-a: la respuesta REAL llega retrasada — jamá sustituida).
    `limite` aplica el handler solo a las primeras N peticiones; la función
    hace unpage.unroute si el llamador lo pide."""
    estado = {"n": 0, "estados": []}

    def handler(route) -> None:
        estado["n"] += 1
        if retardo > 0:
            time.sleep(retardo)
            estado["estados"].append("continuado-retardado")
            route.continue_()
            return
        if respuesta is not None:
            estado["estados"].append("cumplido-datos-simulados")
            route.fulfill(
                status=200,
                content_type="application/json",
                body=json.dumps(respuesta))
            return
        estado["estados"].append("continuado")
        route.continue_()

    def handlerConLimit(route) -> None:
        if estado["n"] < limite:
            handler(route)
        else:
            route.continue_()

    page.route(patron_glob, handlerConLimit)
    return estado


# ────────────────────────────────────────────────────────────────────────────
# Sección (h) — semillas/ingesta: key SOLO en memoria (R9.5.4)
# ────────────────────────────────────────────────────────────────────────────

KEY_MEMORIA: dict[str, str] = {}     # {"ingesta": <valor> — jamá impresa}
ID_PROYECTO_C9: list[Optional[int]] = [None]


def isoPasadoReciente(minutos: int) -> str:
    """timestamp del payload en el PASADO RECIENTE — cae arriba del listado
    (received_at DESC, id DESC)."""
    momento = datetime.now(timezone.utc) - timedelta(minutes=minutos)
    return momento.strftime("%Y-%m-%dT%H:%M:%S+00:00")


def logSemilla(mensaje: str, nivel: str = "INFO", entorno: str = "production",
               excepcion: Optional[str] = None, metadato: Optional[dict] = None,
               correlacion: Optional[str] = None, minutos: int = 5) -> dict:
    cuerpo: dict = {
        "level": nivel,
        "timestamp": isoPasadoReciente(minutos),
        "message": mensaje,
        "environment": entorno,
        "source": "qa-c9-arnes",
    }
    if excepcion is not None:
        cuerpo["exception"] = excepcion
    if metadato is not None:
        cuerpo["metadata"] = metadato
    if correlacion is not None:
        cuerpo["correlation_id"] = correlacion
    return cuerpo


def ingestar(logs: list[dict], desde_indice: Optional[int] = None) -> tuple[int, int]:
    """POST /api/v1/ingest/batch con X-API-Key (≤200) — 202 {"accepted": N}.
    Solo el CONTEO aceptado se imprime; la key vive SOLO en memoria."""
    if "ingesta" not in KEY_MEMORIA:
        raise RuntimeError("sin key en memoria (B4 debe correr antes)")
    estado, cuerpo = httpPost(
        "/api/v1/ingest/batch", {"logs": logs},
        cabeceras={"X-API-Key": KEY_MEMORIA["ingesta"]})
    aceptados = int(cuerpo.get("accepted", -1)) if isinstance(cuerpo, dict) else -1
    cifra(f"ingesta batch: HTTP {estado} · accepted={aceptados} de "
          f"{len(logs)} (X-API-Key en memoria, jamá impresa)")
    return estado, aceptados


# ────────────────────────────────────────────────────────────────────────────
# Sección (i) — fase destructiva ÚNICA (R9.1.5/D-C9-5)
# ────────────────────────────────────────────────────────────────────────────

COMPOSE_BIN: list[str] = ["podman", "compose"]


def _compose(args: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(
        COMPOSE_BIN + args, cwd=str(RAIZ), capture_output=True, text=True,
        timeout=300)


def pararBackend() -> None:
    rc = _compose(["stop", "backend"])          # SOLO el servicio backend
    print(f"[kill] compose stop backend → rc={rc.returncode} "
          f"({(rc.stderr or rc.stdout or '').strip()[-160:]})", flush=True)
    if rc.returncode != 0:
        print("[kill] stop no confirmó rc=0 — siguiendo con la ventana de "
              "observación", flush=True)


def levantarBackend() -> None:
    rc = _compose(["up", "-d", "backend"])      # restauración en el `finally`
    print(f"[kill] compose up -d backend → rc={rc.returncode} "
          f"({(rc.stderr or rc.stdout or '').strip()[-160:]})", flush=True)


def bucleHealth(segundos: int = 180) -> None:
    t0 = time.monotonic()
    while time.monotonic() - t0 < segundos:
        estado, _ = httpGet(SALUD_BACKEND, timeout=5.0)
        if estado == 401:
            print(f"[health] backend vivo ({SALUD_BACKEND} 401) tras "
                  f"{time.monotonic() - t0:.0f} s — ventana 180 s (ver "
                  "H-C9-SALUD)", flush=True)
            return
        time.sleep(3.0)
    print("\n!!!! PILOTO CAÍDO — restauración del backend fallida "
          f"({segundos} s) !!!!", flush=True)
    n = salirPorVeredictos()
    print("[exit] 2 (R9.1.5 — restauración fallida)", flush=True)
    sys.exit(2)


# ────────────────────────────────────────────────────────────────────────────
# BLOQUE B1 — login + routing/guard/404 (E9.1 … E9.7)
# ────────────────────────────────────────────────────────────────────────────

def b1(browser, usuario: str, password: str, cont: Contadores,
       wsc: WsContador) -> tuple:
    """Sesión principal por UI + routing/guard/404. Devuelve (ctx, page)."""
    ctx = browser.new_context()
    page = ctx.new_page()
    cont.adjuntar(page)
    wsc.adjuntar(page)

    # — E9.7-A preparó la página nueva del mismo contexto más abajo —
    loginPorUI(page, usuario, password)
    print("[run] login principal por UI (inputs + Entrar + **/logs)",
          flush=True)

    # ─── E9.1 (R9.2.1): navegación secuencial a las 9 rutas, SIN 404 de
    # servidor (respuesta de navegación = index.html del fallback SPA) ───
    p = pareja("E9.1")
    try:
        rutas = [
            ("/logs", "pantalla Logs"),
            ("/logs/99999991", "detalle (pantalla hermana)"),
            ("/proyectos", "pantalla Proyectos"),
            ("/proyectos/nuevo", "formulario nuevo"),
            ("/proyectos/99999991", "ficha (o pantalla no-existe)"),
            ("/proyectos/99999991/editar", "formulario editar"),
            ("/noexiste", "404 del panel"),
        ]
        fallidas: list[str] = []
        for ruta, _rotulo in rutas:
            page.goto(BASE + ruta, wait_until="domcontentloaded")
            page.wait_for_timeout(700)
            # TopBar del layout Protegidas presente (incluye la pantalla
            # hermana del log — D-C8-8) y main con contenido (jamá vacío):
            if not textoVisible(page, "Project_Log"):
                fallidas.append(f"{ruta}:sin-TopBar")
            if len(page.locator("main").inner_text().strip()) < 20:
                fallidas.append(f"{ruta}:main-vacío")
            cont.reiniciar()
        # /login y / con sesión ⇒ redirect a /logs SIN pantalla propia:
        page.goto(BASE + "/login", wait_until="domcontentloaded")
        page.wait_for_url("**/logs", timeout=15000)
        page.goto(BASE + "/", wait_until="domcontentloaded")
        page.wait_for_url("**/logs", timeout=15000)
        if not textoVisible(page, "Logs"):
            fallidas.append("/:sin-redirect-Logs")
        # respuesta de navegación = index.html (SPA fallback — jamá 404 de
        # servidor solo para el `*` del cliente):
        page.goto(BASE + "/noexiste", wait_until="domcontentloaded")
        if not (textoVisible(page, "Página no encontrada")
                and "/noexiste" in page.url):
            fallidas.append("noexiste:sin-404-panel")
        # pantallas distintivas cosechadas en vivo:
        page.goto(BASE + "/logs", wait_until="domcontentloaded")
        page.wait_for_selector("table", timeout=20000)
        if not textoVisible(page, "Logs"):
            fallidas.append("/logs:sin-h1")
        page.goto(BASE + "/proyectos", wait_until="domcontentloaded")
        if not textoVisible(page, "Proyectos"):
            fallidas.append("/proyectos:sin-h1")
        page.goto(BASE + "/proyectos/nuevo", wait_until="domcontentloaded")
        if not textoVisible(page, "Crear proyecto"):
            fallidas.append("/proyectos/nuevo:sin-form")
        if fallidas:
            fail(p.id, "rutas con pantallas no conformes: " + ", ".join(fallidas))
        else:
            conNota(p, "9 rutas 1:1, sin 404 de servidor (respuesta "
                       "index.html); / y /login con sesión aterrizan "
                       "en /logs SIN pantalla propia")
            vista(p, page, "01_logs_ruta")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.2 (R9.2.2): guard SIN token → 401 /me → /login con destino ───
    p = pareja("E9.2")
    ctx2 = None
    try:
        ctx2, page2 = contextoLimpio(browser)
        cont2: Contadores = Contadores()
        cont2.adjuntar(page2)
        page2.goto(BASE + "/proyectos/99999999", wait_until="domcontentloaded")
        page2.wait_for_url("**/login", timeout=15000)
        page2.wait_for_timeout(400)
        if cont2.respCuenta("me", 401) < 1:
            fail(p.id, "el guard no consultó GET /panel/me con 401")
        elif not page2.evaluate(
                "() => { try { return window.sessionStorage.getItem"
                "('projectlog.panel.destino') === '/proyectos/99999999'; }"
                " catch (e) { return false; } }"):
            fail(p.id, "destino (pathname) NO recordado por el guard "
                       "(projectlog.panel.destino)")
        else:
            # en el aterrizaje en /login el GUARD ya resolvió sin renderizar
            # contenido protegido (proxy: /me 401 ANTES de la redirección —
            # mismo patrón temporal que la parte A de E9.7):
            if cont2.respCuenta("me", 200) != 0:
                fail(p.id, "hubo GET /panel/me con 200 durante el guard "
                           "sin token (semántica rota)")
            conNota(p, "destino pinedo en vivo: /proyectos/99999999 "
                       "(pathname; search vacío)")
            # login → regreso al destino cumplido:
            page2.fill("#user", usuario)
            page2.fill("#pass", password)
            page2.locator('button[type="submit"]').click()
            page2.wait_for_url("**/proyectos/99999999", timeout=20000)
            page2.wait_for_timeout(600)
            vista(p, page2, "01_retorno_destino")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    finally:
        if ctx2 is not None:
            ctx2.close()
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.3 (R9.2.3): /login con sesión redirige; variante token basura ───
    p = pareja("E9.3")
    ctx3 = None
    try:
        # con sesión (contexto principal): /login ⇒ redirect, formulario AUSENTE
        page.goto(BASE + "/login", wait_until="domcontentloaded")
        page.wait_for_url("**/logs", timeout=15000)
        page.wait_for_timeout(400)
        if textoVisible(page, "Iniciar sesión"):
            fail(p.id, "con sesión vigente el formulario AÚN se muestra")
        else:
            conNota(p, "con sesión: /login redirige a /logs, formulario "
                       "ausente (GET /me 200)")
        # variante: token BASURA en storage (string corrupto, jamá un JWT):
        # la primera llamada API con Bearer-basura devuelve 401 → el
        # interceptor LIMPIA el token y aterriza en /login con formulario.
        ctx3, page3 = contextoLimpio(browser, storage_basura=True)
        cont3: Contadores = Contadores()
        cont3.adjuntar(page3)
        page3.goto(BASE + "/logs", wait_until="domcontentloaded")
        page3.wait_for_url("**/login", timeout=20000)
        page3.wait_for_timeout(600)
        ok_form = textoVisible(page3, "Iniciar sesión")
        ok_descartado = page3.evaluate(
            "() => { try { return window.sessionStorage.getItem"
            "('projectlog.panel.token') === null; } catch (e) { return "
            "false; } }")
        ok_401 = cont3.respCuenta("logs", 401) >= 1
        if not ok_form or not ok_descartado:
            fail(p.id, f"token basura: formulario={ok_form} "
                       f"descartado={ok_descartado}")
        else:
            conNota(p, "variante con token BASURA en storage (string "
                       f"corrupto, jamá un JWT real): 401 observado="
                       f"{ok_401} → token descartado (booleano interno) y "
                       "formulario normal, sin crash/loop")
            vista(p, page3, "02_token_basura_formulario")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    finally:
        if ctx3 is not None:
            ctx3.close()
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.4 (R9.2.4): 404 del PANEL con y sin sesión ───
    p = pareja("E9.4")
    ctx4 = None
    try:
        page.goto(BASE + "/noexiste", wait_until="domcontentloaded")
        page.wait_for_timeout(600)
        hay_404 = (textoVisible(page, "Página no encontrada")
                   and "/noexiste" in page.url
                   and textoVisible(page, "HTTP 404"))
        vacio = len(page.locator("main").inner_text().strip()) < 20
        if not hay_404 or vacio:
            fail(p.id, f"404 del panel no conforme (hay={hay_404} "
                       f"vacío={vacio})")
        else:
            conNota(p, "literal 404 cosechado EN VIVO: '404 · Página no "
                       "encontrada' + 'HTTP 404'; URL estable, jamá vacío")
            vista(p, page, "01_404_panel")
        # sin sesión (contexto nuevo): /login con destino guardado
        ctx4, page4 = contextoLimpio(browser)
        page4.goto(BASE + "/noexiste", wait_until="domcontentloaded")
        page4.wait_for_url("**/login", timeout=15000)
        if not textoVisible(page4, "Iniciar sesión"):
            fail(p.id, "404 sin sesión no aterrizó en /login con destino")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    finally:
        if ctx4 is not None:
            ctx4.close()
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.5 (R9.2.5): 401 inline con credenciales malas ───
    p = pareja("E9.5")
    ctx5 = None
    try:
        ctx5, page5 = contextoLimpio(browser)
        cont5: Contadores = Contadores()
        cont5.adjuntar(page5)
        page5.goto(BASE + "/login", wait_until="domcontentloaded")
        page5.fill("#user", "qa-c9-bad-user")
        page5.fill("#pass", LIT_ROJA)
        indice = cont5.marcaIndice()
        page5.locator('button[type="submit"]').click()
        esperarQue(lambda: cont5.respCuenta("auth", 401, desde=indice) >= 1,
                   page5, timeout_s=10)
        page5.wait_for_timeout(300)
        if not textoVisible(page5, "Usuario o contraseña incorrectos"):
            fail(p.id, "mensaje inline EXACTO ausente bajo el formulario")
        elif "/login" not in page5.url:
            fail(p.id, "hubo redirect tras 401 (única excepción al "
                       "interceptor: inline sin redirect)")
        else:
            conNota(p, "401 del POST real; URL estable /login; vista no "
                       "cerrada; campo culpable no revelado")
            vaciarInputsLogin(page5)
            vista(p, page5, "01_401_inline_limpio")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    finally:
        if ctx5 is not None:
            ctx5.close()
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.6 (R9.2.6): doble submit bloqueado (route delay + contador) ───
    p = pareja("E9.6")
    ctx6 = None
    try:
        ctx6, page6 = contextoLimpio(browser)
        cont6: Contadores = Contadores()
        cont6.adjuntar(page6)
        page6.goto(BASE + "/login", wait_until="domcontentloaded")
        page6.fill("#user", usuario)
        page6.fill("#pass", password)
        indice = cont6.marcaIndice()
        interceptar(page6, "**/panel/auth/login", retardo=0.6)
        boton = page6.locator('button[type="submit"]')
        boton.click()
        page6.wait_for_timeout(30)
        try:
            # 2.º click rápido mientras ENVIANDO (acción de fuerza: sin
            # actionability — el estado `enviando` lo ignora igualmente)
            page6.locator('button[type="submit"]').click(force=True,
                                                          timeout=2000)
        except Exception:
            pass
        ok_enviando = esperarQue(
            lambda: boton.inner_text().strip() == "Enviando…"
            and boton.is_disabled(), page6, timeout_s=2, intervalo_ms=20)
        vaciarInputsLogin(page6)                      # S16: shot sin campos
        vista_ok = ok_enviando
        if vista_ok:
            shot(p, page6, "01_enviando_deshabilitado")
            p.pantalla_vista = True
        page6.wait_for_url("**/logs", timeout=20000)
        n_posts = cont6.respCuenta("auth", 200, desde=indice)
        if not vista_ok:
            fail(p.id, "el doble click no dejó el botón 'Enviando…' "
                       "deshabilitado")
        elif n_posts != 1:
            fail(p.id, f"contador de POST == {n_posts} (esperado 1)")
        else:
            conNota(p, "route delay 600 ms del POST (respuesta REAL "
                       "continuada — D-C9-3); botón 'Enviando…' + "
                       "deshabilitado observados con shot de campos vacíos")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    finally:
        if ctx6 is not None:
            ctx6.close()
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.7 (R9.2.7): restauro de sesión ANTES de renderizar (A) + storage
    # bloqueado (B) — PASS-CON-NOTA: el aserto es proxy temporal ───
    p = pareja("E9.7")
    ctx7b = None
    t_me: list[float] = []
    try:
        # variante A: página NUEVA del MISMO contexto (storage vivo)
        page_nueva = ctx.new_page()
        cont7: Contadores = Contadores()
        cont7.adjuntar(page_nueva)
        page_nueva.on("response", lambda r: t_me.append(time.monotonic())
                      if "/api/v1/panel/me" in r.url else None)
        page_nueva.goto(BASE + "/logs", wait_until="domcontentloaded")
        page_nueva.wait_for_selector("table", timeout=20000)
        t_render = time.monotonic()
        ok_orden = bool(t_me) and t_me[-1] <= t_render
        ok_user = page_nueva.evaluate(
            "([u]) => { const h = document.querySelector('header');"
            " return h !== null && h !== undefined && h.innerText.indexOf(u)"
            " !== -1; }", [usuario])
        sin_form = esperarQue(
            lambda: page_nueva.get_by_text("Iniciar sesión",
                                           exact=False).count() == 0,
            page_nueva, timeout_s=1.5, intervalo_ms=100)
        if not ok_orden:
            fail(p.id, "/panel/me NO llego antes del primer render "
                       "protegido (proxy temporal)")
        elif not ok_user or not sin_form:
            fail(p.id, f"username en barra={ok_user}; formulario jamá "
                       f"visible={sin_form}")
        else:
            conNota(p, "variante A: proxy temporal t(/me) <= t(primer "
                       "render protegido) — SIN captura por-frame de un "
                       "flash (MATIZ declarado en spec §5)")
            shot(p, page_nueva, "01_restauro_logs")
            p.pantalla_vista = True
        page_nueva.close()

        # variante B: storage bloqueado (fallback a memoria del hook)
        ctx7b, page7b = contextoStorageBloqueado(browser)
        cb: Contadores = Contadores()
        cb.adjuntar(page7b)
        page7b.goto(BASE + "/login", wait_until="domcontentloaded")
        page7b.fill("#user", usuario)
        page7b.fill("#pass", password)
        page_estable = True
        try:
            page7b.locator('button[type="submit"]').click()
            page7b.wait_for_url("**/logs", timeout=20000)
            page7b.wait_for_selector("table", timeout=20000)
        except Exception:
            page_estable = False
        if not page_estable:
            raise AssertionError("emulación de storage bloqueado no "
                                 "resultó (la app no quedó funcional)")
        indice_b = cb.marcaIndice()
        page7b.wait_for_timeout(1200)
        logs_con_bearer = cb.respCuenta("logs", 200, desde=indice_b)
        if logs_con_bearer < 1:
            raise AssertionError("panel funcional con storage bloqueado "
                                 "sin GET /panel/logs 200 con Bearer "
                                 "observado")
        conNota(p, "variante B (storage bloqueado por init-script): panel "
                   "funcional — token en memoria de pestaña, /logs servido "
                   "con Bearer (REST 200)")
        shot(p, page7b, "02_storage_bloqueado_funcional")
        p.pantalla_vista = True
    except Exception as e:
        # emulación B fallida — si la variante A ya evidenció, PASS-CON-
        # NOTA con BLOQUEADO honesto de B como nota (R9.6.4); si ni A ni B
        # observaron pantalla → FAIL (jamá falso PASS).
        variante_a_ok = p.pantalla_vista and t_me
        if variante_a_ok:
            conNota(p, "variante B: BLOQUEADO honesto — emulación de "
                       "storage bloqueado no resultó "
                       f"({type(e).__name__}: {str(e)[:100]}) — jamá falso "
                       "PASS (R9.6.4); se cubre con la parte A")
        else:
            fail(p.id, f"excepción del arnés: {type(e).__name__}: "
                       f"{str(e)[:140]}")
    finally:
        if ctx7b is not None:
            ctx7b.close()
    passO()  # veredicto único al cierre del check (idempotente)
    return ctx, page


# ────────────────────────────────────────────────────────────────────────────
# BLOQUE B2 — storage/hibernación (E9.9 booleanos, E9.8 logout al FINAL)
# ────────────────────────────────────────────────────────────────────────────

def b2(ctx, page, cont: Contadores, wsc: WsContador, usuario: str,
       password: str) -> None:
    # ─── E9.9 (R9.2.9): JWT SOLO en sessionStorage — evaluate BOLEANO/keys ──
    p = pareja("E9.9")
    try:
        page.goto(BASE + "/logs", wait_until="domcontentloaded")
        page.wait_for_selector("table", timeout=20000)
        resultado = page.evaluate(
            "() => {"
            "  const s = window.sessionStorage;"
            "  const token = s.getItem('projectlog.panel.token');"
            "  const expira = s.getItem('projectlog.panel.expires_at');"
            "  const lsClaves = Object.keys(window.localStorage);"
            "  const lsSucio = lsClaves.some(k => k.indexOf("
            "'projectlog.panel') !== -1);"
            "  const cookie = document.cookie;"
            "  return {"
            "    token_presente: token !== null,"
            "    token_forma_interna: token !== null &&"
            "      token.split('.').length === 3 && token.length >= 30,"
            "    expira_presente: expira !== null,"
            "    localStorage_limpio: !lsSucio,"
            "    cookie_limpio: cookie.indexOf('projectlog.panel') === -1"
            "      && !/[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}/"
            "    .test(cookie)"
            "  };"
            "}")
        faltan = [k for k, v in resultado.items() if not v]
        if faltan:
            fail(p.id, "inspección booleana con clave en rojo: "
                       + ", ".join(faltan))
        else:
            conNota(p, "evaluate devolvió SOLO booleans/keys: token+"
                       "expires_at en sessionStorage (shape interno "
                       "verificado dentro del navegador), localStorage y "
                       "cookies limpios — evidencia estructurada, sin "
                       "screenshot de valores")
            shot(p, page, "01_logs_pantalla_aserto")   # pantalla real observada
            p.pantalla_vista = True
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.8 (R9.2.8): logout LOCAL sin red — AL FINAL del bloque con
    # re-login inmediato por UI (D-C9-10) ───
    p = pareja("E9.8")
    try:
        indice_req = cont.marcaIndice()
        handshakes_antes = wsc.on
        page.locator("button", has_text="Cerrar sesión").click()
        page.wait_for_url("**/login", timeout=15000)
        page.wait_for_timeout(900)
        limpio = page.evaluate(
            "() => {"
            "  const s = window.sessionStorage, l = window.localStorage;"
            "  return {"
            "    token_fuera: s.getItem('projectlog.panel.token') === null,"
            "    expira_fuera: s.getItem('projectlog.panel.expires_at')"
            "      === null,"
            "    sin_destino: s.getItem('projectlog.panel.destino')"
            "      === null,"
            "    ls_limpio: Object.keys(l).every(k => k.indexOf("
            "'projectlog.panel') !== -1 ? false : true)"
            "  };"
            "}")
        # NINGÚN request de logout (no existe endpoint) — patrón literal:
        n_logout_patron = sum(1 for _m, u in cont.requests[indice_req:]
                              if "logout" in u)
        n_handshakes = wsc.on - handshakes_antes
        faltan = [k for k, v in limpio.items() if not v]
        if faltan or n_logout_patron != 0 or n_handshakes != 0:
            fail(p.id, f"storage={faltan or 'limpio'} · requests-logout="
                       f"{n_logout_patron} · handshakes post-click="
                       f"{n_handshakes}")
        else:
            conNota(p, "logout local: URL→/login, storage limpio "
                       "(booleanos), CERO requests de logout, CERO "
                       "handshake WS post-click; 2.º login por UI a "
                       "código (D-C9-10) para el resto de la corrida")
            vista(p, page, "01_login_final_limpio")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    finally:
        # D-C9-10: re-login inmediato por UI (documentado en qa-log)
        loginPorUI(page, usuario, password)
        print("[run] E9.8/D-C9-10: 2.º login REAL por UI (la sesión "
              "principal quedó destruida por el logout del check)",
              flush=True)
    passO()  # veredicto único al cierre del check (idempotente)
# ────────────────────────────────────────────────────────────────────────────
# BLOQUE B3 — /logs estados sin semilla (E9.10, E9.12, E9.15)
# ────────────────────────────────────────────────────────────────────────────

def b3(browser, page, cont: Contadores, wsc: WsContador) -> None:
    # ─── E9.10 (R9.2.10): cambio de filtro reinicia página + set_filters ───
    p = pareja("E9.10")
    E910_ERR: list[str] = []
    try:
        page.goto(BASE + "/logs", wait_until="domcontentloaded")
        page.wait_for_selector("table", timeout=20000)
        # marca sin recarga: evaluate booleano de marcador global
        page.evaluate("() => { window.__qaC9Marcador = 7; return true; }")
        # cursor avanzado: Cargar más SI está disponible (precondición;
        # si el listado cabe en 50, se anota — asertos esenciales = conteos)
        avanzado = False
        carga = page.locator("button", has_text="Cargar más")
        if carga.count() == 1 and not carga.is_disabled():
            indice_cursor = cont.marcaIndice()
            carga.click()
            esperarQue(
                lambda: cont.respCuenta(
                    "logs", None, desde=indice_cursor) >= 1,
                page, timeout_s=10)
            avanzado = True
        else:
            conNota(p, "cursor no avanzado: sin semilla (B3) el listado "
                       "cabe en la 1.ª página — asertos esenciales de "
                       "conteo/URL intactos")
        ws_antes = wsc.set_filters
        reqs_antes = cont.marcaIndice()
        page.fill("#f-text", NEEDLE_E10)
        page.wait_for_timeout(1200)                # debounce 400 ms del UI
        n_logs_texto = len(cont.reqCon("logs", "text=", desde=reqs_antes))
        delta_sf = wsc.set_filters - ws_antes
        forma_ok = (wsc.ultimas_claves is not None
                    and len(wsc.ultimas_claves) == 6
                    and not wsc.ultimo_con_cursor)
        url_ok = "text=" in page.url and NEEDLE_E10 in page.url
        sin_recarga = page.evaluate(
            "() => window.__qaC9Marcador === 7")
        if not url_ok:
            E910_ERR.append("la URL NO refleja text=<needle>")
        elif n_logs_texto != 1:
            E910_ERR.append(f"peticiones GET /panel/logs con el needle: "
                            f"{n_logs_texto} (esperado 1)")
        elif delta_sf != 1 or not forma_ok:
            E910_ERR.append(f"frames sent set_filters: delta={delta_sf}, "
                            f"forma 6 claves sin cursor={forma_ok}")
        elif not sin_recarga:
            E910_ERR.append("hubo recarga de página (set_filters debe "
                            "reenviarse SIN recargar — page.url estable)")
        else:
            conNota(p, "1 GET /panel/logs con text=needle · 1 set_filters "
                       "(6 claves, nulls, SIN cursor — S8-8) · URL "
                       "rehidratada · sin recarga" + (" · cursor avanzado"
                       if avanzado else ""))
            shot(p, page, "01_filtro_aplicado")
            p.pantalla_vista = True
        # Reiniciar ⇒ query VACÍA + GET /panel/logs SIN parámetros
        ws_antes = wsc.set_filters
        reqs_antes = cont.marcaIndice()
        page.locator("button", has_text="Reiniciar").click()
        page.wait_for_timeout(1200)
        url_limpia = (urllib.parse.urlparse(page.url).query == ""
                      and page.url.rstrip("/").endswith("/logs"))
        sin_param = len(cont.reqCon("logs", "?", desde=reqs_antes)) == 0 \
            and len(cont.reqCon("logs", None, desde=reqs_antes)) >= 1
        sin_recarga = page.evaluate("() => window.__qaC9Marcador === 7")
        if not url_limpia or not sin_param or not sin_recarga:
            E910_ERR.append(f"Reiniciar: url_limpia={url_limpia} "
                            f"GET sin parámetros={sin_param} "
                            f"sin recarga={sin_recarga}")
        else:
            conNota(p, "Reiniciar: query de URL VACÍA + GET /panel/logs "
                       "sin parámetros")
        if E910_ERR:
            fail(p.id, "; ".join(E910_ERR))
        else:
            passO()
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.12 (R9.2.12): estados diferenciados — jamá vacío por error ───
    p = pareja("E9.12")
    E912_ERR: list[str] = []
    try:
        # (a) esqueleto: la 1.ª petición /panel/logs retrasada (pantalla real)
        cont.reiniciar()
        interceptar(page, "**/panel/logs", retardo=1.4, limite=1)
        page.goto(BASE + "/logs", wait_until="domcontentloaded")
        ok_esqueleto = esperarQue(
            lambda: page.locator(".animate-pulse").count() > 0
            and not textoVisible(page, "No hay logs"),
            page, timeout_s=4, intervalo_ms=80)
        if not ok_esqueleto:
            E912_ERR.append("(a) esqueleto no observado con la 1.ª petición "
                            "retrasada")
        else:
            shot(p, page, "01_esqueleto")
            p.pantalla_vista = True
        page.unroute("**/panel/logs")
        page.wait_for_selector("table", timeout=20000)
        # volver a estado sin filtros (tras (b) se restaurará):
        page.locator("button", has_text="Reiniciar").click()
        page.wait_for_timeout(700)
        # (b) vacío por filtro: 2 literales EXACTOS
        page.fill("#f-text", NEEDLE_VACIO)
        page.wait_for_timeout(900)
        hay_vacio = (textoVisible(page, "No hay logs que cumplan los filtros")
                     and textoVisible(
                         page, "Prueba a reiniciar filtros o amplía el "
                               "rango temporal"))
        if not hay_vacio:
            E912_ERR.append("(b) estado vacío por filtro con sus 2 "
                            "literales no observado")
        else:
            conNota(p, "(b) 'No hay logs que cumplan los filtros' + "
                       "'Prueba a reiniciar filtros o amplía el rango "
                       "temporal'")
            page.locator("button", has_text="Reiniciar").click()
            page.wait_for_timeout(700)
        # (c) 400 from>to POR LA URL (D-C8-6: la query es fuente única).
        # from ANTERIOR a las semillas (hoy) mantiene la lista NO vacía
        # (aserto de conservación real); to AÚN más atrás la invierte:
        desde_f = (datetime.now(timezone.utc) - timedelta(days=1)).strftime(
            "%Y-%m-%dT00:00")
        hasta_f = (datetime.now(timezone.utc) - timedelta(days=30)).strftime(
            "%Y-%m-%dT00:00")
        page.fill("#f-from", desde_f)
        page.wait_for_timeout(1000)
        ids_intermedios = filasTabla(page)
        page.fill("#f-to", hasta_f)
        # el 400 llega con el banner rojo; la lista previa DEBE conservarse:
        ok_400 = esperarQue(
            lambda: textoVisible(page, "filter_from_to_inverted"),
            page, timeout_s=8)
        page.wait_for_timeout(400)
        ids_post = filasTabla(page)
        sin_vacio = not textoVisible(page, "No hay logs que cumplan los "
                                            "filtros")
        sin_esqueleto = page.locator(".animate-pulse").count() == 0
        lista_ok = ids_intermedios == ids_post if ids_intermedios else None
        if not ok_400 or not sin_vacio or not sin_esqueleto:
            E912_ERR.append(f"(c) 400 from>to: banner={ok_400} "
                            f"vacíoAUSENTE={sin_vacio} "
                            f"esqueletoAUSENTE={sin_esqueleto}")
        elif lista_ok is False:
            E912_ERR.append("(c) la lista previa NO se conservó (filas "
                            "difieren tras el banner 400)")
        elif lista_ok is None:
            E912_ERR.append("(c) with=desde dejó el listado vacío: no "
                            "aplica lista previa (aserto débil)")
        else:
            conNota(p, "(c) banner 400 'filter_from_to_inverted' + lista "
                       f"previa conservada ({len(ids_post)} filas por "
                       "timestamp|mensaje); vacío/esqueleto AUSENTES")
            vista(p, page, "02_banner_400_from_to")
        page.locator("button", has_text="Reiniciar").click()
        page.wait_for_timeout(700)
        if E912_ERR:
            fail(p.id, "; ".join(E912_ERR))
        else:
            passO()
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.15 (R9.3.1): banner 400 con code y campo culpable ───
    p = pareja("E9.15")
    try:
        # variante (b): filter_invalid_value CON field=level (URL rehidratada)
        page.goto(BASE + "/logs?level=PANICA", wait_until="domcontentloaded")
        ok_b = esperarQue(
            lambda: textoVisible(page, "filter_invalid_value")
            and textoVisible(page, "level"),
            page, timeout_s=8)
        page.wait_for_timeout(300)
        campo_ok = textoVisible(page, "Campo culpable") and textoVisible(
            page, "level")
        if not ok_b or not campo_ok:
            fail(p.id, f"(b) variante level=PANICA: banner={ok_b} "
                       f"campo_maracado={campo_ok}")
        else:
            conNota(p, "variante (b) 'filter_invalid_value' CON field="
                       "level (URL ?level=PANICA rehidratada — campo "
                       "culpable marcado); lista conservada se pinea en "
                       "E9.12-c (el montaje fresco por URL no tiene lista)")
            vista(p, page, "01_banner_400_campo")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    finally:
        page.locator("button", has_text="Reiniciar").click()
        page.wait_for_timeout(700)
    passO()  # veredicto único al cierre del check (idempotente)
# ────────────────────────────────────────────────────────────────────────────
# BLOQUE B4 — proyectos: E9.21 PRIMERO (crea proyecto C9) → 19→20→22→23→14
# ────────────────────────────────────────────────────────────────────────────

def b4(page, cont: Contadores) -> None:
    # ─── E9.21 (R9.3.7): flujo 2 COMPLETO vía UI — 201/409/400-por-campo ───
    p = pareja("E9.21")
    try:
        page.goto(BASE + "/proyectos/nuevo", wait_until="domcontentloaded")
        indice = cont.marcaIndice()
        page.fill("#p-name", NOMBRE_PROYECTO)
        page.fill("#p-desc", f"Proyecto sembrado por el arnés C9 ({TS})")
        with page.expect_response(
                lambda r: r.url.endswith("/api/v1/panel/projects")
                and r.request.method == "POST", timeout=20000) as info:
            page.locator('button[type="submit"]').click()
        respuesta = info.value
        if respuesta.status != 201:
            fail(p.id, f"POST /panel/projects = {respuesta.status} "
                       "(esperado 201)")
        else:
            page.wait_for_url("**/proyectos/*", timeout=15000)
            page.wait_for_timeout(600)
            aviso = textoVisible(
                page,
                "Proyecto creado; genera su API key para que las apps "
                "puedan ingestar")
            if not aviso:
                fail(p.id, "aviso literal del 201 ausente en la ficha")
            else:
                ID_PROYECTO_C9[0] = int(
                    urllib.parse.urlparse(page.url).path.rstrip("/").split("/")[-1])
                conNota(p, f"201 → ficha (id {ID_PROYECTO_C9[0]}) + aviso "
                           "literal exacto; proyecto C9 nacido por UI "
                           "(DOBLE como semilla — R9.5.4/D-C9-9)")
                vista(p, page, "01_aviso_201")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    if ID_PROYECTO_C9[0] is None:
        notaRun("E9.21 FAIL: sin proyecto C9 — E9.19/20/22/23/14 se "
                "degradan a BLOQUEADO por falta de datos")

    # ─── E9.19 (R9.3.5): maqueta 04 — tarjeta del proyecto C9 ───
    p = pareja("E9.19")
    try:
        page.goto(BASE + "/proyectos", wait_until="domcontentloaded")
        page.wait_for_timeout(700)
        tarjeta = page.locator("button", has_text=NOMBRE_PROYECTO).first
        if tarjeta.count() == 0:
            fail(p.id, "tarjeta del proyecto C9 no visible")
        else:
            texto = tarjeta.inner_text()
            cond = ("Activo" in texto and "has_key:" in texto
                    and "Resumen" not in texto)
            if not cond:
                fail(p.id, "tarjeta sin STATUS/has_key o CON resumen "
                           f"(contenido: {texto[:80]}…)")
            else:
                conNota(p, "tarjeta maqueta 04: STATUS 'Activo', name, "
                           "description, api_y_prefijo visible + has_key, "
                           "SIN bloque de resumen")
                vista(p, page, "01_tarjetas_maqueta_04")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.20 (R9.3.6): maqueta 05 — ficha con bloque key + variante 404 ───
    p = pareja("E9.20")
    try:
        page.goto(BASE + f"/proyectos/{ID_PROYECTO_C9[0]}",
                  wait_until="domcontentloaded")
        page.wait_for_timeout(700)
        ok_05 = (textoVisible(page, "API key de ingesta")
                 and textoVisible(page, "has_key")
                 and textoVisible(page, NOMBRE_PROYECTO))
        if not ok_05:
            fail(p.id, "ficha sin bloque 'API key de ingesta'/has_key/nombre")
        else:
            conNota(p, "ficha maqueta 05: bloque 'API key de ingesta' + "
                       "has_key (sin key aún: prefijo '—' hasta el "
                       "regenerate de E9.23)")
            vista(p, page, "01_ficha_maqueta_05")
            # variante 404
            page.goto(BASE + "/proyectos/99999999",
                      wait_until="domcontentloaded")
            page.wait_for_timeout(700)
            if textoVisible(page, "Este proyecto no existe (o fue eliminado)"):
                conNota(p, "variante 404: 'Este proyecto no existe (o "
                           "fue eliminado)' + Volver")
            else:
                fail(p.id, "variante 404 de la ficha no conforme")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.22 (R9.3.8): modales 8a/8c construidos, SIN petición DELETE ───
    p = pareja("E9.22")
    try:
        page.goto(BASE + f"/proyectos/{ID_PROYECTO_C9[0]}",
                  wait_until="domcontentloaded")
        page.wait_for_timeout(600)
        borrados = []
        page.on("request", lambda r: borrados.append(urlSegura(r.url))
                if r.request.method == "DELETE" else None)

        def probar_modal(boton_zona: str, titulo_modal: str,
                         etiqueta_shot: str) -> bool:
            page.locator("button", has_text=boton_zona).first.click()
            dialogo = page.locator("div[role='dialog']")
            esperarQue(lambda: dialogo.count() > 0
                       and textoVisible(page, titulo_modal),
                       page, timeout_s=5)
            if dialogo.count() == 0:
                return False
            confirmar = dialogo.locator(
                "button", has_text=boton_zona).last
            # 1. sin texto: disabled + clase computada (getComputedStyle del
            # botón rojo bg-red-300 → rgb)
            color_base = claseComputada(page, confirmar, "backgroundColor")
            ok_base = confirmar.is_disabled() and "rgb(252, 165, 165)" \
                in color_base
            # 2. texto equivocado: SIGUE disabled
            dialogo.locator("input[type='text']").fill("no-es-este-nombre")
            page.wait_for_timeout(250)
            ok_mal = confirmar.is_disabled()
            # 3. nombre EXACTO: habilitado + bg-red-700
            dialogo.locator("input[type='text']").fill(NOMBRE_PROYECTO)
            page.wait_for_timeout(250)
            color_on = claseComputada(page, confirmar, "backgroundColor")
            ok_on = (not confirmar.is_disabled()
                     and "rgb(185, 28, 28)" in color_on)
            # 4. trim: con espacios delante/detrás SIGUE habilitado (V10)
            dialogo.locator("input[type='text']").fill(
                f"   {NOMBRE_PROYECTO}  ")
            page.wait_for_timeout(250)
            ok_trim = not confirmar.is_disabled()
            dialogo.locator("input[type='text']").fill("")
            page.wait_for_timeout(150)
            if not (ok_base and ok_mal and ok_on and ok_trim):
                return False
            # shot con el botón HABILITADO (nombre exacto en el input — no
            # es credencial: el nombre es literal de nuestro propio needle)
            dialogo.locator("input[type='text']").fill(NOMBRE_PROYECTO)
            page.wait_for_timeout(120)
            shot(p, page, etiqueta_shot)
            p.pantalla_vista = True
            dialogo.locator("button", has_text="Cancelar").click()
            page.wait_for_timeout(250)
            return True
        ok_8a = probar_modal("Eliminar definitivamente",
                             "Eliminar definitivamente el proyecto",
                             "01_modal_8a_habilitado")
        ok_8c = probar_modal("Purgar logs", f"Purgar logs de {NOMBRE_PROYECTO}",
                             "02_modal_8c_habilitado")
        sin_delete = len(borrados) == 0
        if not sin_delete:
            fail(p.id, "se ENvió una petición DELETE desde el arnés "
                       "(PROHIBIDO — los DELETE son C10)")
        elif not (ok_8a and ok_8c):
            fail(p.id, f"modales 8a/8c: 8a={ok_8a} 8c={ok_8c} (habilitación "
                       "por trim o clases computadas en rojo)")
        else:
            conNota(p, "modales 8a/8c: deshabilitado bg-roja-300 → "
                       "habilitado bg-roja-700 SOLO con nombre exacto "
                       "(trim ok); CERO peticiones DELETE (los 3 DELETE "
                       "reales son C10)")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.23 (R9.3.9): regenerate → revelación ÚNICA (key SOLO en memoria
    # + shot REDACTADO) — precede a las ingestas (D-C9-9) ───
    p = pareja("E9.23")
    try:
        page.goto(BASE + f"/proyectos/{ID_PROYECTO_C9[0]}",
                  wait_until="domcontentloaded")
        page.wait_for_timeout(600)
        page.locator("button", has_text="Generar").first.click()
        dialogo_confirm = page.locator("div[role='dialog']")
        esperarQue(lambda: dialogo_confirm.count() > 0, page, timeout_s=5)
        indice = cont.marcaIndice()
        with page.expect_response(
                lambda r: "/api-key/regenerate" in r.url
                and r.request.method == "POST", timeout=20000) as info:
            dialogo_confirm.locator(
                "button", has_text="Confirmar").click()
        respuesta = info.value
        # captura del cuerpo 201 en MEMORIA (jamá impresa; ingestion B5):
        try:
            cuerpo = respuesta.json()
            KEY_MEMORIA["ingesta"] = str(cuerpo.get("key", ""))
        except Exception:
            KEY_MEMORIA["ingesta"] = ""
        ok_201 = respuesta.status == 201
        forma_key = bool(re.match(r"^[A-Za-z0-9_]{4}_[A-Za-z0-9_-]{43}$",
                                  KEY_MEMORIA.get("ingesta", "")))
        dialogo_key = page.locator("div[role='dialog']")
        esperarQue(lambda: textoVisible(
            page, "No se volverá a mostrar; cópiala ahora"),
            page, timeout_s=8)
        aviso = textoVisible(page, "No se volverá a mostrar; cópiala ahora")
        copiar = dialogo_key.locator("button", has_text="Copiar")
        copiar_ok = copiar.count() > 0 and not copiar.first.is_disabled()
        # key matcheada INTERNAMENTE en el navegador (booleano, sin extraer)
        clave_shape_ui = page.evaluate(
            "() => {"
            "  const d = document.querySelector(\"div[role='dialog']\");"
            "  const c = d ? d.querySelector('code') : null;"
            "  if (c === null) return false;"
            "  return /^[A-Za-z0-9_]{4}_[A-Za-z0-9_-]{43}$/.test("
            "c.innerText.trim());"
            "}")
        if not (ok_201 and forma_key and aviso and copiar_ok
                and clave_shape_ui):
            fail(p.id, f"201={ok_201} forma_key={forma_key} "
                       f"aviso={aviso} copiar={copiar_ok} "
                       f"shape_en_dom_boleano={clave_shape_ui}")
        else:
            # shot REDACTADO: overlay opaco sobre el tramo de la key
            codigo = dialogo_key.locator("code").first
            shotRedactado(p, page, codigo, "01_revelacion_redactada")
            # cerrar: solo prefijo corto + has_key true en la ficha
            dialogo_key.locator(
                "button", has_text="He copiado la key; cerrar").click()
            esperarQue(lambda: page.locator(
                "div[role='dialog']").count() == 0, page, timeout_s=8)
            page.wait_for_timeout(900)
            longitudes = page.evaluate(
                "() => {"
                "  const t = document.body.innerText || '';"
                "  const salidas = [];"
                "  const g = t.matchAll(/[A-Za-z0-9_]{4}_[A-Za-z0-9_-]+/g);"
                "  for (const m of g) salidas.push(m[0].length);"
                "  return salidas;"
                "}")
            solo_prefijo = all(l <= 20 for l in longitudes) and \
                len(longitudes) >= 1
            has_key_on = textoVisible(page, "has_key") and textoVisible(
                page, "true")
            if not solo_prefijo or not has_key_on:
                fail(p.id, f"tras cerrar: prefijos cortos={longitudes[:5]} "
                           f"has_key=true visible={has_key_on}")
            else:
                conNota(p, "regenerate 201 → revelación ÚNICA con aviso "
                           "literal + Copiar (G3: presencia/habilitación); "
                           "key matcheada por BOOLEANO interno; shot con "
                           "overlay REDACTADO; tras cerrar SOLO "
                           "prefijo corto + has_key")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.14 (R9.2.14): estado vacío legítimo + CTA — PASS-CON-NOTA G1 ───
    p = pareja("E9.14")
    ruta_proyectos = page.url
    try:
        interceptar(page, "**/panel/projects",
                    respuesta={"items": [], "next_cursor": None})
        page.goto(BASE + "/proyectos", wait_until="domcontentloaded")
        page.wait_for_timeout(600)
        ok_vacio = textoVisible(page, "No hay proyectos todavía")
        if not ok_vacio:
            fail(p.id, "estado vacío legítimo no observado con datos "
                       "simulados")
        else:
            cta = page.locator("a", has_text="Nuevo proyecto").first
            if cta.count() == 0:
                fail(p.id, "CTA 'Nuevo proyecto' ausente en el estado vacío")
            else:
                cta.click()
                page.wait_for_url("**/proyectos/nuevo", timeout=15000)
                ok_cta = textoVisible(page, "Crear proyecto")
                if ok_cta:
                    conNota(
                        p,
                        "PANTALLA REAL, DATOS SIMULADOS (nota G1 "
                        "OBLIGATORIA): route-interception de /panel/"
                        "projects con items:[],next_cursor:null — estado "
                        "'primer uso' inalcanzable con datos vivos (los "
                        "DELETE son C10); la CTA se navega 100% real "
                        "al formulario")
                    vista(p, page, "02_cta_navegada_real")
                else:
                    fail(p.id, "CTA no aterrizó en el formulario real")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    finally:
        page.unroute("**/panel/projects")
        # regresar a la ficha REAL (por si un sub-paso la necesita)
        page.goto(ruta_proyectos if "proyectos/" in ruta_proyectos
                  else BASE + "/proyectos", wait_until="domcontentloaded")
        page.wait_for_timeout(400)
    passO()  # veredicto único al cierre del check (idempotente)
# ────────────────────────────────────────────────────────────────────────────
# BLOQUE B5 — semillas/ingesta con la key ya en memoria (D-C9-9)
# ────────────────────────────────────────────────────────────────────────────

def b5() -> None:
    fase("B5 · semillas/ingesta (sin veredicto propio)")
    # ≥60 logs del needle de E9.13 — batch ÚNICO (≤200):
    lote35 = [
        logSemilla(
            f"{NEEDLE_E35}-n{i:02d}", nivel="INFO", entorno="production",
            minutos=i)
        for i in range(1, 61)
    ]
    estado, aceptados = ingestar(lote35)
    if estado != 202 or aceptados != 60:
        print(f"[guard] semilla e35 fallida: HTTP {estado} accepted="
              f"{aceptados} (los checks de B6 fallarán por falta de datos "
              "— sin veredicto artificial del arnés)", flush=True)

    # ERROR production c/ exception + metadata + correlation (E9.17/E9.25):
    stack_e25 = (
        f"{NEEDLE_E25_STACK}\n"
        "Exception in qa-c9 SettlementRunner: Cannot read field "
        "\"rate\" because \"pendingCharge.rate\" is null\n"
        "\tat qa.c9.SettlementRunner.run(SettlementRunner.java:42)\n"
        f"\tat qa.c9.Bootstrap.main(Bootstrap.java:{TS[-4:]})")
    estado, aceptados = ingestar([
        logSemilla(
            NEEDLE_E25, nivel="ERROR", entorno="production",
            excepcion=stack_e25,
            metadato={"caso": "criterio-2-demo", "arnes": "c9",
                      "needle": NEEDLE_E25_STACK},
            correlacion=NEEDLE_CORR_E25, minutos=2),
    ])
    if estado != 202 or aceptados != 1:
        print(f"[guard] semilla e25 fallida: HTTP {estado} "
              f"accepted={aceptados}", flush=True)

    # log SIN exception ni metadata (bloques ocultos — E9.17-b):
    estado, aceptados = ingestar([
        logSemilla(NEEDLE_E17B, nivel="INFO", entorno="production",
                   minutos=3),
    ])
    if estado != 202 or aceptados != 1:
        print(f"[guard] semilla e17b fallida: HTTP {estado} "
              f"accepted={aceptados}", flush=True)
    cifra("semillas en memoria: e35×60 · e25×1 (ERROR/production + "
          "exception+correlation) · e17b×1 (sin exception)")


# ────────────────────────────────────────────────────────────────────────────
# BLOQUE B6 — /logs y detalle CON datos (E9.13→16→24→11-a→17→18→25) + 11-c
# ────────────────────────────────────────────────────────────────────────────

def b6(page, cont: Contadores, wsc: WsContador) -> None:
    # ─── E9.13 (R9.2.13): Volver conserva filtros y página (acumulado) ───
    p = pareja("E9.13")
    query: str = ""
    try:
        page.goto(BASE + f"/logs?text={NEEDLE_E35}",
                  wait_until="domcontentloaded")
        page.wait_for_selector("table", timeout=20000)
        page.wait_for_timeout(400)
        n50 = len(filasTabla(page))
        carga = page.locator("button", has_text="Cargar más")
        if carga.count() == 1 and carga.is_disabled():
            raise RuntimeError(
                "semilla ≥60 no visible en el acumulado (Cargar más "
                "deshabilitado)")
        carga.click()
        esperarQue(lambda: len(filasTabla(page)) >= 60, page, timeout_s=10)
        ids_antes = filasTabla(page)
        n_antes = len(ids_antes)
        query = urllib.parse.urlparse(page.url).query
        if n_antes < 60:
            raise RuntimeError(f"acumulado {n_antes} filas (esperado ≥60)")
        shot(p, page, "01_acumulado_60")
        p.pantalla_vista = True
        # Detalle de la 1.ª fila (todas las filas matchean el needle):
        page.locator("table tbody tr").first.locator(
            "button", has_text="Detalle").click()
        page.wait_for_url("**/logs/*?*", timeout=15000)
        url_detalle = urlSegura(page.url)
        if query and urllib.parse.urlparse(page.url).query != query:
            raise RuntimeError(
                f"la query del detalle NO está intacta "
                f"({url_detalle[:120]})")
        # Volver → MISMA query + MISMAS filas + MISMA 1.ª fila + SIN refetch
        reqs_antes = cont.marcaIndice()
        page.locator("a", has_text="Volver a logs").first.click()
        page.wait_for_selector("table", timeout=20000)
        page.wait_for_timeout(1400)
        n_refetch = len(cont.reqCon("logs", None, desde=reqs_antes))
        ids_post = filasTabla(page)
        dedup_ok = len(set(ids_post)) == len(ids_post)
        if urllib.parse.urlparse(page.url).query != query:
            fail(p.id, "la query string cambió al volver")
        elif n_refetch != 0:
            fail(p.id, f"HUBO {n_refetch} GET /panel/logs al montar el "
                       "retorno (el store debe saltar el 1.º fetch)")
        elif ids_post != ids_antes or len(ids_post) != n_antes:
            fail(p.id, f"filas tras volver: {len(ids_post)} vs "
                       f"{n_antes} (o 1.ª fila distinta)")
        elif not dedup_ok:
            fail(p.id, "duplicados en el acumulado (dedup roto)")
        else:
            conNota(p, "acumulado keyset post-parche (D-C8-8): query y filas "
                       f"({n_antes} por claves timestamp|mensaje) intactas, "
                       "1.ª fila idéntica, CERO refetch al montar (store "
                       "consume una sola vez), dedup sin duplicados")
            vista(p, page, "02_volver_query_intacta")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # F5 ⇒ acumulado LIMPIO (50 + next_cursor no-null):
    try:
        page.reload(wait_until="domcontentloaded")
        page.wait_for_selector("table", timeout=20000)
        page.wait_for_timeout(500)
        n50 = len(filasTabla(page))
        cursor_ok = textoVisible(page, "cursor disponible")
        # notar el F5 sin re-marcar (la pareja ya está marcada)
        print(f"[nota] E9.13 F5 acumulado limpio: filas={n50} "
              f"cursor_disponible={cursor_ok}", flush=True)
    except Exception as e:
        print(f"[nota] E9.13 F5: excepción {type(e).__name__}: "
              f"{str(e)[:160]}", flush=True)
    # ─── E9.16 (R9.3.2): banda En vivo — lote de 3, orden, SIN refetch ───
    p = pareja("E9.16")
    try:
        # Reiniciar (sin filtros) para que el frame del lote pase el
        # server-side set_filters (el texto del needle filtraría NO-matcheos)
        page.locator("button", has_text="Reiniciar").click()
        esperarQue(lambda: textoVisible(page, "Conectado"), page,
                   timeout_s=15)
        page.wait_for_timeout(800)
        cont.reiniciar()
        wsc.reiniciar()
        reqs_antes = cont.marcaIndice()
        lote16 = [logSemilla(f"{needle('e16')}-n{i:02d}",
                             nivel=("INFO", "WARN", "ERROR")[i - 1],
                             minutos=0, )
                  for i in range(1, 4)]
        estado, aceptados = ingestar(lote16)
        if estado != 202 or aceptados != 3:
            raise RuntimeError(
                f"ingesta del lote de 3 fallida: HTTP {estado}")
        grupo_ok = esperarQue(
            lambda: bandaEnVivoTexto(page).count("En vivo") > 0
            and "· 3 logs ·" in bandaEnVivoTexto(page),
            page, timeout_s=15)
        # 3 filas del lote EN el orden del envío, al comienzo:
        ids_top = filasTabla(page)[:3]
        mensajes_top = [c.split("|", 1)[1] for c in ids_top]
        esperado16 = {f"{needle('e16')}-n01", f"{needle('e16')}-n02",
                      f"{needle('e16')}-n03"}
        orden_ok = set(mensajes_top) == esperado16 and \
            len(set(mensajes_top)) == 3
        batch_ok = "batch_id b_" in bandaEnVivoTexto(page)
        n_refetch = len(cont.reqCon("logs", None, desde=reqs_antes))
        conectado = textoVisible(page, "Conectado")
        if not grupo_ok or not batch_ok:
            fail(p.id, f"banda/grupo del lote: banda 3 logs={grupo_ok} "
                       f"batch_id={batch_ok}")
        elif not orden_ok:
            fail(p.id, f"el grupo llegó distinto: {sorted(set(mensajes_top))[:4]}")
        elif n_refetch != 0:
            fail(p.id, f"REFETCH {n_refetch} de GET /panel/logs en la "
                       "ventana (sin refresco exigido)")
        elif not conectado:
            fail(p.id, "el indicador dejó de estar Conectado durante la "
                       "banda")
        else:
            conNota(p, "banda '● En vivo' con batch_id (b_+uuid del frame) "
                       f"· 3 logs del lote al comienzo (orden observado: "
                       f"{[m[-3:] for m in mensajes_top]}), SIN refetch (0 "
                       "GET /panel/logs) e indicador sigue Conectado")
            vista(p, page, "01_banda_grupo_lote")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.24 (R9.4.1): CRITERIO 1 — 2.ª ventana de lote de 3 ───
    p = pareja("E9.24")
    try:
        banda_antes = bandaEnVivoTexto(page)
        cont.reiniciar()
        reqs_antes = cont.marcaIndice()
        shot(p, page, "01_antes")
        p.pantalla_vista = True
        lote24 = [logSemilla(f"{needle('e24')}-n{i:02d}",
                             nivel=("ERROR", "INFO", "WARN")[i - 1],
                             minutos=0) for i in range(1, 4)]
        estado, aceptados = ingestar(lote24)
        if estado != 202 or aceptados != 3:
            raise RuntimeError(
                f"ingesta del 2.º lote fallida: HTTP {estado}")
        grupo_ok = esperarQue(
            lambda: "· 3 logs ·" in bandaEnVivoTexto(page)
            and bandaEnVivoTexto(page) != banda_antes,
            page, timeout_s=15)
        mensajes_top = [c.split("|", 1)[1] for c in filasTabla(page)[:3]]
        esperado24 = {f"{needle('e24')}-n01", f"{needle('e24')}-n02",
                      f"{needle('e24')}-n03"}
        orden_ok = set(mensajes_top) == esperado24 and \
            len(set(mensajes_top)) == 3
        n_refetch = len(cont.reqCon("logs", None, desde=reqs_antes))
        conectado = textoVisible(page, "Conectado")
        if not grupo_ok:
            fail(p.id, "el grupo del 2.º lote no appeared en la banda "
                       "(banda distinta a la anterior)")
        elif not orden_ok:
            fail(p.id, f"grupo del 2.º lote distinto: {sorted(set(mensajes_top))[:4]}")
        elif n_refetch != 0 or not conectado:
            fail(p.id, f"refetch={n_refetch} conectado={conectado}")
        else:
            conNota(p, "CRITERIO 1 del 06: la app del homelab se "
                       "SUSTITUYE por ingesta REST programática (misma "
                       "superficie POST /ingest/batch, 202 {accepted:3}) — "
                       "grupo en banda En vivo sin refresco e indicador "
                       "Conectado; screenshots antes/después")
            vista(p, page, "02_despues_grupo")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.11 (R9.2.11): indicador SOLO en /logs — parte (a) — se SELLará
    # al final de B7 (partes a/b/c acumuladas en E911_STATE) ───
    p = pareja("E9.11")
    try:
        literal = indicadorEstado(page)
        if "Conectado" not in literal or "Desconectado" in literal:
            E911_STATE["error"] = ("indicador verde no visible en la barra "
                                   "de /logs")
        else:
            span = page.locator("#indicador-conexion").locator(
                "span", has_text="Conectado").first
            color_texto = claseComputada(page, span, "color")
            # verde de conexión (#16a34a familia del dot + verde-700 texto)
            ok_color = "rgb(21, 128, 61)" in color_texto
            if not ok_color:
                try:
                    ok_color = "rgb(22, 163, 74)" in claseComputada(
                        page, span.locator("span").first, "backgroundColor")
                except Exception:
                    ok_color = False
            if not ok_color:
                E911_STATE["error"] = (f"color computado del indicador no "
                                       f"verde-conexión: {color_texto}")
            else:
                E911_STATE["notas"].append(
                    "parte (a): literal REAL cosechado en vivo "
                    f"'{literal}' + color computado verde conexión")
                shot(p, page, "01_conectado")
                E911_STATE["vista"] = True
    except Exception as e:
        E911_STATE["error"] = (f"excepción del arnés (a): "
                               f"{type(e).__name__}: {str(e)[:140]}")
    # (sin passO aquí — ver sellarE911: sello único al final de B7)
    # ─── E9.17 (R9.3.3): bloques detalle CON/SIN exception+metadata ───
    p = pareja("E9.17")
    try:
        page.fill("#f-text", NEEDLE_E25)
        page.wait_for_timeout(1000)
        fila = page.locator("table tbody tr").first
        fila.locator("button", has_text="Detalle").click()
        page.wait_for_selector("table, pre", timeout=20000)
        page.wait_for_timeout(700)
        # literal cosechado EN VIVO (maqueta 'Exception…' vs app real):
        bloques = page.locator("section h2").all_inner_texts()
        bloque_exc = [b for b in bloques
                      if "stacktrace" in b.lower() or "excepc" in b.lower()]
        ok_exc = any(textoVisible(page, b) for b in bloque_exc) \
            and len(bloque_exc) >= 1
        pre_exc = page.locator("section", has_text="stacktrace").locator(
            "pre") if bloque_exc else None
        exc_pre_ok = pre_exc is not None and pre_exc.count() >= 1 and \
            NEEDLE_E25_STACK in pre_exc.first.inner_text()
        ok_meta = textoVisible(page, "Metadata (jsonb)")
        ok_corr = textoVisible(page, "Correlation_id") and textoVisible(
            page, NEEDLE_CORR_E25)
        copiar_hay = page.locator("button", has_text="Copiar").count() >= 2
        if not (ok_exc and exc_pre_ok and ok_meta and ok_corr):
            fail(p.id, f"bloques: excepción={ok_exc} "
                       f"pre-needle={exc_pre_ok} metadata={ok_meta} "
                       f"correlation={ok_corr}")
        else:
            rotulo_bloque = bloque_exc[0] if bloque_exc else "cosechado"
            conNota(p, f"bloques CON datos: '{rotulo_bloque}' (literal "
                       "cosechado en vivo) + "
                       "'Metadata (jsonb)' + <pre> con el needle del "
                       "stacktrace + correlation copiable (Copiar "
                       f"×{copiar_hay} presencia/habilitación — G3)")
            vista(p, page, "01_bloques_con_exception", completa=True)
            # log SIN exception: bloques AUSENTES (ocultos, no vacíos)
            page.goto(BASE + "/logs", wait_until="domcontentloaded")
            page.wait_for_selector("table", timeout=20000)
            page.fill("#f-text", NEEDLE_E17B)
            page.wait_for_timeout(1000)
            page.locator("table tbody tr").first.locator(
                "button", has_text="Detalle").click()
            page.wait_for_timeout(900)
            bloques2 = page.locator("section h2").all_inner_texts()
            sin_exc = all("stacktrace" not in b.lower()
                          and "excepc" not in b.lower() for b in bloques2)
            sin_meta = not textoVisible(page, "Metadata (jsonb)")
            hay_msg = textoVisible(page, NEEDLE_E17B)
            if not (sin_exc and sin_meta and hay_msg):
                fail(p.id, "los bloques APARECEN sin exception/metadata "
                           "(deben estar ocultos)")
            else:
                conNota(p, "log SIN exception/metadata: bloques AUSENTES "
                           "(ocultos) + message presente; cabecera dt "
                           "maqueta 03 (Level/Environment/Proyecto/"
                           "Source/Timestamp/Received_at/Id/Correlation)")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.18 (R9.3.4): 404 del detalle jamá vacío ───
    p = pareja("E9.18")
    try:
        page.goto(BASE + "/logs/99999999", wait_until="domcontentloaded")
        page.wait_for_timeout(800)
        ok = (textoVisible(page, "Este registro no existe (o fue eliminado)")
              and page.locator("a", has_text="Volver a logs").count() >= 1
              and len(page.locator("main").inner_text().strip()) > 20)
        if not ok:
            fail(p.id, "404 del detalle: literal/Volver/vacío no conforme "
                       "(jamá esqueleto infinito)")
        else:
            vista(p, page, "01_404_detalle")
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.25 (R9.4.2): CRITERIO 2 — de filtros a detalle < 120 s ───
    p = pareja("E9.25")
    try:
        page.goto(BASE + "/logs", wait_until="domcontentloaded")
        page.wait_for_selector("table", timeout=20000)
        t0 = time.monotonic()                       # CRÓNÓMETRO
        page.select_option("#f-level", "ERROR")
        page.select_option("#f-env", "production")
        page.wait_for_timeout(300)
        page.fill("#f-text", NEEDLE_E25)
        fila = page.locator("table tbody tr", has_text=NEEDLE_E25).first
        esperarQue(lambda: fila.count() >= 1, page, timeout_s=20)
        fila.locator("button", has_text="Detalle").click()
        pre = page.locator("pre", has_text=NEEDLE_E25_STACK).first
        esperarQue(lambda: pre.count() >= 1
                   and pre.inner_text().count(NEEDLE_E25_STACK) >= 0, page,
                   timeout_s=60)
        t1 = time.monotonic()
        duracion = t1 - t0
        ok_url = re.search(r"/logs/\d+", page.url) is not None
        ok_corr = textoVisible(page, NEEDLE_CORR_E25)
        if not ok_url or not ok_corr:
            fail(p.id, f"detalle: url={ok_url} correlation={ok_corr}")
        elif duracion >= 120:
            fail(p.id, f"cronómetro {duracion:.1f} s (límite 120 s)")
        else:
            conNota(p, "CRITERIO 2: filters→detalle con <pre> del "
                       f"stacktrace needle en {duracion:.1f} s (< 120 s, "
                       "monotonic); contenido EXACTO sembrado visible")
            shot(p, page, "01_criterio2_cronometro", completa=True)
            p.pantalla_vista = True
    except Exception as e:
        fail(p.id, f"excepción del arnés: {type(e).__name__}: {str(e)[:180]}")
    passO()  # veredicto único al cierre del check (idempotente)
    # ─── E9.11 parte (c): fuera de /logs indicador y banner AUSENTES (se
    # acumula en E911_STATE; el sello es al final de B7) ───
    try:
        page.goto(BASE + "/proyectos", wait_until="domcontentloaded")
        page.wait_for_timeout(700)
        ausente = page.evaluate(
            "() => { const s = document.getElementById"
            "('indicador-conexion'); return s !== null && "
            "s.innerText.trim() === ''; }")
        sin_banner = not textoVisible(
            page, "Streaming inactivo: los datos en pantalla no se "
            "actualizan en vivo")
        if ausente and sin_banner:
            E911_STATE["notas"].append(
                "parte (c): en /proyectos el slot #indicador-conexion "
                "queda VACÍO (portal SOLO /logs — S8-4) y banner AUSENTE")
            shot(Pareja("E9.11"), page, "02_proyectos_sin_indicador")
            E911_STATE["vista"] = True
        else:
            E911_STATE["error"] = (E911_STATE["error"]
                                   or f"parte (c): indicador ausente="
                                      f"{ausente} banner ausente={sin_banner}")
    except Exception as e:
        E911_STATE["error"] = (E911_STATE["error"] or
                               f"parte (c): excepción {type(e).__name__}: "
                               f"{str(e)[:140]}")
    # (sin passO aquí: E9.11 se sella al final de B7 — sello único G2)


# ────────────────────────────────────────────────────────────────────────────
# BLOQUE B7 — FASE DESTRUCTIVA ÚNICA (E9.11-b + E9.26 de la MISMA ventana)
# ────────────────────────────────────────────────────────────────────────────

def b7(page, cont: Contadores, wsc: WsContador) -> None:
    """FASE DESTRUCTIVA ÚNICA (siempre última, R9.1.5/D-C9-5): `stop` del
    servicio backend + restauración en `finally` + bucle health 180 s.
    E9.11-b (caída) y E9.26 (restauración) comparten ESTA ventana."""
    fase("B7 · fase destructiva (Última — ventana ÚNICA de kill, D-C9-5)")
    p26 = pareja("E9.26")
    try:
        page.goto(BASE + "/logs", wait_until="domcontentloaded")
        page.wait_for_selector("table", timeout=20000)
        esperarQue(lambda: textoVisible(page, "Conectado"), page,
                   timeout_s=20)
        page.wait_for_timeout(700)
        ids_pre = filasTabla(page)
        setf_pre = wsc.set_filters
        handshakes_pre = wsc.on
        batch_pre = wsc.batch_recibidos
        reqs_pre = cont.marcaIndice()          # índice para el refetch único
        shot(p26, page, "01_pre_caída")
        p26.pantalla_vista = True
        print(f"[cifra] pre-caída: {len(ids_pre)} filas · set_filters "
              f"acumulados {setf_pre} · handshakes {handshakes_pre}",
              flush=True)

        print("[kill] docker/podman compose stop backend — ventana ÚNICA "
              "(R9.1.5/D-C9-5; JAMÁS down -v ni postgres)", flush=True)
        try:
            pararBackend()
            # — E9.11-b de la MISMA ventana: banner + Reconectando (intento
            # N)… creciente + lista pintada persiste —
            ok_banner = esperarQue(
                lambda: textoVisible(
                    page,
                    "Streaming inactivo: los datos en pantalla no se "
                    "actualizan en vivo"),
                page, timeout_s=45)
            intentos: list[int] = []
            t0 = time.monotonic()
            while time.monotonic() - t0 < 26.0:
                literal = indicadorEstado(page)
                m = re.search(r"Reconectando \(intento (\d+)\)", literal)
                if m:
                    n = int(m.group(1))
                    if not intentos or n != intentos[-1]:
                        intentos.append(n)
                page.wait_for_timeout(600)
            n_creciente = len(intentos) >= 2
            ids_durante = filasTabla(page)
            lista_persiste = ids_durante == ids_pre
            shot(p26, page, "02_banner_caída")
            print(f"[cifra] caída: banner={ok_banner} intentos={intentos} "
                  f"lista_persiste={lista_persiste}", flush=True)
            if not ok_banner:
                E911_STATE["error"] = (E911_STATE["error"] or
                                       "banner literal de streaming "
                                       "inactivo no observado tras kill del "
                                       "backend")
            elif not n_creciente:
                E911_STATE["error"] = (E911_STATE["error"] or
                                       f"'Reconectando (intento N)…' con N "
                                       f"creciente no observado (intentos: "
                                       f"{intentos})")
            elif not lista_persiste:
                E911_STATE["error"] = (E911_STATE["error"] or
                                       "la lista no persiste pintada "
                                       "durante la caída (fallback REST)")
            else:
                E911_STATE["notas"].append(
                    "parte (b) de la MISMA ventana de kill (D-C9-5): "
                    "banner 'Streaming inactivo…' + 'Reconectando (intento "
                    f"N)…' creciente (intentos {intentos}) + lista sigue "
                    "pintada por REST-idle tras perder el WS")
                E911_STATE["vista"] = True
                E911_STATE["b_obs"] = True
        finally:
            print("[kill] restauración: compose up -d backend (try/finally "
                  "+ bucle health 180 s)", flush=True)
            levantarBackend()
            bucleHealth(180)

        # — E9.26-post: Conectado + banner retirado + set_filters reenviado +
        # 1 refetch + ids únicos + delta 0 + CERO batch frames en la caída —
        ok_conectado = esperarQue(lambda: textoVisible(page, "Conectado"),
                                  page, timeout_s=90)
        page.wait_for_timeout(1500)   # settle del refetch al reconectar
        banner_fuera = not textoVisible(
            page, "Streaming inactivo: los datos en pantalla no se "
            "actualizan en vivo")
        delta_setf = wsc.set_filters - setf_pre
        delta_hand = wsc.on - handshakes_pre
        delta_batch = wsc.batch_recibidos - batch_pre
        n_refetch = cont.respCuenta("logs", None, desde=reqs_pre)
        ids_post = filasTabla(page)
        unicos = len(set(ids_post)) == len(ids_post)
        delta0 = ids_post == ids_pre
        print(f"[cifra] post: conectado={ok_conectado} "
              f"banner_fuera={banner_fuera} delta_set_filters={delta_setf} "
              f"delta_handshakes={delta_hand} refetch={n_refetch} "
              f"delta_batch={delta_batch} unicos={unicos} "
              f"delta0={delta0}", flush=True)
        if not ok_conectado:
            fail("E9.26", "el indicador NO volvió a Conectado tras "
                          "restaurar (health 180 s ya en verde)")
        elif not banner_fuera:
            fail("E9.26", "el banner de streaming inactivo sigue visible "
                          "tras la restauración")
        elif delta_setf < 1:
            fail("E9.26", "set_filters NO reenviado tras la reconexión "
                          f"(delta {delta_setf})")
        elif n_refetch != 1:
            fail("E9.26", f"refetch de 1.ª página tras reconectar = "
                          f"{n_refetch} (esperado EXACTAMENTE 1 — "
                          "alReconectar solo dispara desde reconectando)")
        elif delta_batch != 0:
            fail("E9.26", "llegó un frame batch durante la caída/"
                          "restauración (sin ingesta posible: delta "
                          f"{delta_batch} — replay o duplicación)")
        elif not unicos or not delta0:
            fail("E9.26", f"ids únicos={unicos} · delta del id-set "
                          f"(pre↔post) NO 0 (replay/duplicados)")
        else:
            conNota(p26, "post-restauración: '● Conectado', banner RETIRADO, "
                       f"set_filters reenviado (delta {delta_setf}), "
                       f"refetch EXACTO de 1 (alReconectar), ids únicos con "
                       "DELTA 0 del id-set vs pre-caída; en la caída CERO "
                       "frames batch (sin ingesta posible: sin replay por "
                       "AUSENCIA) — CRITERIO 3 del 06 (D-C9-5)")
            vista(p26, page, "03_post_restaurado")
    except Exception as e:
        fail("E9.26", f"excepción del arnés: {type(e).__name__}: "
                      f"{str(e)[:180]}")
        levantarBackend()          # restauración de seguridad si la ventana
        bucleHealth(180)           # de arriba murió antes de su finally
    passO()  # veredicto único al cierre del check (idempotente)
    sellarE911()                   # una única línea `E9.11: …` (G2)


# ────────────────────────────────────────────────────────────────────────────
# B8 — cierre: informe resumido + cifras RAW + residuo (R9.5.5)
# ────────────────────────────────────────────────────────────────────────────

def b8(cont: Contadores, wsc: WsContador, version_pw: str) -> int:
    fase("B8 · cierre (informe resumido + cifras RAW + residuo)")
    print(f"[cifra] playwright {version_pw} · chromium headless · BASE "
          f"{BASE} · needles ts={TS}", flush=True)
    for patron, _prefijo in Contadores.PATRONES:
        statuses = [s for p_, s in cont.responses if p_ == patron]
        resumen = ", ".join(f"{statuses.count(st)}×{st}"
                            for st in sorted(set(statuses))) or "0"
        cifra(f"peticiones/response {patron}: {resumen}")
    cifra(f"requests totales observados: {len(cont.requests)}")
    cifra(f"WS: handshakes={wsc.on} · cierres={wsc.cerrado} · "
          f"set_filters enviados={wsc.set_filters} · "
          f"batches recibidos={wsc.batch_recibidos} · "
          f"framesent observable={wsc.observado_framesent} · "
          f"PLAN B aplicado={wsc.plan_b_usado}")
    shots = sorted(x.name for x in DIR_EVID.glob("*.png"))
    print(f"[cifra] evidencia PNG en disco: {len(shots)} "
          "(NO commiteados — D-C9-12; listing + sha256 en qa-report)",
          flush=True)
    for s in shots:
        print(f"   - {s}")
    print("\n== RESIDUO de la siembra (R9.5.5 — NOTIFICADO, NO limpiado; "
          "la limpieza real son los DELETE de C10, jamá SQL desde C9) ==",
          flush=True)
    print(f"   · proyecto sembrado por UI: {NOMBRE_PROYECTO} "
          f"(id {ID_PROYECTO_C9[0]})", flush=True)
    print(f"   · logs sembrados: needles qa-c9-{TS}-* (e35×60, e25, "
          "e16×3, e24×3, e17b...)", flush=True)
    print("   · key de ingesta del proyecto C9: ACTIVA en memoria del "
          "proceso y activa en la BD — nota de higiene local (entorno dev "
          "de una sola VM, citada en el qa-report); jamá impresa",
          flush=True)
    return salirPorVeredictos()


# ────────────────────────────────────────────────────────────────────────────
# main — orden de corrida B0→B8 (design §2)
# ────────────────────────────────────────────────────────────────────────────

def main() -> int:
    print("== arnés C9 qa_vis_c9 — Playwright sync, chromium headless ==",
          flush=True)
    verificarPila()                      # B0 (exit 2 si pila no disponible)
    version_pw = verificarEntorno()      # B0 (exit 2 si entorno incompleto)
    DIR_EVID.mkdir(parents=True, exist_ok=True)
    print(f"[b0] evidencia dir: {DIR_EVID}", flush=True)
    print(f"[b0] env cargado EN PROCESO (claves: ADMIN_INITIAL_USERNAME, "
          "ADMIN_INITIAL_PASSWORD — valores jamá a os.environ/argv/stdout)",
          flush=True)
    usuario = ENV.get("ADMIN_INITIAL_USERNAME", "")
    password = ENV.get("ADMIN_INITIAL_PASSWORD", "")
    if not usuario or not password:
        print("[guard] ADMIN_INITIAL_USERNAME/PASSWORD ausentes en .env "
              "(R9.1.2)", flush=True)
        sys.exit(2)
    global COMPOSE_BIN
    COMPOSE_BIN = detectarComposeBin()
    print(f"[b0] binario destructiva SOLO servicio backend: "
          f"{' '.join(COMPOSE_BIN)}", flush=True)

    from playwright.sync_api import sync_playwright

    cont = Contadores()
    wsc = WsContador()
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        try:
            ctx, page = b1(browser, usuario, password, cont, wsc)   # B1
            b2(ctx, page, cont, wsc, usuario, password)             # B2
            b3(browser, page, cont, wsc)                            # B3
            b4(page, cont)                                          # B4
            if not KEY_MEMORIA.get("ingesta"):
                fase("B5 · sin key en memoria — semillas BLOQUEADAS")
                print("[guard] sin API key del proyecto C9 (E9.23): la "
                      "corrida no puede sembrar; checks de B6 fallarán por "
                      "falta de datos (sin veredictos artificiales)",
                      flush=True)
            else:
                b5()                                                # B5
            b6(page, cont, wsc)                                     # B6
            b7(page, cont, wsc)                                     # B7
            ctx.close()
        except Exception as e:
            print(f"[guard] excepción global del arnés: {type(e).__name__}: "
                  f"{str(e)[:200]}", flush=True)
            sellarE911()          # E9.11 sin sello ⇒ guard de 26 en B8
        finally:
            browser.close()
    return b8(cont, wsc, version_pw)                                    # B8


if __name__ == "__main__":
    ENV = cargarEnvProceso(RUTA_ENV)      # ANTES de todo: en proceso (S16)
    if "--check" in sys.argv:
        # chequeo trivial de sintaxis/carga (la corrida va por el wrapper)
        print("arnés cargado — no corrida (última fase la hace el "
              "wrapper con la pila arriba)")
        sys.exit(0)
    codigo_salida = main()
    print(f"[exit] {codigo_salida}", flush=True)
    sys.exit(codigo_salida)


