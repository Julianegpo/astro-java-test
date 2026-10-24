# webapp — Astro app (template)

**Astro 7** starter generated from the `basics` template and **deliberately left untouched**: the
work in this repo lives in `server/` (see [`../SPEC.md`](../SPEC.md)) and the rule is never to
touch this folder (success criterion 7).

```sh
npm create astro@latest -- --template basics
```

## Commands

All from `webapp/`:

| Command | Action |
| :--- | :--- |
| `npm install` | Installs dependencies |
| `npm run dev` | Starts the dev server at `localhost:4321` |
| `npm run build` | Production build to `./dist/` |
| `npm run preview` | Previews the build |
| `npm run astro ...` | Astro CLI (`astro add`, `astro check`, …) |

## Structure

```text
webapp/
├── public/            # static assets
├── src/
│   ├── assets/        # images
│   ├── components/    # .astro components
│   ├── layouts/       # layouts
│   └── pages/         # routes (index.astro)
├── astro.config.mjs
└── package.json
```

Docs: <https://docs.astro.build>.
