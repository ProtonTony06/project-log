---
project: "Project_Log"
artifact: skill-registry
last_updated: 2026-10-20
purpose: >
  Registro de skills disponibles en el entorno (nivel usuario). El
  orchestrator inyecta los compact rules relevantes en cada sub-agente,
  como TEXTO pre-digerido (los sub-agentes NO leen este fichero).
---

# Skill Registry — Project_Log

## Skills relevantes para el proyecto

| Skill | Triggers / cuándo inyectar |
|---|---|
| github-skill | Flujos de Git y GitHub (crear repo, branches, PRs) — relevante en C1 (repo nuevo) y siguientes |
| branch-pr | Creación de PRs en Agent Teams Lite, issue-first — solo si se usa GitHub remoto |
| issue-creation | Creación de issues en Agent Teams Lite, issue-first — solo si se usa GitHub remoto |
| render-deploy | Deploy en Render — NO aplica (este proyecto usa Docker Compose en Proxmox); no inyectar |

## Skills excluidas del registro (meta o de otros proyectos)

- `sdd-*` (sdd-init, sdd-explore, sdd-propose, sdd-spec, sdd-design, sdd-tasks,
  sdd-apply, sdd-verify, sdd-archive): fases del propio flujo SDD.
- `skill-registry`, `skill-creator`, `skills-sync`, `subagent-creator`,
  `memory-saver`, `get-memory-skill`: meta/infraestructura.
- `cv-fullstack`, `goalapp-backend-api`, `go-testing`, `judgment-day`,
  `supabase-skill`, `memory-saver`, `render-deploy`: de otros proyectos o
  stacks no usados aquí (Supabase NO es el stack de Project_Log).

## Convenciones de proyecto

- No existe ni `CLAUDE.md`, `agents.md`, `AGENTS.md` ni reglas de lint en el
  project root todavía; el paquete de definición (`docs/`) hace de fuente de
  verdad de literales de API/UI/BD hasta que el código y su tooling existan.