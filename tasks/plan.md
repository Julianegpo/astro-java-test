# Implementation Plan: Esqueleto microservicios Spring (`server/`)

> Fuente de verdad: [`../SPEC.md`](../SPEC.md) (aprobada, 22 decisiones cerradas). Este plan concreta **cómo**, la spec define **qué**.

## Overview

Construir bajo `server/` un multi-módulo Maven con un parent (`build-parent`) que fija todas las versiones y cinco módulos: `config-server` (nativo, 8888), `discovery-server` (Eureka, 8761), `auth-service` (H2 + JWT RS256, puerto aleatorio), `demo-service` (`Hello world!`, puerto aleatorio) y `gateway-server` (8080, única puerta de entrada, valida JWT y sirve Swagger UI agregado). Build con QA completo (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo) y `--release 21` compilado con la JDK 25 instalada.

## Architecture Decisions (del plan, sobre la base de la spec)

- **Versiones fijadas hoy (Maven Central / docs oficiales, oct-2026)** — verificado, no supuesto:

  | Componente | Versión | Fuente |
  |---|---|---|
  | Spring Boot | **3.5.16** (LTS; docs: "compatible with versions up to and including Java 25" ✓ JDK 25 OK) | docs.spring.io/spring-boot/3.5 |
  | Spring Cloud | **2025.0.3** (último train de Boot 3.5; 2025.1.x/2026.x son para Boot 4) | Maven Central |
  | springdoc-openapi | **2.9.1** (línea Boot 3.x; swagger-ui 5.32.14 con fix XSS) | springdoc.org/v2 |
  | Lombok | **1.18.48** (forzado vía `lombok.version`) | Maven Central |
  | JaCoCo | **0.8.15** | Maven Central |
  | Spotless plugin | **3.10.4** (+ google-java-format **1.36.1**; gjf ≥1.37.0 rompe la glue del plugin al pasar `Style` de enum a record) | Maven Central; causa raíz verificada con `javap` en T3 |
  | SpotBugs plugin | **4.10.5.0** (+FindSecBugs) | Maven Central |
  | Checkstyle | plugin **3.6.0** + tool **14.3.0** | Maven Central |
  | Maven Wrapper | **3.9.16** (último 3.9.x estable; 4.0 solo RC) | Maven Central |

- **Config client sin `bootstrap.yml`** — refinamiento técnico de D5 (flag para el usuario): el patrón estándar desde Spring Cloud 2020.x es `spring-cloud-starter-config` + `spring.config.import: optional:configserver:http://localhost:8888` en el `application.yml` de cada servicio (así lo hace el demo oficial `springdoc-openapi-demos/demo-microservices`, verificado). Cada servicio lleva su `spring.application.name` local y todo lo demás viaja del config server. *(Si prefieres el `bootstrap.yml` clásico, se cambia antes de empezar.)*
- **Claves RS256** (D14/D21): par generado con una herramienta Java de una sola ejecución con la JDK 25 (`keytool`/single-file program → PEM PKCS#8 + X.509). Privada solo en `config-server/src/main/resources/config/auth-service.yml`, pública en `gateway-server.yml`. Marcado SOLO-DEV.
- **Rutas del gateway** (D6 + agregación D22), patrón verificado en el demo oficial de springdoc:
  - `POST /api/auth/token` → `lb://auth-service` (el servicio es dueño de su path completo, sin rewrite);
  - `/api/demo/**` → `lb://demo-service`;
  - bucle OpenAPI: `/v3/api-docs/auth-service` y `/v3/api-docs/demo-service` → `lb://<servicio>` con `RewritePath` a `/v3/api-docs` (el `openapi` self-loop del demo, aquí explícito por servicio);
  - ⚠️ En Spring Cloud **2025.0.x** el namespace es `spring.cloud.gateway.*` (el `.server.webflux` del demo es de gateway 5.x/Boot 4) — verificar arranque.
- **Agregación swagger en gateway**: probar primero la agregación automática de springdoc 2.9.1 (la que usa el demo oficial); si el UI no lista los servicios, fallback documentado `springdoc.swagger-ui.urls[]` (misma property en la doc v2) — mismo diseño de rutas, sin retrabajo.
- **auth-service seguridad**: tiene `spring-boot-starter-security` (necesario para resource-server/crypto) → cadena de seguridad propia que permite `POST /api/auth/token`, swagger, actuator/health y protege el resto; el JWT real lo exige el gateway (D6).
- **H2** (D16): `jdbc:h2:mem:authdb`, `ddl-auto=none`, `schema.sql` + `data.sql` (INSERT usuario `demo` con hash BCrypt precalculado). Boot ejecuta el SQL init **antes** de JPA → el INSERT funciona sin defer.
- **Tests en frío** (sin red/servicios): perfiles `test` con `spring.cloud.config.enabled=false`/`optional:configserver` y Eureka desactivados; el E2E del gateway usa `demo-service` stubbeado (WebTestClient) y claves RS256 de test en `src/test/resources`.

## Dependency Graph

```
build-parent (T1)  ── versiones + QA heredados (raíz del reactor)
├── config-server (T2) :8888 ────────────┐
└── discovery-server (T4) :8761 ─────┐   │
                                     │   │
        auth-service  (T5 → T6) ◄────┼───┘   H2/seed + JWT RS256
        demo-service  (T7)      ◄────┼───┘   hello + registro
                                     │
        gateway-server (T8 → T9 → T10) ◄┘    rutas + validación JWT + swagger
                │
                └── T11: README + 7 criterios de éxito (cierre)
```

- **Build (reactor):** parent → [`config`, `discovery`] → [`auth`, `demo`] → `gateway`; `./mvnw -B verify` lo respeta solo.
- **Runtime:** config → eureka → auth/demo → gateway; los checkpoints lo respetan.
- **Único acoplamiento entre módulos:** el contrato JWT — T6 firma `sub`/`roles`/`iss`/`exp`, T9 los valida con la pública del config (D14/D21). Un desajuste se resuelve en T9 salvo decisión explícita.

## Vertical Slicing

Cada tarea deja un camino completo y comprobable (nada de construir por capas):

| Tarea | Camino completo que queda verde |
|---|---|
| T2 | El config "por código" sirve properties por HTTP (servidor + repo nativo + test) |
| T3 | `verify` falla ante cualquier violación de formato/estilo/seguridad (RED/GREEN sobre config-server) |
| T4 | Descubrimiento operativo: Eureka solo, arranca en frío sin nada más |
| T5 | `auth` arranca con el usuario `demo` sembrado en H2 y degrada limpio sin config server |
| T6 | `POST /api/auth/token` devuelve un JWT RS256 verificable con TTL del config |
| T7 | `GET /api/demo/hello` → `Hello world!` + registro en Eureka |
| T8 | El gateway enruta a servicios de puerto aleatorio descubiertos por nombre |
| T9 | Camino crítico protegido: 401 sin/bad token → 200 con token (E2E dentro de `verify`) |
| T10 | Todo el flujo probable desde el navegador (Swagger agregado, sin Postman) |
| T11 | Otra persona puede arrancar y probar el sistema siguiendo el README |

## Task List

> Detalle de cada tarea (criterios, verificación, ficheros) en [`todo.md`](todo.md).

### Fase 1: Cimientos
- [x] **T1**: `server/pom.xml` parent + `lombok.config` + Maven Wrapper → *detalle en `todo.md`*
- [x] **T2**: `config-server` nativo (8888) + config común del repo nativo
- [x] **T3**: QA en el build (Spotless + Checkstyle + SpotBugs/FindSecBugs + JaCoCo), validado con `config-server`

### Fase 2: Descubrimiento + Auth
- [x] **T4**: `discovery-server` Eureka (8761, 1 nodo)

### Checkpoint 1 (tras T1–T4) — *pospuesto hasta aquí a propósito: arranca config+eureka juntos, sin dos visitas*
- [x] `./mvnw -B verify` en verde con QA activo y sin errores de toolchain en JDK 25 (verificado en T3)
- [x] `config-server` (8888) sirve `/{application}/{profile}` y `discovery-server` (8761) sirve su dashboard *(con el humano)*
- [x] Revisión/pausa con el humano antes de continuar con T5 *(hecha; banner autopreservación Eureka: dejarlo, ver `todo.md` T4)*

- [x] **T5**: `auth-service`: esqueleto web + config client + Eureka + H2/JPA con `schema.sql`/`data.sql`
- [x] **T6**: `auth-service`: emisión JWT RS256 (`POST /api/auth/token`), TTL por config server, tests unit + web

### Checkpoint 2 (tras T5–T6)
- [x] Eureka visible en 8761 y `auth-service` registrado con puerto aleatorio *(con el humano: AUTH-SERVICE UP en 64107)*
- [x] `POST http://localhost:<puerto-auth>/api/auth/token` con `demo`/`demo` devuelve JWT con `exp` acorde a config *(con el humano vía Swagger UI del propio servicio: 200 + token con exp−iat=3600; password malo → 401 + ErrorResponse)*
- [x] `./mvnw -B verify` en verde (BUILD SUCCESS, exit 0)

### Fase 3: Demo + Gateway
- [x] **T7**: `demo-service`: `GET /api/demo/hello` → `"Hello world!"` + Eureka + config client + springdoc
- [x] **T8**: `gateway-server`: rutas vía Eureka (auth/demo + bucle `/v3/api-docs/**`) con config desde config server
- [ ] **T9**: `gateway-server`: validación JWT RS256 (pública desde config) + whitelist (D20) + tests E2E (sin token 401 / token válido 200 / clave incorrecta 401)

### Checkpoint 3 (tras T7–T9)
- [ ] Flujo completo por el gateway: token → `GET /api/demo/hello` → `Hello world!`; sin token → 401
- [ ] `./mvnw -B verify` en verde (incluye E2E de la cadena)

### Fase 4: Swagger agregado y cierre
- [ ] **T10**: Swagger UI agregado en el gateway (`http://localhost:8080/swagger-ui.html`) con docs de auth y demo
- [ ] **T11**: `server/README.md` (orden de arranque, comandos, flujo Swagger), `./mvnw -B verify` global y recorrido manual de los 7 criterios de éxito de la spec

### Checkpoint final
- [ ] Los 7 criterios de éxito de SPEC.md verificados uno a uno
- [ ] `webapp/` intacto
- [ ] Revisión con el humano

## Risks and Mitigations

| Riesgo | Impacto | Mitigación |
|---|---|---|
| google-java-format (Spotless) no corre en el JVM de la build (JDK 25) | Medio | **Materializado en T3**: gjf 1.37.0 rompió la glue de Spotless 3.10.4 (`Style` enum→record, sin `valueOf`) → pin a **1.36.1** (default validado por el plugin). Fallback a `palantir-java-format` no hizo falta. `server/.mvn/jvm.config` con los `--add-exports` de `jdk.compiler` queda como red de seguridad (hoy opcional: Spotless abre módulos vía `Unsafe`) |
| SpotBugs/FindSecBugs sobre JDK 25 | Medio | Pin 4.10.5.0 (ASM moderno); compilamos a bytecode 21; ajustar exclusiones puntuales documentadas |
| springdoc 2.9.1 no agrega rutas del gateway automáticamente | Bajo | Fallback `springdoc.swagger-ui.urls[]` (documentado v2); mismas rutas, sin retrabajo |
| Namespace `spring.cloud.gateway.*` (2025.0.x) vs `.server.webflux` (demo Boot 4) | Bajo | Verificar arranque en T8; la doc de Spring Cloud 4.3.x es la referencia |
| H2/JPA vs `schema.sql`/`data.sql` | Bajo | Orden nativo Boot: SQL init antes de JPA; `ddl-auto=none`; verificado en T5/T6 |
| Tests en frío requieren config server/Eureka caídos | Bajo | Profiles `test` con config/Eureka desactivados desde T5; E2E con stub |
| Descarga del wrapper/deps en primera build | Bajo | Red verificada hoy; wrapper pinneado a 3.9.16 |
