# webapp — app Astro (plantilla)

Starter **Astro 7** generado con la plantilla `basics` y **dejado intacto a propósito**: el trabajo de este repo está en `server/` (ver [`../SPEC.md`](../SPEC.md)) y la regla es no tocar esta carpeta (criterio de éxito 7).

```sh
npm create astro@latest -- --template basics
```

## Comandos

Todos desde `webapp/`:

| Comando | Acción |
| :--- | :--- |
| `npm install` | Instala dependencias |
| `npm run dev` | Servidor de desarrollo en `localhost:4321` |
| `npm run build` | Build de producción a `./dist/` |
| `npm run preview` | Previsualiza el build |
| `npm run astro ...` | CLI de Astro (`astro add`, `astro check`, …) |

## Estructura

```text
webapp/
├── public/            # assets estáticos
├── src/
│   ├── assets/        # imágenes
│   ├── components/    # componentes .astro
│   ├── layouts/       # layouts
│   └── pages/         # rutas (index.astro)
├── astro.config.mjs
└── package.json
```

Documentación: <https://docs.astro.build>.
