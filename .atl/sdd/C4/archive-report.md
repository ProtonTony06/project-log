---
change: C4
artifact: archive-report
last_updated: 2026-10-24
status: closed
commits:
  - 8437bdd feat(keys): API key de ingesta — regenerar/revelar una vez, revocar; hash SHA-256 + prefix (C4)
  - 8cf0b9f test(smoke): scripts/qa_c4_smoke.sh codificando E4.15–E4.29 [S] (C4)
  - docs(SDD): commit de cierre que incluye este informe (hash en `git log` a partir de 8cf0b9f)
---

# Archive Report — C4 · API keys de ingesta

Cierre del corte C4 (modo `atl`: sin sync/moves de `openspec/`; audit trail
en `.atl/sdd/C4/`). Veredicto: **PASS-with-notes** — 0 CRITICAL / 3 W / 3 S.
Base: HEAD a6298a8 + código C4 commiteado por este archive.

## Qué quedó implementado

- Paquete `com.projectlog.keys` (3 ficheros): `ApiKeyController` (POST
  `/projects/{id}/api-key/regenerate` 201 / DELETE 204, ramas 404/409
  ARCHIVED antes de generar, D-1/D-2/D-4/D-5), `ApiKeyService`
  (SecureRandom estático, Base64url 47 chars, sha256 UTF-8 del String
  completo, comparación tiempo-constante, sin loggers) y
  `ApiKeyResponse` (record snake_case).
- `ProjectsRepository`: 2 estamientos SQL aditivos
  (`UPDATE_REGENERAR_KEY`/`UPDATE_REVOCAR_KEY` con RETURNING) + Javadoc de
  clase ampliado; C3 intacto (pin en `ProjectsKeySqlTest`).
- Tests [L]: 17 en 4 suites (`ApiKeyControllerMvcTest` 9, `ApiKeyServiceTest`
  3, `ProjectsKeySqlTest` 4, `ApiKeyLogHygieneTest` 1).
- Smoke: `scripts/qa_c4_smoke.sh` (E4.15–E4.29, 15/15).

## Evidencia QA

- QA global **30/30 PASS** (QP-30 N-A: opcional fuera-de-spec, hueco G-1
  documentado); qa-log.txt con corrida canónica de re-corrida 2026-10-24.
- `mvn test`: 77/77 (Failures 0, Errors 0), BUILD SUCCESS (17 tests de C4 +
  C1/C2/C3 re-corridos).
- Smoke canónico: **15/15 PASS (E4.15–E4.29), exit 0**, recomposición única
  `--force-recreate`, sin `down -v` (volumen pgdata intacto).
- Complementos loop-back **QP-07/QP-26** verificados en la re-corrida:
  `Mockito.verify(projectsRepository).revokeKey(7L)` en el MvcTest y aserto
  [M] de `updated_at` avanzado (epoch > ref, `sleep 2`) en E4.26.

## Checks del verify (A–G): todos PASS

A diff-0 intocables (QP-29 cerrado por verify, 0 en todos los ficheros); B
CorsConfig sin `X-API-Key` (cierre S7); C ausencia de `findByApiKeyHash`
(R4.5, frontera C5); D nota maqueta 05 sigue siendo verdadera (Decisión 4);
E cobertura de los 9 Done + 409 de proposal §5 con evidencia ID a ID
(parejas obligatorias presentes); F semántica contra el código real
(7/7 spot-checks: orden 404→409→generación, revoke sin chequeo de status,
sha256 sin trim/salt, sin @ExceptionHandler/DIVE, SecureRandom estático, sin
loggers, estamientos aditivos); G QA como evidencia sin manipulación
(77/77 + 15/15 en qa-log, consistentes).

## Hallazgos (copiados del verify-report)

- **W-1 — Desviaciones D-1..D-6 preservadas** (documentadas en tasks "Notas
  transversales"), NO defectos: 204 por `status(NO_CONTENT).build()` (D-2) y
  constantes SQL package-private con pin en el mismo paquete sin reflection
  (D-3) divergen del texto literal de R4.7/R4.3 por necesidad técnica;
  `equals("ARCHIVED")` sin WHERE (D-1), helper `error()` 3 parámetros (D-4),
  `LinkedHashMap` (D-5), sin Javadoc extra de C5 (D-6). Queda anotado en
  archive que D-2/D-3 divergen del literal por necesidad técnica.
- **W-2 — [O] deliberados no ejecutados**: QP-01 Content-Type del 201, QP-10
  Content-Length 0, QP-19 cross-check `created_at`↔`key_created_at` (QP-29
  diff-0 quedó CERRADO por el verify, check A). Ninguno bloquea.
- **W-3 — Código sin commitear** → **RESUELTO**: los commits de este archive
  (código+tests, smoke, docs SDD) dejan el working tree limpio.
- **S-1 — Hueco G-1/QP-30**: rama (6) de R4.8 (RETURNING vacío→404, segunda
  barrera) sin método de test; el spec no le asigna E4.x (opcional por
  qa-plan). Reabrible en C5 si lo considera relevante por carreras.
- **S-2 — Hueco G-2**: semántica R4.12 ("hash persiste al desarchivar") sin
  escenario de test; E4.22/E4.25/E4.26 cubren archivar-no-toca-key y el
  409/DELETE en archivado, pero el ciclo completo ACTIVA+archivar+
  desarchivar no se corre (opcional, C5).
- **S-3 — LIKE-underscore**: único LIKE del smoke en E4.19c (~línea 368) usa
  como aguja la key completa; un `_` en la key opera como comodín en LIKE →
  patrón ligeramente más permisivo. Dirección conservadora (el check exige
  que NO haya plaintext); para C5+ usar needles literales con escape `\_` o
  `position()`/`=` en queries con guiones bajos (observación para cortes
  futuros).

## Deudas RETENIDAS (heredadas de C3, NO de C4 — solo se anotan)

- Smoke `docker compose` completo a re-verificar en C9 (C4 corrió bajo
  podman, patrón C3).
- 401 ISO-8859-1 en C8/C9 (heredado de C2, ya anotado en su archive).

## Deudas NUEVAS del corte

- NINGUNA.