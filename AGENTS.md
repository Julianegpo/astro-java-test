# Agent Instructions

## Repo layout

- Single app: `webapp/` — a stock Astro starter (basics template). No monorepo tooling.
- All commands (npm, astro) run from `webapp/`, not the repo root.
- Not a git repository — branch/commit/PR steps from skills do not apply until git is initialized.
- Node `>=22.23.4` required (`webapp/package.json` engines); npm with committed `package-lock.json` (not pnpm/yarn).
- `astro.config.mjs` is empty (framework defaults, dev server on port 4321). `tsconfig.json` extends `astro/tsconfigs/strict`.
- No test, lint, typecheck, or CI tooling is configured. Don't invent verification commands; `npm run build` is the only real check today.
- `webapp/AGENTS.md` is auto-loaded for work in `webapp/` and covers the dev server — start it in background mode (`astro dev --background`, managed via `astro dev stop|status|logs`). Never run a foreground dev server.

## Agent Skills (OpenCode)

This project pins skills under `.agents/skills/` (see `skills-lock.json` for sources and hashes).

- If a task matches a skill, invoke it with the `skill` tool before acting. Skills live at `.agents/skills/<skill-name>/SKILL.md`.
- Follow the skill workflow strictly; never skip required steps (spec, plan, test) when a skill demands them.

Intent → skill mapping:

- Feature / new functionality → `spec-driven-development`, then `incremental-implementation` and `test-driven-development`
- Planning / breakdown → `planning-and-task-breakdown`
- Bug / failure / unexpected behavior → `debugging-and-error-recovery`
- Code review → `code-review-and-quality`
- Refactoring / simplification → `code-simplification`
- API or interface design → `api-and-interface-design`
- UI work → `frontend-ui-engineering`
