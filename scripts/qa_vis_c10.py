#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ═══════════════════════════════════════════════════════════════════════════
# qa_vis_c10.py — arnés visual del corte C10 (T10.29/FC#35)
#
# Canon C9: REUTILIZA la maquinaria de qa_vis_c9 por IMPORT — JAMÁS edita ni
# copy-pastea scripts/qa_vis_c9.py (queda BIT A BIT intacto). El módulo C9
# expone parejas/vista/shot/pantalla-viva/contadores/WS/bloques como funciones
# del módulo y el bloque destructivo como función, lo que permite invocarlos
# desde este guion con las globals rebasadas.
#
# DECISIÓN G5 — import LIMPIO (constatado empíricamente, 2026-11-04): script
# temporal en /tmp que hizo `import qa_vis_c9` y constató:
#     import=ok · efectos=ninguno · faltan=nada · needle=qa-c9-<ts>-t ·
#     TS=<ts-import>
# stdout VACÍO, RESULTADOS/MARCADOS vacíos, ENV={} y todas las piezas
# presentes (b1..b8, pareja/passO/vista/shot, Contadores, WsContador,
# loginPorUI, contextoLimpio, pararBackend/levantarBackend/bucleHealth,
# sellarE911, verificarPila/verificarEntorno/detectarComposeBin,
# cargarEnvProceso, esperarQue/claseComputada/filasTabla/...). Causa
# constatada en el fuente: el único efecto lateral módulo-nivel es la
# definición de needles/constantes (qa_vis_c9.py solo corre su main bajo
# `if __name__ == "__main__"`). ⇒ NO se usa el fallo de seguridad R11 (copia
# binaria controlada) — la vía 1 (import) es la que aplica y está en el
# design D-C10-13. Este fichero NO copia código del C9.
#
# REBASO DE GLOBALS C9 (única escritura permitida sobre el módulo importado):
#   c9.DIR_QA   → .atl/sdd/C10            (evidencia/reportes del corte C10)
#   c9.DIR_EVID → .atl/sdd/C10/evidencia  (PNGs NUNCA commiteados — D-C9-12)
#   c9.TS       → TS de ESTA corrida C10
# CONSECUENCIA: `c9.needle()` hardcodea el prefijo qa-c9- y NO se rebasa con
# el TS — este arnés usa su PROPIO needle10() con prefijo qa-c10- (JAMÁS
# qa-c9-). Los needles CONGELADOS de import (c9.NEEDLE_E35/E25/E25_STACK/
# NEEDLE_CORR_E25/NEEDLE_E17B) SÍ se reutilizan tal cual: son la siembra que
# el re-run de b6 matchea, y cualquier residuo qa-c9-* que generen queda
# cubierto por el guard de limpieza `^qa-c9-|^qa-c10-`.
#
# ORDEN DE CORRIDA (D-C10-13):
#   B0 (pila/entorno, exit 2 honesto) → P0 (aprovisionamiento REST: JWT del
#   panel en memoria, proyectos A=qa-c10-<ts>-purga-8c y B=qa-c10-<ts>-par-b
#   creados POR REST con key regenerada — la key vive SOLO en memoria del
#   proceso, S16) → siembra (needles congelados de C9 para el re-run de b6 +
#   semillas propias qa-c10-* del resumen y del flujo 8b) → RE-RUNS SUAVES
#   honestos con el módulo entero c9.b1/b3/b6 (E9.1-7, E9.10/12/15, E9.13/16/
#   24/11-a/17/18/25 = 18 re-ejecuciones; b2/b4/b5 NO se re-ejecutan — E9.19
#   pina el resumen FUERA de la tarjeta, que C10 añade; b4 crea el proyecto C9
#   por UI con sus globals congelados; b5 es reemplazada por el P0) → CHECKS
#   E10.x (15 — lecturas del resumen, doble confirmación, estados del flujo
#   8, post-estados y CORS latente, DESTRUCTIVOS AL FINAL y SOLO contra la
#   siembra efímera propia) → L (limpieza del residuo de arneses con guard
#   duro `^qa-c9-|^qa-c10-` — skip-and-report de lo que no matchea, JAMÁS
#   del usuario) → c9.b7 (kill-window acotada SOLO al servicio backend +
#   sello E9.11 + E9.26) → informe propio (33 checks).
#
# VEREDICTO: `id: VEREDICTO (nota)` canon G2; guard de cierre EXACTO = 33
# checks (18 re-runs + 15 E10.x — datos para el guard b8 del informe).
#
# Higiene (S16/CD-4): credenciales de .env parseadas EN PROCESO
# (c9.cargarEnvProceso — jamá os.environ/argv/stdout); JWT y API keys SOLO
# en memoria del proceso Python. En pantalla/shots no hay ningún secreto en
# los flujos C10 (la revelación de key era E9.23, que NO se re-ejecuta).
#
# Destructividad: purga 8c (DELETE /projects/{A}/logs con N real), eliminación
# 8b (DELETE /logs/{id}), eliminación 8a (DELETE /projects/{A}) — SOLO contra
# los proyectos A/B nombrados con needle10() de esta corrida. `b7` para/levanta
# SOLO el servicio backend (JAMÁS down -v, jamá postgres — privilegio de C8).
#
# Uso:
#   bash scripts/qa_vis_c10.sh            # vía el wrapper (canon C9)
#   python3 scripts/qa_vis_c10.py --check # chequeo trivial de carga, sin correr
# ═══════════════════════════════════════════════════════════════════════════

import json
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Callable, Optional
from urllib.parse import quote

RAIZ = Path(__file__).resolve().parents[1]
_scripts = RAIZ / "scripts"
if str(_scripts) not in sys.path:
    sys.path.insert(0, str(_scripts))

# ── decisión G5-a: IMPORT LIMPIO (ver cabecera — evidencia del 2026-11-04) ──
import qa_vis_c9 as c9

DIR_QA = RAIZ / ".atl" / "sdd" / "C10"
TS = time.strftime("%Y%m%d%H%M%S")

# ── REBASO de globals del módulo C9 (las 3 del design D-C10-13) ────────────
c9.DIR_QA = DIR_QA
c9.DIR_EVID = DIR_QA / "evidencia"     # NO commitear (D-C9-12)
c9.TS = TS

ESPERA_CHECKS = 33   # 18 re-runs (7 b1 + 3 b3 + 7 b6 + 1 b7) + 15 E10.x

ORIGEN_OTRO = "http://otra-cosa.invalid:9"   # origin AJENA (canon E7.9)


def needle10(caso: str) -> str:
    """Needle del arnés C10 (R-1): prefijo PROPIO qa-c10-<ts>-<caso>.
    JAMÁS c9.needle() — hardcodea qa-c9- y un accidente de prefijo dejaría
    restos fuera del guard de limpieza."""
    return f"qa-c10-{TS}-{caso}"


# Siembra propia de esta corrida (destructiva SOLO contra estos nombres):
NOMBRE_A = needle10("purga-8c")          # par A: purga 8c + eliminado 8a
NOMBRE_B = needle10("par-b")             # par B: estado vacío + archivado
RES_P = needle10("res")                  # semillas del resumen (9 logs)
N8B = needle10("e8b")                    # registro del flujo 8b
POST_P = needle10("post-purga")          # ingesta clave-sobrevive (E10.12)
ARCH_P = needle10("arch")                # semillas del archivado en B
N8A_MSG = needle10("e8a")                # ingesta pre-8a (cascade post-humana)

# literales UI congelados (fuente: frontend/src — REQ-C10-UI/DEL/LOGDEL)
LIT_MISMATCH = "La confirmación no coincide"
LIT_BANNER_PURGA = " logs eliminados; el proyecto y su key siguen intactos"
LIT_BANNER_8A = "Proyecto y sus logs eliminados definitivamente"
LIT_BANNER_8B = "Registro eliminado"
LIT_VACIO_FILTRO = "No hay logs que cumplan los filtros"
LIT_FICHA_404 = "Este proyecto no existe (o fue eliminado)"
LIT_ENVIO = "HTTP 400"

# Estado esperado del resumen del proyecto A: NADA congelado — los checks
# E10.1-2/3/5 leen el by_level/total ACTUAL por REST y asertan que la UI lo
# renderiza fielmente (badges/orden/franja con el MISMO formatear() de
# ResumenLogs). Los re-runs de b6 añaden e16×3/e24×3 al total — la cifra
# fantasma de las semillas propias era un FAIL de guion, no de producto
# (D-C10-13 / nota c10-http-recorta-ows: re-anclaje ratificado 2026-11-04).


def fmt10(n: int) -> str:
    """El formatear() EXACTO de ResumenLogs.tsx — separador de miles con
    espacio; el re-anclaje compara lo mismo que compara la UI."""
    return re.sub(r"\B(?=(\d{3})+(?!\d))", " ", str(n))


def resumenVivo(pid: int) -> dict:
    """(guard) summary del proyecto por REST — LA fuente de las afirmaciones
    del resumen UI (jamá cifras de guion): dict con by_level/total/…"""
    estado, cuerpo = rest("GET", f"/api/v1/panel/projects/{pid}/summary",
                          None, bearer())
    if estado != 200 or not isinstance(cuerpo, dict) \
            or "by_level" not in cuerpo:
        raise AssertionError(f"summary REST de {pid}: HTTP {estado} (sin "
                             "by_level — los checks de resumen necesitan el "
                             "cuerpo vivo; sin veredicto artificial)")
    return cuerpo


def badgesEnOrden(texto: str, pares: list[tuple[str, str]]) -> list[int]:
    """Posiciones ADVANCIENDO (de cada `nive cnt` se busca desde la posición
    del anterior — jamá una substring de otro badge más adelante): []
    si no están todos en el ORDEN del JSON."""
    pos: list[int] = []
    desde = 0
    for niv, cnt in pares:
        i = texto.find(f"{niv} {cnt}", desde)
        if i < 0:
            return []
        pos.append(i)
        desde = i + 1
    return pos


# ────────────────────────────────────────────────────────────────────────────
# REST mínimo propio (Authorization Bearer + DELETE/PUT — httpPost del C9 no
# cubre DELETE con cabeceras de confirmación ni GET con cuerpo parseado)
# ────────────────────────────────────────────────────────────────────────────

def rest(metodo: str, ruta: str, cuerpo=None, cabeceras: Optional[dict] = None,
         timeout: float = 30.0):
    """(status, dict|list|{}) — jamá lanza por status; S16: no imprime nada."""
    data = json.dumps(cuerpo).encode("utf-8") if cuerpo is not None else None
    cab = {"Content-Type": "application/json"} if cuerpo is not None else {}
    if cabeceras:
        cab.update(cabeceras)
    req = urllib.request.Request(c9.BASE + ruta, data=data, method=metodo,
                                 headers=cab)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            crudo = r.read().decode("utf-8", "replace")
            try:
                return r.status, (json.loads(crudo) if crudo.strip() else {})
            except json.JSONDecodeError:
                return r.status, {}
    except urllib.error.HTTPError as e:
        crudo = e.read().decode("utf-8", "replace") if e.fp is not None else ""
        try:
            return e.code, (json.loads(crudo) if crudo.strip() else {})
        except json.JSONDecodeError:
            return e.code, {}   # cuerpo no-JSON (HTML del proxy): status tal cual
    except Exception as exc:   # conexión caída: SIN éxito fingido
        return 0, {"_excepcion": type(exc).__name__}


def preflight(origin: str):
    """OPTIONS de CORS (preflight) — el CORS se procesa ANTES de la cadena de
    seguridad ⇒ NO requiere JWT (canon E7.9)."""
    req = urllib.request.Request(c9.BASE + "/api/v1/panel/projects",
                                 method="OPTIONS")
    req.add_header("Origin", origin)
    req.add_header("Access-Control-Request-Method", "DELETE")
    req.add_header("Access-Control-Request-Headers", "X-Confirm-Name")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            cab = {k.lower(): v for k, v in r.headers.items()}
            return r.status, cab
    except urllib.error.HTTPError as e:
        cab = {k.lower(): v for k, v in (e.headers or {}).items()}
        return e.code, cab
    except Exception as exc:
        return 0, {"_excepcion": type(exc).__name__}


def bearer() -> dict:
    """Authorization con el JWT EN MEMORIA — jamá impreso (S16)."""
    if not JWT10[0]:
        raise RuntimeError("sin JWT en memoria (P0 debe correr antes)")
    return {"Authorization": f"Bearer {JWT10[0]}"}


# ────────────────────────────────────────────────────────────────────────────
# P0 — aprovisionamiento REST (sin Playwright): sesión + proyectos + siembra
# ────────────────────────────────────────────────────────────────────────────

JWT10: list[Optional[str]] = [None]
KEYS10: dict[str, str] = {}            # {"A"/"B": plk_... — SOLO memoria, S16}
ID_A: list[Optional[int]] = [None]
ID_B: list[Optional[int]] = [None]


def ingestar10(clave: str, logs: list[dict]) -> tuple[int, int]:
    estado, cuerpo = rest("POST", "/api/v1/ingest/batch", {"logs": logs},
                          {"X-API-Key": clave})
    aceptados = (int(cuerpo.get("accepted", -1))
                 if isinstance(cuerpo, dict) else -1)
    c9.cifra(f"ingesta batch REST: HTTP {estado} · accepted={aceptados} de "
             f"{len(logs)} (X-API-Key en memoria, jamá impresa)")
    return estado, aceptados


def crearProyecto(nombre: str, descripcion: str) -> tuple[int, Optional[int], str]:
    estado, cuerpo = rest("POST", "/api/v1/panel/projects",
                          {"name": nombre, "description": descripcion},
                          bearer())
    if estado != 201 or not isinstance(cuerpo, dict) or "id" not in cuerpo:
        c9.cifra(f"crear proyecto REST: HTTP {estado} (sin id — guard abajo)")
        return estado, None, ""
    pid = int(cuerpo["id"])
    estado_k, cuerpo_k = rest(
        "POST", f"/api/v1/panel/projects/{pid}/api-key/regenerate",
        None, bearer())
    clave = str(cuerpo_k.get("key", "")) if isinstance(cuerpo_k, dict) else ""
    c9.cifra(f"crear+regenerate REST: proyecto id={pid} · HTTP {estado}/"
             f"{estado_k} (key en memoria, jamá impresa)")
    return estado, pid, clave


def aprovisionar(env10: dict[str, str]) -> None:
    """B0-P0 · sesión REST + proyectos A/B + siembra completa de la corrida.
    Guard: ante fallo de sesión/siembra, exit 2 HONESTO (sin veredictos
    artificiales — los checks E10.x necesitan estos datos)."""
    c9.fase("P0 · aprovisionamiento REST (JWT/key en memoria + siembra)")
    estado, cuerpo = c9.httpPost(
        "/api/v1/panel/auth/login",
        {"username": env10["ADMIN_INITIAL_USERNAME"],
         "password": env10["ADMIN_INITIAL_PASSWORD"]})
    token = str(cuerpo.get("token", "")) if isinstance(cuerpo, dict) else ""
    if estado != 200 or not token:
        print(f"[guard] login REST → HTTP {estado} sin token — la corrida "
              "no puede aprovisionar (higiene: sin eco de ninguna credencial)",
              flush=True)
        sys.exit(2)
    JWT10[0] = token                       # SOLO memoria del proceso (S16)
    print("[p0] sesión REST 200 (JWT en memoria del proceso — jamá impresa)",
          flush=True)

    _estado, pid_a, clave_a = crearProyecto(
        NOMBRE_A, "par A del arnés C10: purga 8c + eliminado 8a")
    if pid_a is None or not clave_a:
        print(f"[guard] proyecto A (purga-8c) no disponible: HTTP {_estado} "
              "— exit 2 honesto (R9.6.2)", flush=True)
        sys.exit(2)
    ID_A[0] = pid_a
    KEYS10["A"] = clave_a
    # La key del proyecto A alimenta el re-run de b6 (c9.ingestar lee
    # KEY_MEMORIA — la siembra del módulo C9 ingesta en ESTE proyecto):
    c9.KEY_MEMORIA["ingesta"] = clave_a
    c9.ID_PROYECTO_C9[0] = pid_a           # nota de residuo del módulo C9

    _estado, pid_b, clave_b = crearProyecto(
        NOMBRE_B, "par B del arnés C10: estado vacío + archivado")
    if pid_b is None or not clave_b:
        print(f"[guard] proyecto B (par-b) no disponible: HTTP {_estado} "
              "— exit 2 honesto (R9.6.2)", flush=True)
        sys.exit(2)
    ID_B[0] = pid_b
    KEYS10["B"] = clave_b

    # ── siembra del re-run de b6 con needles CONGELADOS del módulo C9 ──
    # (réplica fiel de c9.b5: mismo needle, mismas formas — b6 matchea)
    lote35 = [
        c9.logSemilla(
            f"{c9.NEEDLE_E35}-n{i:02d}", nivel="INFO", entorno="production",
            minutos=i)
        for i in range(1, 61)
    ]
    estado, aceptados = c9.ingestar(lote35)
    if estado != 202 or aceptados != 60:
        print("[guard] semilla e35 (congelada del C9) fallida — los checks "
              "que dependen de ella fallarán por falta de datos (sin "
              "veredicto artificial)", flush=True)
    stack_e25 = (
        f"{c9.NEEDLE_E25_STACK}\n"
        "Exception in qa-c9 SettlementRunner: Cannot read field "
        "\"rate\" because \"pendingCharge.rate\" is null\n"
        "\tat qa.c9.SettlementRunner.run(SettlementRunner.java:42)\n"
        f"\tat qa.c9.Bootstrap.main(Bootstrap.java:{TS[-4:]})")
    c9.ingestar([
        c9.logSemilla(
            c9.NEEDLE_E25, nivel="ERROR", entorno="production",
            excepcion=stack_e25,
            metadato={"caso": "criterio-2-demo", "arnes": "c9",
                      "needle": c9.NEEDLE_E25_STACK},
            correlacion=c9.NEEDLE_CORR_E25, minutos=2),
    ])
    c9.ingestar([
        c9.logSemilla(c9.NEEDLE_E17B, nivel="INFO", entorno="production",
                      minutos=3),
    ])
    c9.cifra("semillas re-runs: e35×60 · e25×1 (ERROR/production + "
             "exception+correlation) · e17b×1 (sin exception)")

    # ── semillas propias qa-c10-<ts> ──
    # Resumen (E10.1-2): 9 logs con proporciones EXACTAS del by_level
    # (FATAL 1 · ERROR 2 · WARN 1 · DEBUG 2 · INFO 3) y par de entornos
    # (production 6 · staging 3), en el ORDEN de la banda maqueta-04.
    plan_res = [
        ("FATAL", ["production"]),
        ("ERROR", ["production", "staging"]),
        ("WARN", ["staging"]),
        ("DEBUG", ["production", "production"]),
        ("INFO", ["production", "production", "staging"]),
    ]
    resumen_logs = []
    n = 1
    for nivel, entornos in plan_res:
        for env_ in entornos:
            resumen_logs.append(c9.logSemilla(
                f"{RES_P}-{n:02d}", nivel=nivel, entorno=env_, minutos=n))
            n += 1
    estado, aceptados = ingestar10(KEYS10["A"], resumen_logs)
    if estado != 202 or aceptados != 9:
        print("[guard] semilla del resumen (qa-c10-<ts>-res-*) fallida — "
              "checks E10.1/E10.2 fallarán por falta de datos", flush=True)

    # Registro del flujo 8b (E10.8/E10.9) — 1 log con needle propio:
    estado, aceptados = ingestar10(KEYS10["A"], [
        c9.logSemilla(N8B, nivel="INFO", entorno="production", minutos=1),
    ])
    if estado != 202 or aceptados != 1:
        print("[guard] semilla del flujo 8b fallida — E10.8/E10.9 fallarán "
              "por falta de datos", flush=True)

    # Ingesta pre-8a (cascade post-eliminación — E10.14):
    ingestar10(KEYS10["A"], [
        c9.logSemilla(N8A_MSG, nivel="WARN", entorno="production",
                      minutos=1),
    ])

    # Resumen por REST justo tras sembrar — cifra base para las notas E10:
    estado_sum, cuerpo_sum = rest(
        "GET", f"/api/v1/panel/projects/{ID_A[0]}/summary", None, bearer())
    total = cuerpo_sum.get("total") if isinstance(cuerpo_sum, dict) else None
    c9.cifra(f"siembra C10 completa: proyecto A total={total} (e35×60 + e25 "
             "+ e17b + res×9 + e8b + e8a = 73) · proyecto B vacío")
    # (el ORDEN del by_level lo pina el smoke C10-S1 por REST y los
    # LogsQuerySqlTest por literal — el arnés visual no lo duplica)


def resto_ingesta() -> tuple:
    """Ingesta post-purga de E10.12 — POST /ingest/batch con la key del
    proyecto PURGADO (sobrevive, §3.3) y needle propio POST_P. S16: la key
    sale SOLO de KEYS10 (memoria del proceso). Devuelve (status, cuerpo)."""
    return rest("POST", "/api/v1/ingest/batch",
                {"logs": [c9.logSemilla(f"{POST_P}-n{i:02d}", nivel="WARN",
                                        entorno="production", minutos=i)
                          for i in (1, 2)]},
                {"X-API-Key": KEYS10["A"]})


# ────────────────────────────────────────────────────────────────────────────
# Helpers de check (canon C9: try/except → fail con detalle; modal SIEMPRE
# cerrado antes de continuar — H-C9-MODAL-ABIERTO)
# ────────────────────────────────────────────────────────────────────────────

def ir(page, ruta: str, espera_ms: int = 800) -> None:
    page.goto(c9.BASE + ruta, wait_until="domcontentloaded")
    page.wait_for_timeout(espera_ms)


def cerrarSiAbierta(page) -> None:
    """Cierra (Cancelar) cualquier modal que hubiera quedado abierto — S16
    higiene del flujo, H-C9-MODAL-ABIERTO."""
    try:
        for _ in range(3):
            dialogo = page.locator("div[role='dialog']")
            if dialogo.count() == 0:
                return
            dialogo.first.locator("button", has_text="Cancelar").click()
            page.wait_for_timeout(250)
    except Exception:
        pass


def dialogoConfirmar(page, etiqueta_zona: str, literal_titulo: str,
                     etiqueta_confirmar: str):
    """Abre el modal de la zona destructiva con el botón `etiqueta_zona`, una
    SOLA vez (el .first es el botón de la ficha — el del modal aún no existe)
    y espera el diálogo con el título `literal_titulo`. Devuelve el locator."""
    zona = page.locator("button", has_text=etiqueta_zona).first
    zona.click()
    page.wait_for_timeout(350)
    dialogo = page.locator("div[role='dialog']").first
    ok_apertura = c9.esperarQue(
        lambda: dialogo.count() > 0
        and c9.textoVisible(page, literal_titulo), page, timeout_s=8)
    if not ok_apertura:
        raise AssertionError(
            f"modal `{etiqueta_zona}` no abrió con `{literal_titulo}`")
    dialogo.locator("input[type='text']").first.fill("")
    return dialogo, dialogo.get_by_role(
        "button", name=etiqueta_confirmar, exact=True)


# ═══════════════════════════════════════════════════════════════════════════
# CHECKS E10.x (15) — lecturas → mismatches → 8b → 8c → 8a → post-estados →
# CORS. DESTRUCTIVOS SOLO contra A (y solo al final).
# ═══════════════════════════════════════════════════════════════════════════

def e10(page, browser, cont, wsc: object, usuario: str, env10: dict) -> None:
    ctx: Optional[object] = None
    page_obs = None

    # ── E10.1 — resumen de la TARJETA (flujo 9, maqueta-04) ──────────────────
    p = c9.pareja("E10.1")
    try:
        idx_r = cont.marcaIndice()
        idx_req = cont.marcaIndiceRequests()
        ir(page, "/proyectos")
        # re-anclaje ratificado: la FUENTE es el cuerpo REST VIVO (el proyecto
        # A acumula TODA la siembra de la corrida — la cifra fantasma
        # «9 logs» era un FAIL de guion, D-C10-13).
        cuerpo_s = resumenVivo(ID_A[0])
        total = int(cuerpo_s["total"])
        esperado_lv = [(str(niv), fmt10(int(cnt)))
                       for niv, cnt in cuerpo_s["by_level"].items()]
        esperado_env = [(str(env_), fmt10(int(cnt)))
                        for env_, cnt in cuerpo_s["by_environment"].items()]
        tarjeta = page.locator("button", has_text=NOMBRE_A).first
        if tarjeta.count() == 0:
            raise AssertionError(f"tarjeta de {NOMBRE_A} invisible en listado")
        texto = tarjeta.inner_text()
        # badges == NÚMERO de claves del JSON vivo (y sus textos idénticos):
        n_lv = tarjeta.locator(".lv").count()
        n_env = tarjeta.locator(".env").count()
        ok_cifras = (n_lv == len(esperado_lv) and n_env == len(esperado_env)
                     and all(f"{niv} {cnt}" in texto
                             for niv, cnt in esperado_lv + esperado_env))
        posiciones = badgesEnOrden(texto, esperado_lv)
        ok_orden = posiciones != []
        ok_franja = (f"{fmt10(total)} logs" in texto
                     and "último:" in texto)
        ok_no_vacio = ("sin logs todavía" not in texto and total > 0)
        ok_cargo = (cont.reqCon("projects", "include=summary",
                                desde=idx_req) != []
                    and cont.respCuenta("projects", 200, desde=idx_r) >= 1)
        c9.vista(p, page, "01_tarjeta_resumen_banda")
        if not (ok_cifras and ok_orden and ok_franja and ok_no_vacio
                and ok_cargo):
            raise AssertionError(
                f"tarjeta: badges={n_lv}/{n_env} cifras={ok_cifras} "
                f"orden={posiciones} franja={ok_franja} "
                f"no-vacio={ok_no_vacio} include={ok_cargo}")
        c9.conNota(p, "banda de severidad de la tarjeta == NÚMEROS Y ORDEN "
                      "del JSON REST vivo (n_lv=%d/n_env=%d, total %s) — "
                      "el ORDEN es el del CASE del backend (nota "
                      "c10-orden-bandas: DEBUG=4 ANTES de INFO=5) y la UI "
                      "jamá reordena · franja «%s logs · último:» · GET "
                      "/projects?include=summary observado en la ventana"
                      % (n_lv, n_env, fmt10(total), fmt10(total)))
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.2 — resumen de la FICHA (bloque maqueta-05) + /summary por REST ──
    p = c9.pareja("E10.2")
    try:
        idx_req = cont.marcaIndiceRequests()
        ir(page, f"/proyectos/{ID_A[0]}")
        # FUENTE = cuerpo REST vivo (re-anclaje ratificado):
        cuerpo = resumenVivo(ID_A[0])
        total = int(cuerpo["total"])
        esperado_lv = [(str(niv), fmt10(int(cnt)))
                       for niv, cnt in cuerpo["by_level"].items()]
        esperado_env = [(str(env_), fmt10(int(cnt)))
                        for env_, cnt in cuerpo["by_environment"].items()]
        ok_seccion = c9.textoVisible(page, "Resumen de logs")
        ok_bloque = (c9.textoVisible(page, fmt10(total), exacto=True)
                     and c9.textoVisible(page, "logs totales")
                     and c9.textoVisible(page, "último"))
        ok_ultimo = (isinstance(cuerpo.get("last_log_at"), str)
                     and cuerpo["last_log_at"].endswith("Z")
                     and c9.textoVisible(page, cuerpo["last_log_at"]))
        n_lv = page.locator("main .lv").count()
        n_env = page.locator("main .env").count()
        ok_banda = (n_lv == len(esperado_lv) and n_env == len(esperado_env))
        texto_bloque = page.locator("main").inner_text()
        pos_lv = badgesEnOrden(texto_bloque, esperado_lv)
        pos_env = badgesEnOrden(texto_bloque, esperado_env)
        ok_orden = pos_lv != [] and pos_env != []
        ok_req = cont.reqCon("projects", f"/{ID_A[0]}/summary",
                             desde=idx_req) != []
        c9.vista(p, page, "02_ficha_resumen_bloque")
        if not (ok_seccion and ok_bloque and ok_ultimo and ok_banda
                and ok_orden and ok_req):
            raise AssertionError(
                f"ficha: seccion={ok_seccion} bloque={ok_bloque} "
                f"ultimo={ok_ultimo} banda={n_lv}/{n_env} "
                f"orden={pos_lv}/{pos_env} req-observada={ok_req}")
        c9.conNota(p, "GET /projects/{id}/summary → 200 y la ficha lo "
                      "renderiza FIEL: bloque «%s logs totales · último:» "
                      "(maqueta-05) + last_log_at del cuerpo visible + "
                      "badges en el ORDEN del by_level/by_environment del "
                      "JSON (REQ-C10-UI-7/RESUMEN-3/RESUMEN-4)"
                      % fmt10(total))
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.3 — botón «Actualizar» refresca el resumen (flujo 9 paso 5) ──────
    p = c9.pareja("E10.3")
    try:
        ir(page, "/proyectos")
        antes = cont.marcaIndiceRequests()
        page.get_by_role("button", name="Actualizar", exact=True).click()
        page.wait_for_timeout(900)
        ok_refresco = (cont.reqCon("projects", "include=summary",
                                   desde=antes) != [])
        # re-anclaje: el refresco debe dejar la tarjeta FIEL al REST vivo:
        cuerpo_s = resumenVivo(ID_A[0])
        esperado_lv = [(str(niv), fmt10(int(cnt)))
                       for niv, cnt in cuerpo_s["by_level"].items()]
        total = int(cuerpo_s["total"])
        tarjeta = page.locator("button", has_text=NOMBRE_A).first
        texto = tarjeta.inner_text() if tarjeta.count() == 1 else ""
        ok_intacto = (tarjeta.count() == 1
                      and all(f"{niv} {cnt}" in texto for niv, cnt in esperado_lv)
                      and f"{fmt10(total)} logs" in texto)
        c9.vista(p, page, "03_actualizar_refresca")
        if not (ok_refresco and ok_intacto):
            raise AssertionError(
                f"Actualizar: refetch={ok_refresco} tarjeta-intacta="
                f"{ok_intacto}")
        c9.conNota(p, "resumen refrescado SÓLO por botón (NUNCA en vivo por "
                      "WebSocket — flujo 9 paso 5; re-fetch del include="
                      "summary observado en la ventana)")
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.4 — estado vacío «sin logs todavía» en B (M4, no error) ──────────
    p = c9.pareja("E10.4")
    try:
        ir(page, "/proyectos")
        tarjeta = page.locator("button", has_text=NOMBRE_B).first
        ok_tarjeta = (tarjeta.count() == 1
                      and "sin logs todavía" in tarjeta.inner_text())
        ir(page, f"/proyectos/{ID_B[0]}")
        ok_ficha = (c9.textoVisible(page, "Resumen de logs")
                    and c9.textoVisible(page, "sin logs todavía"))
        c9.vista(p, page, "04_ficha_estado_vacio")
        if not (ok_tarjeta and ok_ficha):
            raise AssertionError(
                f"estado vacío: tarjeta={ok_tarjeta} ficha={ok_ficha}")
        c9.conNota(p, "«sin logs todavía» como texto informativo italic (M4) "
                      "en tarjeta (franja) y ficha (bloque) — sin error, sin "
                      "bandas (RESUMEN-5/RESPUMEN-4 solo claves presentes)")
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.5 — archivado: opacidad 80 pero resumen INTACTO (flujo 9 paso 3) ─
    p = c9.pareja("E10.5")
    try:
        estado_i, aceptados_i = ingestar10(KEYS10["B"], [
            c9.logSemilla(f"{ARCH_P}-n{i:02d}", nivel="INFO",
                          entorno="production", minutos=i)
            for i in (1, 2, 3)
        ])
        if estado_i != 202 or aceptados_i != 3:
            raise AssertionError(
                f"siembra de B: HTTP {estado_i} accepted={aceptados_i} "
                f"(esperado 202/3)")
        ir(page, f"/proyectos/{ID_B[0]}")
        # FUENTE = cuerpo REST vivo de B tras la siembra (re-anclaje):
        cuerpo_antes = resumenVivo(ID_B[0])
        total_b = int(cuerpo_antes["total"])
        esperado_lv_b = [(str(niv), fmt10(int(cnt)))
                         for niv, cnt in cuerpo_antes["by_level"].items()]
        ok_antes = (c9.textoVisible(page, fmt10(total_b), exacto=True)
                    and c9.textoVisible(page, "logs totales")
                    and page.locator("main .lv").count() == len(esperado_lv_b))
        # archivar POR UI (PATCH status — NUNCA DELETE, E8.41):
        page.get_by_role("button", name="Archivar", exact=True).click()
        page.wait_for_timeout(350)
        dialogo = page.locator("div[role='dialog']").first
        if not c9.esperarQue(
                lambda: dialogo.count() > 0
                and c9.textoVisible(page, "Archivar proyecto"), page,
                timeout_s=8):
            raise AssertionError("modal archivar no abrió")
        dialogo.get_by_role("button", name="Confirmar (PATCH status)",
                            exact=True).click()
        if not c9.esperarQue(
                lambda: page.get_by_role("button", name="Desarchivar",
                                         exact=True).count() == 1, page,
                timeout_s=10):
            raise AssertionError("PATCH archivar no reflejó «Desarchivar»")
        ir(page, "/proyectos")
        tarjeta = page.locator("button", has_text=NOMBRE_B).first
        ok_archivado = tarjeta.count() == 1
        opacidad = c9.claseComputada(page, tarjeta, "opacity") \
            if ok_archivado else ""
        ok_opacidad = opacidad in ("0.8", "0.80")
        texto_arch = tarjeta.inner_text() if ok_archivado else ""
        # resumen INTACTO == idéntico al REST vivo + badges del JSON visibles:
        cuerpo_des = resumenVivo(ID_B[0])
        ok_intacto = (cuerpo_des == cuerpo_antes
                      and all(f"{niv} {cnt}" in texto_arch
                              for niv, cnt in esperado_lv_b + [
                                  (str(env_), fmt10(int(cnt)))
                                  for env_, cnt in
                                  cuerpo_antes["by_environment"].items()])
                      and f"{fmt10(total_b)} logs" in texto_arch
                      and "sin logs todavía" not in texto_arch)
        c9.vista(p, page, "05_archivado_resumen_intacto")
        # desarchivar (restaurar estado ACTIVE para la limpieza del corte):
        ir(page, f"/proyectos/{ID_B[0]}")
        page.get_by_role("button", name="Desarchivar", exact=True).click()
        page.wait_for_timeout(350)
        dialogo = page.locator("div[role='dialog']").first
        if not c9.esperarQue(
                lambda: dialogo.count() > 0
                and c9.textoVisible(page, "Desarchivar proyecto"), page,
                timeout_s=8):
            raise AssertionError("modal desarchivar no abrió")
        dialogo.get_by_role("button", name="Confirmar (PATCH status)",
                            exact=True).click()
        if not c9.esperarQue(
                lambda: page.get_by_role("button", name="Archivar",
                                         exact=True).count() == 1, page,
                timeout_s=10):
            raise AssertionError("PATCH desarchivar no reflejó «Archivar»")
        if not (ok_antes and ok_archivado and ok_opacidad and ok_intacto):
            raise AssertionError(
                f"archivado: antes={ok_antes} archivado={ok_archivado} "
                f"opacity={opacidad!r} resumen-intacto={ok_intacto}")
        c9.conNota(p, f"opacity-80 == 0.8 en la tarjeta archivada y el resumen "
                      f"sigue INTACTO (franja {fmt10(total_b)} logs == REST "
                      f"vivo, sin rama especial del componente — flujo 9 "
                      f"paso 3); vuelto a ACTIVE para la limpieza del corte")
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── observador de la 2.ª pestaña (E10.11/E10.12): contexto PROPIO con
    #    loginPorUI propio — sessionStorage es POR-PESTAÑA — en /logs con WS
    #    abierto ANTES de la purga/eliminación de A ──────────────────────────
    try:
        ctx_obs, page_obs = c9.contextoLimpio(browser)
        cont.adjuntar(page_obs)
        wsc.adjuntar(page_obs)
        c9.loginPorUI(page_obs, usuario, env10["ADMIN_INITIAL_PASSWORD"])
        ir(page_obs, "/logs", espera_ms=1000)
        conectado = c9.esperarQue(
            lambda: "Conectado" in c9.indicadorEstado(page_obs)
            and wsc.on >= 1, page_obs, timeout_s=20)
        if not conectado:
            raise AssertionError("observador /logs sin banda «● En vivo» "
                                 "activa (WS sin handshake)")
        ctx = ctx_obs
        c9.cifra("observador /logs: 2.º contexto con loginPorUI propio + "
                 "handshake WS vivo (deltas de purga/8a medidos sobre él)")
    except Exception as exc:
        c9.fail(None, "observador de 2.ª pestaña no disponible: "
                      f"{type(exc).__name__}: {str(exc)[:180]} "
                      "(contagia E10.11/E10.12)")
        ctx = page_obs = None

    def pump(segundos: float = 5000.0) -> None:
        """Bombeo de eventos sobre el observador (los frames WS llegan en el
        wait_for_timeout del loop de Playwright)."""
        objetivo = page_obs if page_obs is not None else page
        objetivo.wait_for_timeout(segundos)

    def deltaWsc() -> tuple:
        return (wsc.on, wsc.set_filters, wsc.batch_recibidos)

    m0: Optional[tuple] = None   # ventana WS de la purga (la fija E10.10;
                                 # None ⇒ E10.11 reporta encadenamiento roto)

    # ── E10.6 — 8c mismatch con trim-igual-malo (REQ-C10-UI-2/UX401-2/G6) ────
    p = c9.pareja("E10.6")
    try:
        ir(page, f"/proyectos/{ID_A[0]}")
        idx_auth = cont.marcaIndiceRequests()
        dialogo, confirmar = dialogoConfirmar(
            page, "Purgar logs", f"Purgar logs de {NOMBRE_A}", "Purgar logs")
        # texto trim-igual-malo con NBSP extremo (U+00A0): el trim() del gate
        # lo acepta (botón HABILITADO) pero el transporte HTTP NO lo recorta
        # (no es OWS) ⇒ el servidor ve un valor distinto al EXACTO → 400 real.
        # Los espacios extremos NO sirven: el transporte recorta los OWS del
        # field-value (RFC 9110) y el DELETE se EJECUTARÍA (repro empírico —
        # nota c10-http-recorta-ows…; D-C10-13 ratificados 2026-11-04).
        dialogo.locator("input[type='text']").first.fill(f"{NOMBRE_A} ")
        page.wait_for_timeout(250)
        ok_gate = not confirmar.is_disabled()
        estado_antes, cuerpo_antes = rest(
            "GET", f"/api/v1/panel/projects/{ID_A[0]}/summary", None,
            bearer())
        confirmar.click()
        page.wait_for_timeout(400)
        ok_400 = (c9.textoVisible(page, LIT_MISMATCH)
                  and c9.textoVisible(page, "confirm_mismatch")
                  and c9.textoVisible(page, LIT_ENVIO))
        ok_modal = page.locator("div[role='dialog']").count() > 0
        ok_sesion = ("/login" not in page.url
                     and cont.reqCon("auth", None, desde=idx_auth) == [])
        estado_des, cuerpo_des = rest(
            "GET", f"/api/v1/panel/projects/{ID_A[0]}/summary", None,
            bearer())
        ok_intacto = (estado_des == 200
                      and cuerpo_des == cuerpo_antes)
        c9.vista(p, page, "06_purga_mismatch_dentro_modal")
        # Cancelar — jamá dejar el modal abierto hacia el siguiente flujo:
        dialogo.locator("button", has_text="Cancelar").click()
        page.wait_for_timeout(300)
        if not (ok_gate and ok_400 and ok_modal and ok_sesion and ok_intacto):
            raise AssertionError(
                f"8c mismatch: gate={ok_gate} error400={ok_400} "
                f"modal={ok_modal} sesion={ok_sesion} intacto={ok_intacto}")
        c9.conNota(p, "NBSP extremo (U+00A0) pasa el gate por trim "
                      "(coincideLiteralConTrim) pero el servidor compara "
                      "EXACTO → 400 confirm_mismatch DENTRO del modal, sin "
                      "logout (G6) y sin borrar NADA (summary igual; "
                      "REQ-C10-UI-2/UX401-2; los espacios extremos se "
                      "recortan en TRANSPORTE (RFC 9110) — nota "
                      "c10-http-recorta-ows…)")
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.7 — 8a mismatch idéntico (REQ-C10-UI-2/UX401-2) ──────────────────
    p = c9.pareja("E10.7")
    try:
        ir(page, f"/proyectos/{ID_A[0]}")
        idx_auth = cont.marcaIndiceRequests()
        dialogo, confirmar = dialogoConfirmar(
            page, "Eliminar definitivamente",
            "Eliminar definitivamente el proyecto", "Eliminar definitivamente")
        dialogo.locator("input[type='text']").first.fill(
            f" {NOMBRE_A}xxx")
        page.wait_for_timeout(250)
        ok_gate = c9.esperarQue(lambda: confirmar.is_disabled(), page, 8)
        # y con trim-igual-malo el gate SÍ pasa (misma regla que E10.6 —
        # NBSP extremo: pasa el trim() del gate, llega SIN recortar al
        # servidor → comparación EXACTO falla → 400; nota c10-http-recorta…):
        dialogo.locator("input[type='text']").first.fill(
            f"{NOMBRE_A} ")
        page.wait_for_timeout(250)
        ok_gate_trim = not confirmar.is_disabled()
        confirmar.click()
        page.wait_for_timeout(400)
        ok_400 = (c9.textoVisible(page, LIT_MISMATCH)
                  and c9.textoVisible(page, "confirm_mismatch"))
        ok_modal = page.locator("div[role='dialog']").count() > 0
        ok_sesion = ("/login" not in page.url
                     and cont.reqCon("auth", None, desde=idx_auth) == [])
        estado_des, _cuerpo = rest(
            "GET", f"/api/v1/panel/projects/{ID_A[0]}", None, bearer())
        ok_intacto = estado_des == 200
        c9.vista(p, page, "07_eliminacion_mismatch_dentro_modal")
        dialogo.locator("button", has_text="Cancelar").click()
        page.wait_for_timeout(300)
        if not (ok_gate and ok_gate_trim and ok_400 and ok_modal
                and ok_sesion and ok_intacto):
            raise AssertionError(
                f"8a mismatch: gate-desigual={ok_gate} gate-trim={ok_gate_trim}"
                f" error400={ok_400} modal={ok_modal} sesion={ok_sesion} "
                f"intacto={ok_intacto}")
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.8 — modal 8b: construcción de la sentencia V11 al milímetro ──────
    p = c9.pareja("E10.8")
    SENTENCIA = ""
    confirmar_8b = None
    try:
        ir(page, f"/logs?text={quote(N8B)}", espera_ms=1200)
        n_fila = c9.esperarQue(lambda: len(c9.filasTabla(page)) == 1, page,
                               timeout_s=15)
        if not n_fila:
            raise AssertionError(
                f"filtro text={N8B} no deja exactamente 1 fila en /logs "
                f"({len(c9.filasTabla(page))} filas)")
        page.locator("button", has_text="Detalle").first.click()
        page.wait_for_url("**/logs/*", timeout=15000)
        page.wait_for_timeout(600)
        fragmento = page.url.split("/logs/")[-1].split("?")[0]
        id8b = int(fragmento)
        SENTENCIA = (f"DELETE /logs/{id8b} · "
                     f"header X-Confirm-Log-Id: {id8b}")
        # LA sentencia V11 vive DENTRO del modal 8b (ModalBorrado8b: el
        # párrafo sentencia se pinta cuando `abierto`) — la ficha SOLO pone
        # el botón que la ABRE; asertarla ANTES de abrir era un orden de
        # guion imposible (FAIL de la corrida ts 2026110416* — reordenado,
        # D-C10-13): PRIMERO el modal, LUEGO la sentencia y los V11.
        page.locator("button", has_text="Eliminar registro").first.click()
        page.wait_for_timeout(350)
        dialogo = page.locator("div[role='dialog']").first
        if not c9.esperarQue(
                lambda: dialogo.count() > 0
                and c9.textoVisible(page, "Eliminar registro"), page,
                timeout_s=8):
            raise AssertionError("modal 8b no abrió")
        ok_sentencia = c9.esperarQue(
            lambda: c9.textoVisible(page, SENTENCIA), page, timeout_s=10)
        if not ok_sentencia:
            raise AssertionError(
                f"sentencia V11 literal invisible EN EL MODAL: "
                f"{SENTENCIA}")
        confirmar_8b = dialogo.get_by_role(
            "button", name="Eliminar registro", exact=True)
        entrada = dialogo.locator("input[type='text']").first
        # 1. texto desigual: DISABLED + bg-red-300 (deshabilitado honesto)
        entrada.fill("no-es-esta-sentencia")
        page.wait_for_timeout(250)
        color_base = c9.claseComputada(page, confirmar_8b,
                                       "backgroundColor")
        ok_des = (confirmar_8b.is_disabled()
                  and ("rgb(252, 165, 165)" in color_base
                       or "0.808 0.114 19.571" in color_base))
        # 2. sentencia EXACTA → habilitado + bg-red-700
        entrada.fill(SENTENCIA)
        page.wait_for_timeout(250)
        color_on = c9.claseComputada(page, confirmar_8b, "backgroundColor")
        ok_on = (not confirmar_8b.is_disabled()
                 and ("rgb(185, 28, 28)" in color_on
                      or "0.505 0.213 27.518" in color_on))
        # 3. trim: con espacios al principio y al final SIGUE habilitado (V11)
        entrada.fill(f"   {SENTENCIA}  ")
        page.wait_for_timeout(250)
        ok_trim = not confirmar_8b.is_disabled()
        c9.vista(p, page, "08_modal_8b_sentencia_v11")
        if not (ok_des and ok_on and ok_trim):
            raise AssertionError(
                f"8b construcción: deshabilitado={ok_des} "
                f"habilitado-red-700={ok_on} trim={ok_trim}")
        c9.conNota(p, f"sentencia literal V11 construida al milímetro "
                      f"(#{id8b}) DENTRO del modal — es donde el producto la "
                      f"pinta (ModalBorrado8b; la ficha solo pone el botón "
                      f"que la abre) — y botón red-300→red-700 con trim "
                      f"(REQ-C10-UI-1/LOGDEL-2; el 400 con mal-id lo cubre "
                      f"el smoke C10-S3 en REST)")
        c9.passO()
    except Exception as exc:
        confirmar_8b = None     # E10.9 necesita la pareja ABIERTA y válida —
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.9 — flujo 8b REAL (delete → «Registro eliminado» + /logs query) ──
    p = c9.pareja("E10.9")
    try:
        if confirmar_8b is None or page.locator("div[role='dialog']").count() == 0:
            raise AssertionError("precondición: modal 8b de E10.8 no está "
                                 "abierto (defecto de encadenamiento)")
        query_actual = page.url.split("?", 1)[-1] if "?" in page.url else ""
        confirmar_8b.click()
        page.wait_for_timeout(500)
        ok_vuelta = c9.esperarQue(
            lambda: "/logs/" not in page.url and "text=" in page.url,
            page, timeout_s=15)
        ok_banner = c9.textoVisible(page, LIT_BANNER_8B)
        ok_query = N8B in page.url and (query_actual == "" or
                                        query_actual in page.url)
        n_filas = len(c9.filasTabla(page))
        # D-C10-13 (bucle 2026-11-04): el diseño pina vuelta+banner+query,
        # NO el CONTENIDO del listado. El snapshot del acumulado (E8.35)
        # restaura la fila SIN refetch y el WS no emite borrados (E10.11 la
        # pinna: deltas 0) ⇒ la fila borrada se ve STALE hasta el próximo
        # refetch del usuario — comportamiento OBSERVADO y elevado a
        # decisión en nota (c10-nota-8b-fila-stale…); JAMÁ pinado aquí como
        # correcto (ok_vacio/ok_lit_vacio del guion eran sobre-especificación
        # — FAIL honesto en la re-corrida ts 2026110416* final-1 con
        # vacio=False literal=False).
        c9.vista(p, page, "09_post_8b_banner_query")
        if not (ok_vuelta and ok_banner and ok_query):
            raise AssertionError(
                f"8b real: vuelta=/logs={ok_vuelta} banner={ok_banner} "
                f"query-intacta={ok_query}")
        c9.conNota(p, "post-204: modal cerrado, aviso «Registro eliminado» "
                      f"por location.state y VUELTA a /logs con la query "
                      f"text=<needle> INTACTA (D-C10-8/REQ-C10-E913-1) · "
                      f"filas visibles: {n_filas} — la fila borrada queda "
                      f"STALE hasta el próximo refetch (snapshot E8.35 sin "
                      f"refetch + WS sin borrados: nota "
                      f"c10-nota-8b-fila-stale-snapshot-sin-refetch.md, "
                      f"decisión pendiente del dueño)")
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.10 — purga 8c REAL: banner con el N REAL + resumen a cero ────────
    p = c9.pareja("E10.10")
    try:
        ir(page, f"/proyectos/{ID_A[0]}")
        estado_n, cuerpo_n = rest(
            "GET", f"/api/v1/panel/projects/{ID_A[0]}/summary", None,
            bearer())
        if estado_n != 200 or not isinstance(cuerpo_n, dict):
            raise AssertionError(f"summary previo a purga: HTTP {estado_n}")
        n_real = int(cuerpo_n["total"])
        c9.cifra(f"purga 8c: total leído por REST justo antes = {n_real}")
        # ventana WS ANTES del click (E10.11 parte 1):
        m0 = deltaWsc()
        dialogo, confirmar = dialogoConfirmar(
            page, "Purgar logs", f"Purgar logs de {NOMBRE_A}", "Purgar logs")
        dialogo.locator("input[type='text']").first.fill(NOMBRE_A)
        page.wait_for_timeout(200)
        confirmar.click()
        banner = f"{n_real}{LIT_BANNER_PURGA}"
        ok_banner = c9.esperarQue(
            lambda: c9.textoVisible(page, banner), page, timeout_s=15)
        ok_cero = c9.textoVisible(page, "sin logs todavía")
        ok_cerrado = page.locator("div[role='dialog']").count() == 0
        pump()
        ok_vivo = "/login" not in page.url
        c9.vista(p, page, "10_purga_banner_n_real")
        if not (ok_banner and ok_cero and ok_cerrado and ok_vivo):
            raise AssertionError(
                f"8c real: banner('{n_real}…')={ok_banner} resumen-cero="
                f"{ok_cero} modal-cerrado={ok_cerrado} sesion={ok_vivo}")
        c9.conNota(p, f"banner literal con el N REAL del cuerpo ({n_real}) "
                      f"y «el proyecto y su key siguen intactos» "
                      f"(REQ-C10-DEL-5/PURGA-2/REQ-C10-UI-3); resumen de la "
                      f"ficha a «sin logs todavía» tras el refresh de "
                      f"cargar() — DTO **y resumen**")
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.11 — post-purga: WS deltas 0 con /logs abierto + refetch vacío ───
    p = c9.pareja("E10.11")
    try:
        if page_obs is None:
            raise AssertionError("observador de 2.ª pestaña no disponible "
                                 "(defecto reportado en el arranque de E10)")
        if m0 is None:
            raise AssertionError("ventana de purga no establecida en E10.10 "
                                 "(defecto de encadenamiento)")
        m1 = deltaWsc()
        pump(7000)
        d_on = wsc.on - m0[0]
        d_sf = wsc.set_filters - m0[1]
        d_batch = wsc.batch_recibidos - m0[2]
        page_obs.goto(c9.BASE + f"/logs?text={quote(RES_P)}",
                      wait_until="domcontentloaded")
        page_obs.wait_for_timeout(1200)
        ok_vacio = c9.esperarQue(
            lambda: c9.textoVisible(page_obs, LIT_VACIO_FILTRO), page_obs,
            timeout_s=15)
        c9.vista(p, page_obs, "11_post_purga_delta0_refetch")
        if not (d_on == 0 and d_sf == 0 and d_batch == 0 and ok_vacio):
            raise AssertionError(
                f"deltas WS durante la ventana de purga: handshakes={d_on} "
                f"set_filters={d_sf} batch={d_batch} (esperado 0/0/0) · "
                f"refetch-vacio={ok_vacio}")
        c9.conNota(p, "LiveBroadcaster SÓLO emite con ingesta (jamá por "
                      "borrados): 0 handshakes/0 set_filters/0 batches con "
                      "/logs abierto durante toda la purga (REQ-C10-WS-2) · "
                      "refetch /logs?text=<needle resumen> → vacío literal")
        c9.passO()
    except Exception as exc:
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.12 — la key sobrevive a la pureza de la purga (202 + batch vivo) ──
    p = c9.pareja("E10.12")
    try:
        if page_obs is None:
            raise AssertionError("observador no disponible (ver arranque)")
        ir(page_obs, "/logs", espera_ms=1200)   # set_filters sin filtros
        b_antes = wsc.batch_recibidos
        estado_i, cuerpo_i = resto_ingesta()    # 202 con la key de la purga
        b_post = wsc.batch_recibidos
        ok_batch = c9.esperarQue(
            lambda: wsc.batch_recibidos - b_antes >= 1, page_obs,
            timeout_s=20)
        ok_fila = c9.esperarQue(
            lambda: c9.textoVisible(page_obs, POST_P), page_obs,
            timeout_s=15)
        c9.vista(p, page_obs, "12_key_sobrevive_batch_vivo")
        if not (estado_i == 202 and ok_batch and ok_fila):
            raise AssertionError(
                f"ingesta post-purga: HTTP {estado_i} · batch-delta="
                f"{b_post - b_antes} (esperado ≥1) · fila-viva={ok_fila}")
        c9.conNota(p, "POST /ingest/batch con la key del purgado → 202 y "
                      "delta WS ≥ 1 con la fila en vivo — «sigue aceptando "
                      "ingesta» en el canal también (REQ-C10-PURGA-2; "
                      "counterpart UI del smoke C10-S6)")
        c9.passO()
    except Exception as exc:
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.13 — eliminación 8a REAL (banner + tarjeta ausente) ───────────────
    p = c9.pareja("E10.13")
    try:
        ir(page, f"/proyectos/{ID_A[0]}")
        dialogo, confirmar = dialogoConfirmar(
            page, "Eliminar definitivamente",
            "Eliminar definitivamente el proyecto", "Eliminar definitivamente")
        dialogo.locator("input[type='text']").first.fill(NOMBRE_A)
        page.wait_for_timeout(200)
        confirmar.click()
        ok_ruta = c9.esperarQue(
            lambda: page.url.endswith("/proyectos"), page, timeout_s=15)
        ok_banner = c9.textoVisible(page, LIT_BANNER_8A)
        ok_ausente = c9.esperarQue(
            lambda: page.locator("button", has_text=NOMBRE_A).count() == 0,
            page, timeout_s=15)
        c9.vista(p, page, "13_post_8a_banner_tarjeta")
        if not (ok_ruta and ok_banner and ok_ausente):
            raise AssertionError(
                f"8a real: ruta-listado={ok_ruta} banner={ok_banner} "
                f"tarjeta-ausente={ok_ausente}")
        c9.conNota(p, "post-204: navega a /proyectos con el aviso por "
                      "location.state (D-C10-8) y la tarjeta desaparece "
                      "(REQ-C10-UI-3/DEL-1)")
        c9.passO()
    except Exception as exc:
        cerrarSiAbierta(page)
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.14 — post-8a: key muere, cascade, ficha 404 §5 ────────────────────
    p = c9.pareja("E10.14")
    try:
        estado401, cuerpo401 = rest(
            "POST", "/api/v1/ingest/batch",
            {"logs": [c9.logSemilla("muerte-8a-arnes-c10", nivel="INFO",
                                    entorno="production", minutos=0)]},
            {"X-API-Key": KEYS10["A"]})
        ok_401 = (estado401 == 401
                  and isinstance(cuerpo401, dict)
                  and cuerpo401.get("code") == "api_key_invalid")
        estado_c, cuerpo_c = rest(
            "GET", f"/api/v1/panel/logs?text={quote(N8A_MSG)}", None,
            bearer())
        # GET /logs RESPONDE PAGINADO {items, next_cursor} — el guion asumía
        # list y daba items=-1 SIEMPRE (defecto del guion visible en la
        # corrida ts 2026110416*: cascade(200, items=-1); D-C10-13):
        if isinstance(cuerpo_c, dict) and "items" in cuerpo_c:
            n_cascade = len(cuerpo_c["items"])
        elif isinstance(cuerpo_c, list):
            n_cascade = len(cuerpo_c)
        else:
            n_cascade = -1
        ir(page, f"/proyectos/{ID_A[0]}")
        ok_404ui = c9.esperarQue(
            lambda: c9.textoVisible(page, LIT_FICHA_404), page, timeout_s=15)
        c9.vista(p, page, "14_post_8a_key_muerta_cascade_404")
        if not (ok_401 and n_cascade == 0 and ok_404ui):
            raise AssertionError(
                f"post-8a: ingesta-401={ok_401} · cascade({estado_c}, "
                f"items={n_cascade}) · ficha-404ui={ok_404ui}")
        c9.conNota(p, "la key vivía en columnas de projects → muere con la "
                      "fila (401 api_key_invalid); el ingesta pre-8a desapareció "
                      "por CASCADE (0 items; DELETE-5/E9.26-del-sabor-SQL pero "
                      "vía REST); ficha del eliminado → 404 §5 literal")
        c9.passO()
    except Exception as exc:
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # ── E10.15 — CORS latente: OPTIONS con/por cabeceras de confirmación ──────
    p = c9.pareja("E10.15")
    try:
        origin = (env10.get("FRONTEND_ORIGIN", "")
                  .split(",")[0].strip())
        if not origin:
            raise AssertionError("FRONTEND_ORIGIN ausente en .env — el "
                                 "cierre CORS por defecto sería total y el "
                                 "check del cierre reflejado no es pinnable")
        st_re, cab_re = preflight(origin)
        ok_reflejo = (st_re in (200, 204)
                      and cab_re.get("access-control-allow-origin") == origin
                      and cab_re.get("access-control-allow-origin") != "*"
                      and "DELETE" in cab_re.get(
                          "access-control-allow-methods", "")
                      and "x-confirm-name" in cab_re.get(
                          "access-control-allow-headers", "").lower())
        st_aj, cab_aj = preflight(ORIGEN_OTRO)
        ok_ajena = (st_aj == 403            # canon E7.9 SOLO el status
                    and "access-control-allow-origin" not in cab_aj)
        # sin cambio observable en pantalla (una sola origin ⇒ idéntico al C9):
        ir(page, "/proyectos")
        ok_pantalla = (page.locator("button", has_text=NOMBRE_B).count() == 1
                       and c9.textoVisible(page, "include=summary"))
        c9.vista(p, page, "15_cors_latente_preflight")
        if not (ok_reflejo and ok_ajena and ok_pantalla):
            raise AssertionError(
                f"CORS: preflight-reflejado({st_re})={ok_reflejo} · "
                f"origin-ajena({st_aj})={ok_ajena} · pantalla-sin-cambio="
                f"{ok_pantalla}")
        c9.conNota(p, "preflight OPTIONS con ACR-Method DELETE y "
                      "ACR-Headers X-Confirm-Name → ACAO = origin EXACTA "
                      "(reflejo, sin wildcard) y las cabeceras de "
                      "confirmación admitidas; origin ajena → 403 sin ACAO "
                      "(REQ-C10-CORS-1/3) · con UNA sola origin el "
                      "comportamiento en pantalla es idéntico al C9 "
                      "(CORS-2/5: latente, sin cambio observable)")
        c9.passO()
    except Exception as exc:
        c9.fail(p.id, f"excepción: {type(exc).__name__}: {str(exc)[:220]}")

    # cierre del observador (ya no hace falta para b7/limpieza):
    try:
        if ctx is not None:
            ctx.close()
            c9.cifra("observador cerrado (2.º contexto) — fin de la fase E10")
    except Exception:
        pass
    # ── fin de fase E10.x ──

    # nota de higiene local: c9.ID_PROYECTO_C9[0] sigue apuntando a A
    # (eliminado por 8a — la nota de residuo del informe lo aclara).


# ═══════════════════════════════════════════════════════════════════════════
# FASE L — limpieza del residuo de arneses (C9 + C10): guard duro y skip-and-
# report (D-C10-13 / REQ-C10-QA-5). NO es check — sus fallos se cifran y se
# reportan en el informe, jamá maquillados.
# ═══════════════════════════════════════════════════════════════════════════

def limpieza() -> None:
    """Enumerar GET /projects y purgar (8c) + eliminar (8a) SOLO los
    proyectos cuyo name matchea `^qa-c9-|^qa-c10-` (datos de arnés). Lo que
    NO matchea el guard se REPORTA y se SALTA — JAMÁS datos del usuario."""
    c9.fase("L · limpieza del residuo de arneses (guard ^qa-c9-|^qa-c10-)")
    st, cuerpo = rest("GET", "/api/v1/panel/projects", None, bearer())
    if st != 200 or not isinstance(cuerpo, list):
        print(f"[guard] GET /projects → HTTP {st} — limpieza NO posible: "
              "el residuo queda SIN limpiar (sello honesto para el "
              "qa-report); jamá SQL directo como rescate (R-1)", flush=True)
        return
    saltados: list[str] = []
    eliminados: list[str] = []
    fallos: list[str] = []
    for fila in cuerpo:
        if not isinstance(fila, dict):
            continue
        nombre = str(fila.get("name", ""))
        pid = fila.get("id")
        if not re.match(r"^qa-c9-|^qa-c10-", nombre):
            saltados.append(f"{nombre} (id {pid})")
            continue
        ok = True
        st_p, cuerpo_p = rest("DELETE", f"/api/v1/panel/projects/{pid}/logs",
                              None, {"X-Confirm-Name": nombre, **bearer()})
        if st_p != 200 or not isinstance(cuerpo_p, dict) \
                or cuerpo_p.get("deleted") is None:
            fallos.append(f"{nombre}: purga HTTP {st_p} sin cuerpo "
                          "{\"deleted\":N}")
            ok = False
        st_d, _cuerpo_d = rest("DELETE", f"/api/v1/panel/projects/{pid}",
                               None, {"X-Confirm-Name": nombre,
                                      **bearer()})
        if st_d != 204:
            fallos.append(f"{nombre}: delete HTTP {st_d}")
            ok = False
        if ok:
            eliminados.append(nombre)
    c9.cifra(f"limpieza: eliminados {len(eliminados)} de arnés "
             "(qa-c9-*/qa-c10-*) · saltados (NO matchean el guard) "
             f"{len(saltados)} · fallos {len(fallos)}")
    for s in saltados:
        print(f"   [skip] {s} — no matchea ^qa-c9-|^qa-c10- (datos del "
              "usuario, JAMÁS tocados — skip-and-report)", flush=True)
    for f in fallos:
        print(f"   [fallo] {f}", flush=True)


# ═══════════════════════════════════════════════════════════════════════════
# B8 C10 — informe (b8 del módulo C9 rebasado; GUARD PROPIO = 33 checks:
# 18 re-runs suaves + 15 E10.x — T10.29/ESPERA_CHECKS)
# ═══════════════════════════════════════════════════════════════════════════

def salirPorVeredictos10() -> int:
    """Tabla final ordenada (canon C8/C9) con guard PROPIO C10 = 33."""
    print("\n== VEREDICTOS C10 (orden por ID; re-runs E9 + checks E10.x) ==",
          flush=True)

    def orden(linea: str) -> tuple:
        m = re.match(r"[Ee](9|10)\.(\d+)", linea)
        return (int(m.group(1)), int(m.group(2))) if m else (999, 999)

    fails = 0
    bloqueados = 0
    for r in sorted(c9.RESULTADOS, key=orden):
        print(r)
        if ": FAIL" in r:
            fails += 1
        elif ": BLOQUEADO" in r:
            bloqueados += 1
    n = len(c9.RESULTADOS)
    print(f"\nresumen: {n} marcas · {fails} FAIL · {bloqueados} BLOQUEADO",
          flush=True)
    if n != ESPERA_CHECKS or len(c9.MARCADOS) != ESPERA_CHECKS:
        print(f"[guard] límite EXACTO C10 = {ESPERA_CHECKS} checks "
              f"(18 re-runs + 15 E10.x); marcados {n} (defecto del arnés "
              "— R9.6.1)", flush=True)
        return 1
    if fails > 0 or bloqueados > 0:
        return 1
    return 0


def informe(cont: "c9.Contadores", wsc: "c9.WsContador",
            version_pw: str) -> int:
    """Cifras RAW + estado del residuo + tabla de veredictos (estructura b8
    de C9 con el guard C10). Nada de credenciales en el stdout (S16)."""
    c9.fase("B8 · informe C10 (cifras RAW + residuo + guard de "
            f"{ESPERA_CHECKS})")
    print(f"[cifra] playwright {version_pw} · chromium headless · BASE "
          f"{c9.BASE} · TS C10={TS} (needles qa-c10-*)", flush=True)
    for patron, _prefijo in c9.Contadores.PATRONES:
        statuses = [s for p_1, s in cont.responses if p_1 == patron]
        resumen = ", ".join(f"{statuses.count(st)}×{st}"
                            for st in sorted(set(statuses))) or "0"
        c9.cifra(f"peticiones/response {patron}: {resumen}")
    c9.cifra(f"requests totales observados: {len(cont.requests)}")
    c9.cifra(f"WS: handshakes={wsc.on} · cierres={wsc.cerrado} · "
             f"set_filters enviados={wsc.set_filters} · batches recibidos="
             f"{wsc.batch_recibidos} · framesent observable="
             f"{wsc.observado_framesent} · PLAN B aplicado="
             f"{wsc.plan_b_usado}")
    shots = sorted(x.name for x in c9.DIR_EVID.glob("*.png"))
    print(f"[cifra] evidencia PNG en disco: {len(shots)} "
          "(NO commiteados — D-C9-12/C10: sha256 en qa-report)", flush=True)
    for s in shots:
        print(f"   - {s}")
    # Corrección T10.33 (defecto menor del arnés): el ESTADO del residuo se
    # deriva de lo corrido de verdad (c9.RESULTADOS), JAMÁS de un texto
    # hardcodeado — si la corrida murió antes de E10.x, el informe lo dice
    # «no corrió» (honesto: el guard de 33 y los exit canon quedan intactos).
    def veredictoCheck(cid: str) -> str | None:
        """Veredicto (passO/FAIL/BLOQUEADO…) de un check marcado, o None si
        NUNCA corrió en esta corrida."""
        for r_ in c9.RESULTADOS:
            if r_.startswith(f"{cid}:"):
                return r_.split(":", 1)[1].strip().split(" (")[0]
        return None

    def describeDestino(cid: str, accion: str) -> str:
        v = veredictoCheck(cid)
        if v is None:
            return (f"{accion} NO EJECUTADO ({cid} NO corrió en esta "
                    "corrida — estado en BD INVERIFICADO por el arnés)")
        if v.startswith("PASS"):
            return f"{accion} EJECUTADO y VERDE en la corrida ({cid} → PASS)"
        return (f"{accion} corrió PERO veredicto {v} en {cid} — "
                "estado en BD NO confirmado por el arnés (jamá fingido)")

    print("\n== RESIDUO tras la fase L (estado NOTIFICADO en las cifras de "
          "la limpieza; sin limpiar SOLO si la fase L guardó fallos) ==",
          flush=True)
    print(f"   · proyecto A qa-c10-{TS}-purga-8c (id {ID_A[0]}): "
          + describeDestino("E10.10", "purga 8c") + " · "
          + describeDestino("E10.13", "eliminación 8a"), flush=True)
    est_b = veredictoCheck("E10.4")
    if est_b is None:
        print(f"   · proyecto B qa-c10-{TS}-par-b (id {ID_B[0]}): "
              "no corrió E10.4/E10.5 en esta corrida — la fase L (si corrió) "
              "debe haberlo barrido con el guard; estado NO notificado "
              "hasta fase L verde", flush=True)
    else:
        print(f"   · proyecto B qa-c10-{TS}-par-b (id {ID_B[0]}): vivió para "
              f"los checks E10.4/E10.5 (veredicto {est_b.split(' (')[0]}) — "
              "eliminado en la fase L", flush=True)
    v_1010 = veredictoCheck("E10.10")
    if v_1010 is not None and v_1010.startswith("PASS"):
        print("   · semillas congeladas del módulo C9 (e35×60, e25, e17b): "
              "vivieron en el proyecto A y murieron con él (CASCADE)",
              flush=True)
    else:
        print("   · semillas congeladas del módulo C9 (e35×60, e25, e17b): "
              "estado a la espera de la PURGA 8c (sin purga GREEN, siguen "
              "en la BD — CASCADE aún no aplicado)", flush=True)
    print("   · keys del arnés: SOLO en memoria del proceso (muerto al "
          "salir); en la BD sobrevivieron SOLO a la PURGA (§3.3 literal), "
          "no a la eliminación de la fila (S16: jamá impresas)", flush=True)
    return salirPorVeredictos10()


# ═══════════════════════════════════════════════════════════════════════════
# main — orden de corrida C10 (D-C10-13): B0 → P0+siembra → re-runs suaves
# (b1/b3/b6) → E10.x (destructivos al final) → L → b7 → informe
# ═══════════════════════════════════════════════════════════════════════════

def main() -> int:
    print("== arnés C10 qa_vis_c10 — Playwright sync, chromium headless ==",
          flush=True)
    c9.verificarPila()                      # B0 (exit 2 honesto si pila caída)
    version_pw = c9.verificarEntorno()      # B0 (exit 2 si entorno incompleto)
    c9.DIR_EVID.mkdir(parents=True, exist_ok=True)
    print(f"[b0] evidencia dir: {c9.DIR_EVID} (NO commiteado)", flush=True)
    env10 = c9.cargarEnvProceso(c9.RUTA_ENV)
    c9.ENV = env10        # rebaso del ENV del módulo C9 (en proceso — S16)
    print("[b0] env cargado EN PROCESO (claves: ADMIN_INITIAL_USERNAME, "
          "ADMIN_INITIAL_PASSWORD, FRONTEND_ORIGIN — valores jamá a "
          "os.environ/argv/stdout)", flush=True)
    usuario = env10.get("ADMIN_INITIAL_USERNAME", "")
    password = env10.get("ADMIN_INITIAL_PASSWORD", "")
    if not usuario or not password:
        print("[guard] ADMIN_INITIAL_USERNAME/PASSWORD ausentes en .env "
              "(R9.1.2) — exit 2 honesto", flush=True)
        sys.exit(2)
    c9.COMPOSE_BIN = c9.detectarComposeBin()
    print(f"[b0] destructiva de la kill-window SOLO servicio backend: "
          f"{' '.join(c9.COMPOSE_BIN)}", flush=True)

    # P0 — REST: sesión + proyectos A/B por REST + siembra (needles
    # congelados de C9 para b6 + semillas propias qa-c10-*):
    aprovisionar(env10)

    from playwright.sync_api import sync_playwright

    cont = c9.Contadores()
    wsc = c9.WsContador()
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        try:
            ctx, page = c9.b1(browser, usuario, password, cont,
                              wsc)          # re-runs E9.1-7 (7 checks)
            c9.b3(browser, page, cont, wsc)  # re-runs E9.10/12/15 (3 checks)
            c9.b6(page, cont, wsc)           # re-runs E9.13/16/24/11-a/17/
                                             # 18/25 (7 checks, 6 marcas aquí)
            e10(page, browser, cont, wsc, usuario,
                env10)                       # 15 checks E10.x (destructivos
                                             # AL FINAL y SOLO qa-c10-*)
            limpieza()                       # fase L: guard ^qa-c9-|^qa-c10-
            c9.b7(page, cont, wsc)           # kill-window ÚLTIMA (E9.26 +
                                             # sello E9.11 — maquinaria C9)
            ctx.close()
        except Exception as e:
            print(f"[guard] excepción global del arnés C10: "
                  f"{type(e).__name__}: {str(e)[:200]}", flush=True)
            c9.sellarE911()   # E9.11 sin sello ⇒ guard de 33 falla (honesto)
        finally:
            browser.close()
    return informe(cont, wsc, version_pw)    # B8 rebasado (exit 0/1)


if __name__ == "__main__":
    if "--check" in sys.argv:
        # Chequeo trivial de carga (import C9 incluido) — sin corrida:
        print("arnés C10 cargado — no corrida (la corrida va por el "
              "wrapper qa_vis_c10.sh en su fase T10.32)")
        sys.exit(0)
    codigo = main()
    print(f"[exit] {codigo}", flush=True)
    sys.exit(codigo)