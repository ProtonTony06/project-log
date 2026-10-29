---
project: Project_Log
change: C9
artifact: qa-report
last_updated: 2026-10-29
status: done
results: >-
  Fase A (arnés QA visual, corrida FINAL 6): 21 PASS / PASS-CON-NOTA de 26 ·
  5 FAIL honestos PRODUCTO/DEPLOY (4 hallazgos fichados → C10; E9.26 comparte
  celda con E9.11) · 1 sub-BLOQUEADO-B honesto en E9.7 (storage bloqueado no
  emulable — jamás fingido) · exit 1 honesto = estado de cierre de Fase A.
  Producto SIN CAMBIO verificado (diff arnés-only): 60 Vitest / 6 ficheros y
  177/177 Java de canon C8 intactos. Fase B: E9-B1..B7 VERDE (deploy vm108
  3/3 healthy por 8080 desde la LAN; firewall nftables GATE #11 SSH-jamás-
  cortado + cierre verificado en vivo; pareja CERO-413 202/413-JSON-backend
  con 5m anotado; FRÍO-OK + REINICIO-OK autónomos; smoke compose v5.6.1
  verde; grep S16 de fase = 0 secretos; demo honesta REST de los 3 criterios
  del 06 con el límite WS anotado). Regresión final T9.31: smoke C8
  re-corrido = EXIT 0 · 24 marcas · 23 PASS · 0 FAIL · 1 BLOQUEADO (Q23
  manual) — idéntico al canon C8 (qa-log.txt:511 de C8).
commit: 1056453 (arnés QA final; el bucle del arnés quedó commiteado en 975637f/b50ab4b/6367443/1056453; el smoke T9.31 y la Fase B corrieron con el tree en ese commit + artifacts .atl uncommited)
log: qa-log.txt
---

# QA Report — C9 · QA visual automático (arnés Playwright) + deploy VM/firewall

Veredicto por EJECUCIÓN FORMAL: Fase A con arnés Playwright headless contra
la PILA REAL (regla dura estructural: ningún PASS sin pantalla observada);
Fase B con el runbook FB-0..FB-8 sobre la VM nueva. RAW verbatim de Fase A
en `qa-log.txt` (corrida 6 FINAL; residuos de corridas 3/4/5 con nombres
.bak/.txt aparte). Exit 1 de la corrida final = hallazgos de PRODUCTO que
van fichados a C10 (decisión del gate del coordinador: parches de producto
NO en C9); NO son bugs de arnés.

## Comandos ejecutados (orden REAL)

1. Fase A — 7 corridas `python3 scripts/qa_vis_c9.py` (bucle de hallazgos):
   C1 abortada (CORS 403), C2 10 FAIL (causas-raíz del ARNÉS), C3 8 FAIL +
   pila restaurada, C4 5 FAIL, C5 6 FAIL (2 flakes), C6 **FINAL**. Detalle y
   parches: `apply-progress.md` §Bloques 2-3 (commits 975637f, b50ab4b,
   6367443, 1056453).
2. Verificación de no-toxicidad (T9.19): diff `4e2b559..HEAD` = SOLO
   `scripts/qa_vis_c9.py` + `.atl/notes/*` — **0 diff en frontend/,
   backend/, compose.yaml, .env\*, docs/**. 60 Vitest / 6 ficheros y 177
   Java SIN CAMBIO (corridas de canon C8, no re-corridas: el diff es cero).
3. Fase B — `rsync` → `.env` generado EN la VM → `docker compose up -d
   --build` → nftables GATE #11 → smoke canónico → pareja nginx
   body-size → apagado/arranque frío → `reboot` (runbook FB-0..FB-7, RAW
   redactado en `state.md` §fase-b-log por restricción S16 del entorno).
4. T9.31 `bash scripts/qa_c8_smoke.sh` (AL FINAL del corte, tras Fase B) →
   **EXIT 0 · 24 marcas · 23 PASS · 0 FAIL · 1 BLOQUEADO (Q23)** — idéntico
   al canon C8 (C8/qa-log.txt:511). Su `down -v` barrió la siembra local
   (esperado; re-siembra anotada en T9.32).

## Tabla de veredictos — Fase A (26 parejas MANUAL-Q23 del gate + 3 criterios demo del 06)

| ID | Veredicto corrida 6 | Hallazgo |
|----|---------------------|----------|
| E9.1 | PASS-CON-NOTA | shot mal etiquetado (arnés, pantalla SÍ observada) |
| E9.2 | **FAIL PRODUCTO** | H-C9-E9.2 — guard OK pero `<Navigate to="/">` de Login pisa `navegar(destino)` (arribo real siempre /logs; repro×3 + trazas) |
| E9.3 | PASS-CON-NOTA | /login con sesión redirige + token basura descartado |
| E9.4 | PASS-CON-NOTA | 404 panel en vivo, URL estable |
| E9.5 | PASS-CON-NOTA | 401 inline, URL estable |
| E9.6 | PASS-CON-NOTA | «Enviando…» deshabilitado, n_posts=1 |
| E9.7 | PASS-CON-NOTA **(+BLOQUEADO-B honesto)** | A recarga verificada; B storage bloqueado NO emulable — jamás fingido |
| E9.8 | PASS-CON-NOTA | logout 0 requests/0 handshakes, re-login por UI |
| E9.9 | PASS-CON-NOTA | JWT SOLO en sessionStorage (booleanos) |
| E9.10 | **FAIL PRODUCTO** | H-C9-E9.10 — delta=3 frames `set_filters` por evento (contradice R9.2.10 «UN set_filters») |
| E9.11 | **FAIL PRODUCTO** | H-C9-WS-COLGADO — backoff close-driven sin timeout CONNECTING; cadena muerta con upstream envejecido |
| E9.12 | PASS-CON-NOTA | esqueleto/vacío/400 diferenciados (shot mal etiquetado — arnés; aserto SÍ observado) |
| E9.13 | **FAIL PRODUCTO** | H-C9-E9.13 — `/logs/388??text=…` doble «?» rompe round-trip keyset E8.35 |
| E9.14 | PASS-CON-NOTA | vacío legítimo + CTA (nota G1 obligatoria) |
| E9.15 | PASS-CON-NOTA (sub-BLOQUEADO anotado) | superficie DUAL 400-API vs banner-normalizado (D-C8-6) |
| E9.16 | PASS-CON-NOTA | banda «● EN VIVO» + batch_id, sin refetch |
| E9.17 | PASS-CON-NOTA | exception/metadata/correlation CON/SIN |
| E9.18 | PASS | 404 detalle: main nunca vacío + Volver |
| E9.19 | PASS-CON-NOTA | tarjeta maqueta 04 (Activo/has_key/sin resumen) |
| E9.20 | PASS-CON-NOTA | ficha maqueta 05 + bloque API key + 404 |
| E9.21 | PASS-CON-NOTA | 201 → ficha + aviso literal (proyecto C9 por UI, id 13) |
| E9.22 | PASS-CON-NOTA | modales 8a/8c dual OKLCH/rgb, CERO DELETE |
| E9.23 | PASS-CON-NOTA | revelación única + shot REDACTADO, key solo en memoria |
| E9.24 | PASS-CON-NOTA | **CRITERIO 1**: 2.ª ventana ve el lote sin refetch |
| E9.25 | PASS-CON-NOTA | **CRITERIO 2**: stack del needle visible en <120 s |
| E9.26 | **FAIL PRODUCTO/DEPLOY** | H-C9-WS-COLGADO — delta 0 al volver pero WS sin recuperación |

## Hallazgos de producto (fichas `.atl/notes/c9-hallazgo-*.md` — parches en C10)

1. **H-C9-WS-COLGADO** — reconexión WS se cuelga (E9.11 + E9.26); backoff
   close-driven sin timeout de CONNECTING.
2. **H-C9-E9.13** — doble «?» en `/logs/:id??text=` rompe el acumulado keyset
   (E8.35).
3. **H-C9-E9.2** — el destino recordado del guard se pierde tras el login
   (Navigate condicional pisa `navegar(destino)`).
4. **H-C9-E9.10** — frames `set_filters` múltiples por evento (identidad de
   objeto).

Además **H-C9-NGINX-UPSTREAM** (C10 en producto): el frontend reiniciado
puede arrancar con upstream DNS envejecido — en Fase B se MITIGÓ a nivel
deploy (`restart: unless-stopped` + force-recreate tras saneo; el arranque
frío/reinicio con red completa NO lo reprodujo — FB-7).
`H-C9-FB2-DATASOURCE` (corrigendum del design FB-2: un solo valor compartido
para POSTGRES/SPRING_DATASOURCE passwords), `H-C9-FB6-CHARSET-413` (el JSON
del 413 backend va ISO-8859-1 — patrón H-C8-B2; literal `code` correcto) y
`H-C9-CLOCK-VM108` (reloj local marcado UTC; cosmético) quedan en
`state.md` §hallazgos-fase-b.

## Manuales / PASS-CON-NOTA (§5 — sin silencios)

- E9.7-B: el escenario storage bloqueado NO es emulable en Playwright —
  BLOQUEADO honesto, jamás fingido (canon §5 C8).
- E9.15: el banner visual normaliza el 400 por diseño (D-C8-6) — el aserto
  de banner-con-«Campo culpable» queda BLOQUEADO-anotado; lo API-contrato
  (code+field) SÍ verificó (canon E8.23).
- E9.1/E9.12: dos screenshots etiquetados por pantalla-adyacente (flakes del
  arnés post-corría 5, notados para el acotado C10) — los ASERTOS verdes sí
  fueron observados en la pantalla real.
- Q23 del smoke C8: visual manual por diseño (su hueco es EXACTAMENTE lo
  que este arnés C9 automatiza).

## Fase B — veredictos E9-B1..B7 y salidas redactadas

| Req | Veredicto | Evidencia |
|-----|-----------|-----------|
| R9-B3/E9-B3 (guard acceso) | N/A-PASSED-positivo | SSH OK al primer intento (BLOQUEADA-ACCESO no necesario; guard documentado en FB-0) |
| R9-B1/E9-B1 (pila healthy por 8080 desde LAN) | **PASS** | 3/3 contenedores healthy; panel HTML 200 desde la LAN (login UI por REST/8080 verificado en FB-5 y demo) |
| R9-B2/E9-B2 (GATE #11 orden dura) | **PASS** | SSH-LAN PRIMERO + verificación en conexión NUEVA, DESPUÉS 8080, verificación en vivo desde la LAN ANTES del cierre; consola de escape validada ANTES; jamás SSH cortado. Mecanismo: nftables `table inet pl_vm` chain input policy drop — lo/established/22/8080 (192.168.1.0/24). Boot-safe: `flush table` SCOPED con tabla pre-declarada (el `flush ruleset` global destruye las cadenas iptables-nft de Docker — incidente FB-4 en `state.md`) |
| R9-B4/E9-B4 (frío + reinicio) | **PASS** | FRÍO-OK y REINICIO-OK: 3/3 healthy AUTÓNOMAS (restart: unless-stopped + onboot=1), nftables+docker activos, panel 200 desde LAN en ambos estados. Catch valioso: 1.er render del conf FAILED al boot frío (`flush table` sobre tablas vacías) — corregido boot-safe y re-probado limpio |
| R9-B5/E9-B5 (CERO-413 del proxy) | **PASS** | Pareja desde LAN: (1) ~0,9 MiB schema-válido → **202 `{"accepted":200}`** (proxy no interviene); (2) ~1,2 MiB schema-válido → **413 JSON del BACKEND** (`code":"payload_too_large"`, Latin-1) — el cuerpo ALCANZÓ al backend POR ENCIMA del default; NINGÚN 413 del proxy en la ventana. Valor anotado: **`client_max_body_size 5m;`** en la copia del DESTINO (línea 6 tras `server_name _;` — confirmado en contenedor vivo vía `nginx -T`; repo dif-0; excludes rsync actualizados con `frontend/nginx.conf`) |
| R9-B6/E9-B6 (smoke compose + binario) | **PASS** | Binario anotado: **docker compose — Docker Compose version v5.6.1** (repo docker-ce en Debian 12). Smoke: 3/3 healthy + login 200 (orden literal {expires_at, token, username}) + /me 200 ADMIN, token JAMÁS impreso |
| R9-B7/E9-B7 (secretos/S16) | **PASS** | `.env` generado EN la VM (17 vars, chmod 600, único valor compartido DB/datasource — corrigendum H-C9-FB2-DATASOURCE); jamás rsync-imprimido-commiteado. **grep S16 de fase = 0 secretos**: `plk_/eyJ/var=val` → 0 hits en `qa-log.txt` y en todo `.atl/`; el diff de tracked a la fecha de este reporte es VACÍO; solo placeholders de docs preexistentes (C2/canon) en los greps |
| Demo 3 criterios del 06 (VM) | **VERDE (REST/deploy)** | Criterio 1: lote 3 → 202 `{"accepted":3}`, visible REST. Criterio 2: ERROR production → 202, excepción+metadatos en detalle. Criterio 3 REST: caída → 000 → healthy → delta 0 sin duplicados. **LÍMITE HONESTO** (no simulado): las observaciones NIVEL NAVEGADOR (2.ª ventana sin refetch; cronómetro <120 s; banda/banner y «exactamente 1 refetch» post-restauración) se demostraron en FASE A sobre la pila real (E9.16/E9.24 PASS, E9.25 PASS, E9.26 FAIL por H-C9-WS-COLGADO); en la VM, la parte WS-reconexión del criterio 3 fallaría igual por ese mismo hallazgo (C10) |

## Regresión final (T9.31) y reconciliación de cifras del design

- Re-corrida `qa_c8_smoke.sh` post-Fase B: **EXIT 0 · 24 marcas · 23 PASS ·
  0 FAIL · 1 BLOQUEADO (Q23 manual)** — bit a bit el resumen del canon C8.
- La cifra «32 veredictos [S] PASS» de design.md §7/spec/tasks corresponde
  al conteo de LÍNEAS con `[S]` en la tabla QP-8.x del qa-report C8 (33 hoy;
  21 con la columna veredicto [S]-backed, sobre 59 filas) — NO es una
  propiedad del script del smoke, que emite 24 marcas. Medida REAL del smoke
  = línea resumen arriba. Reconciliado aquí por escrito; sin silencio.

## Inventario PNG de evidencia (30 ficheros, NO commiteados — D-C9-12/S16)

`.atl/sdd/C9/evidencia/` — sha256 truncado a 8; hashes repetidos = mismo PNG
copiado por pantalla para checks afines:

51010118 E9.1_01_01_logs_ruta · 018ea60a E9.11_01_01_conectado ·
8be58106 E9.11_01_02_proyectos_sin_indicador · c5046609 E9.12_01_01_esqueleto ·
21a5c4ab E9.12_02_02_banner_400_from_to · 749a102f E9.13_01_01_acumulado_60 ·
51010118 E9.14_01_02_cta_navegada_real · c5046609 E9.15_01_01_url_level ·
8ec804b1 E9.16_01_01_banda_grupo_lote · 98059a71 E9.17_01_01_bloques_con_exception ·
ca4ec303 E9.18_01_01_404_detalle · aff973e1 E9.19_01_01_tarjetas_maqueta_04 ·
4a7941db E9.20_01_01_ficha_maqueta_05 · c5046609 E9.2_01_01_retorno_destino ·
498877c8 E9.21_01_01_aviso_201 · 37b3efd8 E9.22_01_01_modal_8a_habilitado ·
32f2e365 E9.22_02_02_modal_8c_habilitado · 8a79051b E9.23_01_01_revelacion_redactada ·
8ec804b1 E9.24_01_01_antes · 018ea60a E9.24_02_02_despues_grupo ·
98059a71 E9.25_01_01_criterio2_cronometro · 26a623df E9.26_01_01_pre_caida ·
5c993014 E9.26_02_02_banner_caida · 066f49c4 E9.3_01_02_token_basura_formulario ·
1b16fa1d E9.4_01_01_404_panel · 48514489 E9.5_01_01_401_inline_limpio ·
d9240e2f E9.6_01_01_enviando_deshabilitado · c5046609 E9.7_01_01_restauro_logs ·
f4297454 E9.8_01_01_login_final_limpio · c5046609 E9.9_01_01_logs_pantalla_aserto
(todos .png)

## Residuo (R9.5.5) y frontera del corte

- Residuo de siembra en la pila LOCAL notificado y NO limpiado (fichados en
  apply-progress §residuo); el smoke T9.31 barrió la siembra local con su
  `down -v` (canon T9.31) — estado local: recompuesta y sin siembra C8/C9;
  re-siembra condicional anotada (T9.32, solo si el usuario pide demo).
- La pila LOCAL conserva 60 Vitest/6 y 177 Java SIN CAMBIO (Fase A dif-0).
- Pendiente de decisión del usuario (C10): VM 107 huérfana (borrar o
  reparar — fix = re-render con nameserver único), corrección de los 4
  hallazgos de producto + NGINX-UPSTREAM, acotado arnés E9.2 (observación de
  arribo), UTF-8 en el 413 de ingesta.
- Push a origin: NO (espera instrucción explícita).