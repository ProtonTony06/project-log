---
project: Project_Log
change: C9
artifact: verify-report
last_updated: 2026-10-29
status: done
results: 8/8 checks de verificación PASS (2 con nota documentada en línea — cifra [S] del design reconciliada y residuo de corridas previas) · CRITICAL 0 · WARNING 0 · SUGGESTION 3 (acotado arnés E9.2/VM 107/re-siembra — todos con ficha o decisión de usuario pendiente, C10)
---

# Verify Report — C9 · arnés QA visual + deploy VM/firewall

Verificación contra `spec.md` (R9-B1..B7/E9-B1..B7 y done-when A1–A6+B),
`design.md` (D-C9-1..15, runbook FB-0..FB-8), `tasks.md` (T9.1–T9.37) y los
informes de execution (`apply-progress.md`, `qa-report.md`, `qa-log.txt`,
`state.md` §fase-b-log/§demo-vm/§hallazgos-fase-b).

## 8 checks estilo C8

### (1) Diff del corte = arnés + qa-log + artefactos SDD (dif-0 producto) — PASS

- `git diff 4e2b559..1056453` = `scripts/qa_vis_c9.py` (bucle) +
  `.atl/notes/*` — **0 diff en frontend/, backend/, compose.yaml, .env\*,
  docs/** (T9.19, RAW en qa-log).
- Tree a la fecha (pre-commit final): tracked SIN cambios
  (`git diff HEAD` vacío); untracked SOLO `.atl/sdd/C9/` + `scripts/
  __pycache__/` (el __pycache__ NO entra al commit).
- Producto SIN CAMBIO → 60 Vitest/6 y 177/177 Java de canon C8 intactos.

### (2) Set de 26 checks EXACTO del gate G2 — PASS

- El arnés marca EXACTAMENTE 26 IDs con guard estructural (H-C9-B5:
  B5 condicional `[guard]` NO contamina el conjunto) y una línea por ID
  (H-C9-idempotente: FAIL gana).
- Lo NO ratificado NO existe como check: QP-8.11/8.31 clipboard, 8.38 y el
  matiz 8.37 (anclado en spec §0 frente a la discrepancia del gate) — 0
  apariciones de esos IDs como checks en `qa_vis_c9.py` (comprobado en
  Fase A, apply §frontier/frontera).
- Los 3 extras no-comparables (8.43 con key REDACTADA, 8.26→reclasificado
  [S], 8.34) presentes según gate.

### (3) Higiene S16 — PASS

- grep `plk_[A-Za-z0-9_-]{20,}` sobre `.atl/, .claude/, scripts/` → 0 hits
  (los literales del docstring usan prefijos SIN continuación de credencial
  — H-C9-S16-literales).
- grep `eyJ[A-Za-z0-9_-]{20,}` sobre `qa-log.txt` → 0.
- grep de asignación `*_PASSWORD=value`/`JWT_SECRET=value` literales sobre
  el repo → SOLO placeholders de docs preexistentes (C2/canon 02_TRD).
- Key de E9.23 REDACTADA en el shot (8a79051b revelación única; key solo en
  memoria del proceso). Login sin credenciales en evidencia; token sin eco.
- FB-8 `.env` del DESTINO: grep de fase = 0 valores (check 7).

### (4) Exit canónico + regla dura `pantalla_vista` — PASS

- Corrida FINAL de Fase A: exit **1** CANÓNICO (4-5 FAIL de producto/deploy
  fichados → C10 por decisión del gate; NO bugs de arnés).
- Regla dura D-C9-1/R9.1.4 ESTRUCTURAL: `passO` exige `pantalla_vista` — un
  PASS no observado se auto-convierte en FAIL «falso PASS bloqueado por el
  arnés» (activo en las 7 corridas; los 2 shots mal etiquetados de la
  corrida 6 E9.1/E9.12 son flakes de ETIQUETA con asertos observados, notados).

### (5) Jamás `down -v`/SQL/postgres desde el arnés + kill ÚNICO salud 180 s — PASS

- grep del arnés: 0 hits de down -v / SQL / postgres en el código (B0/frontera).
- Kill local ÚNICO `stop backend` (sin `down -v`) + `bucleHealth` 180 s con
  recuperación de proxy recreado (E9.26/E9.11; en la VM: demo criterio 3 con
  ventana única + delta 0 sin duplicados).

### (6) Playwright fuera del frontend (dif-0, QP-8.54) y guion fuera de la frontera Vitest — PASS

- 0 hits de playwright en `frontend/package.json`; los 2 hits del lockfile
  son `@vitest/browser-playwright` de C8 (pre-existente, sin dif).
- El arnés (`scripts/qa_vis_c9.*`) vive FUERA de `frontend/` — la frontera
  Vitest (6 ficheros) queda intacta (check 1).

### (7) Fase B: binario, `5m`, `.env` jamás al repo/impreso, excludes rsync — PASS

- Binario anotado DOBLE: arnés local = podman compose (H-C9-COMPOSE,
  resuelto en vivo B0) · DESTINO = **docker compose v5.6.1** (FB-3/FB-5).
- `client_max_body_size: 5m` anotado en qa-report §Fase B; editado SOLO en
  la copia del DESTINO; **repo dif-0** (nginx.conf del repo sin cambios);
  excludes rsync actualizados con `frontend/nginx.conf` para futuras
  sincronías (R-D-C9-9).
- `.env` del destino creado EN la VM (R9-B7): 17 vars, chmod 600, valores
  jamás impresos/commiteados; corrigendum H-C9-FB2-DATASOURCE documentado
  (un valor compartido DB/datasource; `down -v` solo sobre la pila FRESCA
  de la VM nueva, lección Q13 respetada — jamás sobre siembra de usuario).
- Pareja CERO-413 verificada desde la LAN (202 `{"accepted":200}` válido /
  413 JSON del BACKEND en 1,2 MiB) — el design pedía pareja en FB-6, hecha.

### (8) Done-when A1–A6+B trazado — PASS (con límites honestos)

- **A1 [L]**: 26/26 veredictos individuales con snapshot semántico del DOM
  vivo; regla dura estructural activa → cero PASS sin pantalla observada.
- **A2 [L]**: criterios 1/2 PASS (2.ª ventana sin refetch; stack <120 s con
  cronómetro 0,6 s); criterio 3 verificado con delta 0 — la RECONEXIÓN WS
  post-restauración FAIL honesto (H-C9-WS-COLGADO → C10; en la VM anotado
  el mismo límite, demo REST verde).
- **A3 [L]**: 9 extras G2 automatizados con sus literales (E9.14 nota
  G1-OBLIGATORIA incluida).
- **A4 [S]**: qa-log RAW commiteado + 30 PNG con sha256 ENLISTADOS en
  qa-report (NO commiteados, D-C9-12); greps S16 = 0.
- **A5 [R]**: manuales/PASS-CON-NOTA §5 sin silencios en qa-report;
  re-corrida `qa_c8_smoke.sh` final EXIT 0 (23 PASS/0 FAIL/1 BLOQUEADO Q23)
  = canon C8. La cifra «32 [S]» del design = líneas con [S] de la tabla C8
  (hoy 33; 21 en columna veredicto) — reconciliado por escrito con la
  medida real del smoke (24 marcas).
- **A6 [MANUAL]**: ratificación visual humana de los 30 shots — pendiente
  del usuario al revisar el artefacto QA (PNGs en `.atl/sdd/C9/evidencia/`).
- **B (G4 aprobada)**: R9-B1/B2/B4/B5/B6/B7 VERDE en VM 108 (192.168.1.58)
  — tabla completa en qa-report §Fase B; SSH nunca cortado; consola de
  escape validada; BLOQUEADA-ACCESO no necesario.

## Hallazgos del verificador (CRITICAL 0 · WARNING 0 · SUGGESTION 3)

1. **SUGGESTION** — acotado del arnés pendiente (observación de ARRIBO en
   E9.2 + retardo esqueleto E9.12 + shots de E9.1): la etiqueta infiel no
   invalida asertos observados, pero merece el parche QA acotado de C10.
2. **SUGGESTION** — VM 107 huérfana de la sesión muerta (sin IP por el bug
   de render de PVE 9.0.3): decisión borrar/reparar pendiente del usuario
   (documentada en state.md §vm107-huerfana; la VM 108 no la pisa).
3. **SUGGESTION** — siembra local barrida por el smoke final (canon T9.31):
   re-siembra solo si el usuario pide demo; estado limpio anotado.

## Frontera

Sin push a origin (espera instrucción). Sin PNGs ni `__pycache__` ni
`.env` en el commit `docs(SDD)` final. Hallazgos de producto y de deploy
no resueltos aquí están fichados para C10.