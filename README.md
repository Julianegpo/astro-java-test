# astro-java-test

Monorepo con dos apps independientes y sin relación entre sí:

| Carpeta | Qué es | Estado |
| --- | --- | --- |
| [`webapp/`](webapp/) | App **Astro 7** (starter por defecto, intacta) | plantilla sin tocar |
| [`server/`](server/) | **Backend microservicios Spring Boot 3.5** (config/Eureka/auth) | en construcción — T1–T6 listas, T7–T11 pendientes |

- La fuente de verdad del backend es [`SPEC.md`](SPEC.md) (22 decisiones cerradas).
- El progreso vive en [`tasks/plan.md`](tasks/plan.md) y [`tasks/todo.md`](tasks/todo.md).
- Guía narrativa de todo lo construido: [`GUIA.md`](GUIA.md).

> La clave privada RS256 bajo `server/config-server/src/main/resources/config/auth-service.yml`
> es **SOLO-DEV** (servicio de ejemplo local). No tiene valor como secreto: no protege nada real
> y generar la tuya tarda 10 segundos (ver «Generar tu propia clave» en el README de `server/`).

## Arranque rápido

Backend (desde `server/`, en este orden):

```powershell
./mvnw -B spring-boot:run -pl config-server     # 8888
./mvnw -B spring-boot:run -pl discovery-server  # 8761
./mvnw -B spring-boot:run -pl auth-service      # puerto aleatorio (sale en el log)
```

Frontend (desde `webapp/`):

```powershell
npm install
npm run dev        # http://localhost:4321
```

Detalles, flujo JWT y Swagger en [`server/README.md`](server/README.md).
