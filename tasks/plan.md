# Implementation Plan: Spring microservices skeleton (`server/`)

> Source of truth: [`../SPEC.md`](../SPEC.md) (approved, 22 closed decisions). This plan defines **how**, the spec defines **what**.

## Overview

Build under `server/` a Maven multi-module project with a parent (`build-parent`) that pins every version and five modules: `config-server` (native, 8888), `discovery-server` (Eureka, 8761), `auth-service` (H2 + RS256 JWT, random port), `demo-service` (`Hello world!`, random port) and `gateway-server` (8080, single entry point, validates JWT and serves aggregated Swagger UI). Build with full QA (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo) and `--release 21` compiled with the installed JDK 25.

## Architecture Decisions (by the plan, on top of the spec)

- **Versions pinned today (Maven Central / official docs, Oct 2026)** — verified, not assumed:

  | Component | Version | Source |
  |---|---|---|
  | Spring Boot | **3.5.16** (LTS; docs: "compatible with versions up to and including Java 25" ✓ JDK 25 OK) | docs.spring.io/spring-boot/3.5 |
  | Spring Cloud | **2025.0.3** (latest Boot 3.5 train; 2025.1.x/2026.x target Boot 4) | Maven Central |
  | springdoc-openapi | **2.9.1** (Boot 3.x line; swagger-ui 5.32.14 with XSS fix) | springdoc.org/v2 |
  | Lombok | **1.18.48** (forced via `lombok.version`) | Maven Central |
  | JaCoCo | **0.8.15** | Maven Central |
  | Spotless plugin | **3.10.4** (+ google-java-format **1.36.1**; gjf ≥1.37.0 breaks the plugin glue when `Style` moves from enum to record) | Maven Central; root cause verified with `javap` in T3 |
  | SpotBugs plugin | **4.10.5.0** (+FindSecBugs) | Maven Central |
  | Checkstyle | plugin **3.6.0** + tool **14.3.0** | Maven Central |
  | Maven Wrapper | **3.9.16** (latest stable 3.9.x; 4.0 is RC-only) | Maven Central |

- **Config client without `bootstrap.yml`** — technical refinement of D5 (flag for the user): the standard pattern since Spring Cloud 2020.x is `spring-cloud-starter-config` + `spring.config.import: optional:configserver:http://localhost:8888` in each service's `application.yml` (exactly what the official `springdoc-openapi-demos/demo-microservices` does, verified). Each service carries its local `spring.application.name` and everything else travels from the config server. *(If you prefer classic `bootstrap.yml`, it can be switched before starting.)*
- **RS256 keys** (D14/D21): pair generated with a single-file Java tool on JDK 25 (`keytool`/single-file program → PKCS#8 + X.509 PEMs). Private key only in `config-server/src/main/resources/config/auth-service.yml`, public key in `gateway-server.yml`. Marked DEV-ONLY.
- **Gateway routes** (D6 + D22 aggregation), pattern verified against the official springdoc demo:
  - `POST /api/auth/token` → `lb://auth-service` (the service owns its full path, no rewrite);
  - `/api/demo/**` → `lb://demo-service`;
  - OpenAPI loop: `/v3/api-docs/auth-service` and `/v3/api-docs/demo-service` → `lb://<service>` with `RewritePath` to `/v3/api-docs` (the demo's `openapi` self-loop, here explicit per service);
  - ⚠️ Namespace note: in Spring Cloud **2025.0.x / Gateway 4.3.x** the config namespace is **`spring.cloud.gateway.server.webflux.*`** (the bare `spring.cloud.gateway.*` from the old starter is deprecated) — verified empirically in T8 against the jar metadata; `GatewayRouteDefinitionsTests` guards it.
- **Aggregated swagger at the gateway**: try springdoc 2.9.1's automatic aggregation first (the one the official demo uses); if the UI does not list the services, the documented fallback is `springdoc.swagger-ui.urls[]` (same property in v2 docs) — same route design, no rework.
- **auth-service security**: it has `spring-boot-starter-security` (required for resource-server/crypto) → its own security chain allowing `POST /api/auth/token`, swagger, actuator/health and protecting the rest; the real JWT requirement lives at the gateway (D6).
- **H2** (D16): `jdbc:h2:mem:authdb`, `ddl-auto=none`, `schema.sql` + `data.sql` (INSERT of user `demo` with a precomputed BCrypt hash). Boot runs SQL init **before** JPA → the INSERT works without defer.
- **Cold tests** (no network/services): test configs in `src/test/resources` with `spring.cloud.config.enabled=false` and Eureka disabled; the gateway E2E stubs `demo-service` (WebTestClient) and uses test RS256 keys in `src/test/resources`.

## Dependency Graph

```
build-parent (T1)  ── versions + inherited QA (reactor root)
├── config-server (T2) :8888 ────────────┐
└── discovery-server (T4) :8761 ─────┐   │
                                     │   │
        auth-service  (T5 → T6) ◄────┼───┘   H2/seed + RS256 JWT
        demo-service  (T7)      ◄────┼───┘   hello + registration
                                     │
        gateway-server (T8 → T9 → T10) ◄┘    routes + JWT validation + swagger
                │
                └── T11: README + 7 success criteria (closing)
```

- **Build (reactor):** parent → [`config`, `discovery`] → [`auth`, `demo`] → `gateway`; `./mvnw -B verify` respects it automatically.
- **Runtime:** config → eureka → auth/demo → gateway; the checkpoints respect it.
- **Only coupling between modules:** the JWT contract — T6 signs `sub`/`roles`/`iss`/`exp`, T9 validates them with the public key from the config (D14/D21). A mismatch is resolved in T9 unless explicitly decided.

## Vertical Slicing

Every task leaves a complete, checkable path (no layer-by-layer building):

| Task | Complete path left green |
|---|---|
| T2 | The "as-code" config serves properties over HTTP (server + native repo + test) |
| T3 | `verify` fails on any formatting/style/security violation (RED/GREEN on config-server) |
| T4 | Discovery operational: Eureka alone, boots cold with nothing else |
| T5 | `auth` boots with the `demo` user seeded in H2 and degrades cleanly without the config server |
| T6 | `POST /api/auth/token` returns a verifiable RS256 JWT with the config-driven TTL |
| T7 | `GET /api/demo/hello` → `Hello world!` + Eureka registration |
| T8 | The gateway routes to random-port services discovered by name |
| T9 | Protected critical path: 01 without/bad token → 200 with token (E2E inside `verify`) |
| T10 | The whole flow testable from the browser (aggregated Swagger, no Postman) |
| T11 | Another person can start and test the system following the README |

## Task List

> Per-task detail (criteria, verification, files) in [`todo.md`](todo.md).

### Phase 1: Foundations
- [x] **T1**: `server/pom.xml` parent + `lombok.config` + Maven Wrapper → *detail in `todo.md`*
- [x] **T2**: native `config-server` (8888) + shared native-repo config
- [x] **T3**: QA in the build (Spotless + Checkstyle + SpotBugs/FindSecBugs + JaCoCo), validated with `config-server`

### Phase 2: Discovery + Auth
- [x] **T4**: `discovery-server` Eureka (8761, single node)

### Checkpoint 1 (after T1–T4) — *deliberately postponed until here: starts config+eureka together, no double visit*
- [x] `./mvnw -B verify` green with QA active and no JDK 25 toolchain errors (verified in T3)
- [x] `config-server` (8888) serves `/{application}/{profile}` and `discovery-server` (8761) serves its dashboard *(with the human)*
- [x] Review/pause with the human before continuing to T5 *(done; Eureka self-preservation banner: keep it, see `todo.md` T4)*

- [x] **T5**: `auth-service`: web skeleton + config client + Eureka + H2/JPA with `schema.sql`/`data.sql`
- [x] **T6**: `auth-service`: RS256 JWT issuing (`POST /api/auth/token`), TTL via config server, unit + web tests

### Checkpoint 2 (after T5–T6)
- [x] Eureka visible on 8761 with `auth-service` registered on a random port *(with the human: AUTH-SERVICE UP on 64107)*
- [x] `POST http://localhost:<auth-port>/api/auth/token` with `demo`/`demo` returns a JWT whose `exp` matches the config *(with the human via the service's own Swagger UI: 200 + token with exp−iat=3600; bad password → 401 + ErrorResponse)*
- [x] `./mvnw -B verify` green (BUILD SUCCESS, exit 0)

### Phase 3: Demo + Gateway
- [x] **T7**: `demo-service`: `GET /api/demo/hello` → `"Hello world!"` + Eureka + config client + springdoc
- [x] **T8**: `gateway-server`: routes via Eureka (auth/demo + `/v3/api-docs/**` loop) with config from the config server
- [ ] **T9**: `gateway-server`: RS256 JWT validation (public key from config) + whitelist (D20) + E2E tests (no token 401 / valid token 200 / wrong key 401)

### Checkpoint 3 (after T7–T9)
- [ ] Full flow through the gateway: token → `GET /api/demo/hello` → `Hello world!`; no token → 401
- [ ] `./mvnw -B verify` green (including the chain E2E)

### Phase 4: Aggregated Swagger and closing
- [ ] **T10**: Aggregated Swagger UI at the gateway (`http://localhost:8080/swagger-ui.html`) with auth and demo docs
- [ ] **T11**: `server/README.md` (startup order, commands, Swagger flow), global `./mvnw -B verify` and a manual walkthrough of the spec's 7 success criteria

### Final checkpoint
- [ ] The 7 success criteria of SPEC.md verified one by one
- [ ] `webapp/` untouched
- [ ] Review with the human

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| google-java-format (Spotless) fails on the build JVM (JDK 25) | Medium | **Materialized in T3**: gjf 1.37.0 broke Spotless 3.10.4's glue (`Style` enum→record, no `valueOf`) → pin **1.36.1** (the plugin's validated default). The `palantir-java-format` fallback was not needed. `server/.mvn/jvm.config` with the `jdk.compiler` `--add-exports` stays as a safety net (optional today: Spotless opens the modules via `Unsafe`) |
| SpotBugs/FindSecBugs on JDK 25 | Medium | Pin 4.10.5.0 (modern ASM); we compile to bytecode 21; document any targeted exclusions |
| springdoc 2.9.1 does not aggregate gateway routes automatically | Low | Fallback `springdoc.swagger-ui.urls[]` (documented in v2); same routes, no rework |
| `spring.cloud.gateway.*` (2025.0.x) vs `.server.webflux` namespace | Low | **Resolved in T8**: verified empirically against the 4.3.5 jar metadata; the correct namespace is `spring.cloud.gateway.server.webflux.*` and the test `GatewayRouteDefinitionsTests` guards it |
| H2/JPA vs `schema.sql`/`data.sql` | Low | Native Boot order: SQL init before JPA; `ddl-auto=none`; verified in T5/T6 |
| Cold tests need the config server/Eureka down | Low | Test configs with config/Eureka disabled since T5; E2E with a stub |
| Wrapper/deps download on first build | Low | Network verified today; wrapper pinned to 3.9.16 |
