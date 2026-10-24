# Spec: Spring microservices backend skeleton (`server/`)

Status: **APROVED (SPECIFY closed) — in PLAN/IMPLEMENT phase.** Plan in `tasks/plan.md`, tasks in `tasks/todo.md`. No open questions.
User rule: **assume nothing** — delegated decisions (Q7, Q12) are marked as such; everything else comes from explicit answers.

---

## Capability Map (Phase 0)

| Module id | Responsibility | Depends on |
|---|---|---|
| `build-parent` | `server/pom.xml` (packaging `pom`): the **only place** that pins versions (Boot BOM + Spring Cloud + Lombok + springdoc + plugins) | — |
| `config-server` | Spring Cloud Config Server, **native** backend ("config as code" in this same repo) | `build-parent` |
| `discovery-server` | Eureka Server (1 node) | `build-parent` |
| `auth-service` | **In-memory H2** (JPA) with a SQL-seeded user; **POST** endpoint that signs and issues **RS256** JWTs with a TTL configurable via config server | `build-parent`, `config-server` |
| `demo-service` | GET returning `"Hello world!"` | `build-parent`, `config-server`, `discovery-server` |
| `gateway-server` | Spring Cloud Gateway: single entry point; **verifies the JWT on every route** (except the whitelist), routes via Eureka and **serves aggregated Swagger UI** | `build-parent`, `config-server`, `discovery-server`, `auth-service` (JWT contract) |

Build order: `build-parent` → [`config-server`, `discovery-server`] → [`auth-service`, `demo-service`] → `gateway-server`

## Decisions (all closed)

| # | Decision | Origin |
|---|---|---|
| D1 | Spring Boot **3.5.x (LTS)** + Spring Cloud **2025.0.x (Northfields)** — the only official GA train with Boot 3.5 that ships netflix+gateway+config (official matrix verified) | User |
| D2 | groupId **`com.demo`**, packages `com.demo.<module>`, layers `adapter.in.web` / `application.service` / `domain` / `adapter.out` | User |
| D3 | Ports: fixed **config 8888, eureka 8761, gateway 8080**; **random** (`server.port=0`) on `auth-service` and `demo-service` (they are discovered via Eureka; the client only sees 8080) | User + evaluation |
| D4 | **Native** Config Server: everything as code in this same repo, no config git repo and no config scattered across services | User |
| D5 | Services **do** read their config from the Config Server: `spring.config.import: optional:configserver:http://localhost:8888` + `spring.application.name` in their local `application.yml` (current standard pattern, verified against the official springdoc demo; **no `bootstrap.yml`** — a plan refinement over the original draft). Eureka URLs, RS256 keys and the JWT TTL travel this way | Accepted recommendation + verified at plan phase |
| D6 | All business traffic **through the gateway**: `POST /api/auth/token`, `GET /api/demo/hello` → `lb://...`. No JWT → 401 | User |
| D7 | Tests with **Mockito** | User |
| D8 | **Lombok 1.18.48** forced (`lombok.version` in the parent; JDK 25 needs ≥1.18.40). `lombok.config` in `server/` | User + verified |
| D9 | **Maven Wrapper: yes** (`mvn -N wrapper:wrapper`, versioned) | Accepted recommendation |
| D10 | Build QA: **Spotless + SpotBugs(FindSecBugs) + Checkstyle** — "everything possible to improve overall quality" | User (Q2) |
| D11 | **JaCoCo** with a report in `verify`; coverage threshold to be defined later | Accepted recommendation |
| D12 | **Solitary Eureka** (1 node) | Accepted recommendation |
| D13 | Compile with **`--release 21`** using the installed JDK 25 (installs nothing; only pins bytecode/APIs to Java 21, the standard LTS alongside Boot 3.5) | Delegated to the assistant (Q7) → **21** |
| D14 | JWT **RS256**: private key only in `auth-service`, public key at the gateway | User (D14) |
| D15 | JWT library: **nimbus via `spring-security-oauth2-resource-server`/`oauth2-jose`** (the standard Spring Security stack) | User |
| D16 | `auth-service`: **in-memory H2** + **Spring Data JPA** + `schema.sql` + `data.sql` with a direct INSERT of the **fixed user `demo`/`demo`** hashed with **BCrypt** | User (Q13) |
| D17 | **Swagger UI via springdoc-openapi 2.9.x** (the Boot 3.5 line, verified): per-service UI and **aggregated at the gateway** → `http://localhost:8080/swagger-ui.html`. No Postman | User |
| D18 | `auth-service`/`demo-service` = **spring-boot-starter-web** (servlet); `gateway-server` = **WebFlux** (reactive, native to spring-cloud-gateway) | Verified technical consequence |
| D19 | **JWT claims**: `sub` (user), `roles`, `iss` (`auth-service`), `exp` = **`app.jwt.expiration-seconds`**, a property read from the config server (`auth-service.yml`, default 3600). Changing the TTL = edit the file + restart `auth-service`, **no recompile** | User (Q6: asked for a config-server-driven TTL) |
| D20 | **Gateway whitelist (no JWT)**: `POST /api/auth/token`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`. Everything else → 401 | User (Q5: "the ones you mention") |
| D21 | **Fixed RS256 keys in the native config**: a pair in the config repo (private → `auth-service.yml`, public → `gateway-server.yml`). JWKS / real rotation discarded (clashes with random ports; Vault = out of scope) | User (Q11) |
| D22 | Swagger **public via the gateway** and aggregated with Eureka discovery — the option that "actually works" with random ports: services are located by name, not by port. **Config verified at plan phase** against the official `springdoc-openapi-demos/demo-microservices`: routes `/{svc}/** → lb://` + loop `/v3/api-docs/{svc} → /v3/api-docs` + springdoc webflux-ui at the gateway; documented fallback `springdoc.swagger-ui.urls[]` if 2.9.1's automatic aggregation falls short | Delegated to the assistant (Q12) ✓ verified |

## Objective

A skeleton under `.\server\` as close as possible to a real enterprise application: Spring Boot 3.5 LTS + Config/Eureka/Gateway, an `auth-service` on H2 issuing RS256 JWTs (TTL configurable via config server), a sample `demo-service`, full build QA and aggregated Swagger UI at the gateway to test without Postman.

Target flow:
1. Open `http://localhost:8080/swagger-ui.html`.
2. `POST /api/auth/token` with `demo`/`demo` → JWT (expiry per config).
3. `GET /api/demo/hello` with `Authorization: Bearer <jwt>` → `Hello world!`.
4. Same call without a token → `401`.

Success criteria:
1. Clean `./mvnw -B verify` from cold (JDK 25 + network): compiles, tests, formats and analyzes.
2. Startup order config → eureka → auth/demo → gateway; the random-port services appear in Eureka.
3. Full JWT flow via the gateway (steps 1–4), also covered by an E2E test (401 without/bad token, 200 with token).
4. Changing `app.jwt.expiration-seconds` in the native `auth-service.yml` and restarting `auth-service` alters the TTL without recompiling.
5. Zero `<version>` in child-module dependencies; versions only in `build-parent`.
6. Swagger UI reachable through the gateway and endpoints testable from the browser.
7. `webapp/` untouched.

## Tech Stack

**Machine (verified):** Amazon Corretto JDK 25.0.4.1 (`C:\dev\tools\Amazon Corretto\jdk25.0.4_10`), Maven 3.10.0 (`C:\dev\tools\apache-maven-3.10.0`), Windows 11.

**Key versions (pinned at plan phase from Maven Central / official docs, Oct 2026):**
- Spring Boot **3.5.16** (LTS; official docs: "compatible with versions up to and including Java 25" → the installed JDK 25 is valid) + Spring Cloud **2025.0.3** (latest Boot 3.5 train → netflix/gateway/config 4.3.x).
- Lombok **1.18.48** · springdoc-openapi **2.9.1** (swagger-ui 5.32.14) · H2 (managed by Boot) · JaCoCo **0.8.15** · Spotless **3.10.4** · SpotBugs **4.10.5.0** (+FindSecBugs) · Checkstyle plugin **3.6.0** + tool **14.3.0** · Maven Wrapper **3.9.16**.

**Dependencies per module** (all with no `<version>` in children):

| Module | Main dependencies |
|---|---|
| `config-server` | `spring-cloud-config-server` |
| `discovery-server` | `spring-cloud-starter-netflix-eureka-server` |
| `auth-service` | `spring-boot-starter-web`, `-data-jpa`, `h2`, `spring-security-crypto`, `spring-boot-starter-security`, `oauth2-resource-server`, `springdoc-openapi-starter-webmvc-ui`, `actuator`, `lombok`(provided) |
| `demo-service` | `spring-boot-starter-web`, `spring-cloud-starter-netflix-eureka-client`, `springdoc-openapi-starter-webmvc-ui`, `actuator`, `lombok`(provided) |
| `gateway-server` | `spring-cloud-starter-gateway-server-webflux` (official starter rename in 4.3.x — the old `spring-cloud-starter-gateway` is deprecated and warns on every start; human decision in T8), `spring-cloud-starter-netflix-eureka-client`, `oauth2-resource-server`, `springdoc-openapi-starter-webflux-ui`, `actuator`, `lombok`(provided) |
| all (test) | `spring-boot-starter-test` (includes Mockito) |

## Commands

Everything from `server/`:

```powershell
./mvnw -B verify                          # build + tests + formatting + analysis (the main check)
./mvnw -B verify -pl demo-service -am     # a concrete module (+ dependencies)
./mvnw spotless:apply                     # format by hand
./mvnw -B spring-boot:run -pl config-server     # 8888
./mvnw -B spring-boot:run -pl discovery-server  # 8761
./mvnw -B spring-boot:run -pl auth-service      # random port (see the log)
./mvnw -B spring-boot:run -pl demo-service      # random port (see the log)
./mvnw -B spring-boot:run -pl gateway-server    # 8080
mvn -N wrapper:wrapper                    # generate the wrapper (once; it is versioned)
```

Test without Postman: browser → `http://localhost:8080/swagger-ui.html`.
CLI alternative:

```powershell
$jwt = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/token -Body '{"username":"demo","password":"demo"}' -ContentType 'application/json').token
Invoke-RestMethod -Uri http://localhost:8080/api/demo/hello -Headers @{ Authorization = "Bearer $jwt" }  # "Hello world!"
Invoke-WebRequest -Uri http://localhost:8080/api/demo/hello   # expected 401
```

## Project Structure

```
astro-java-test/
├── SPEC.md              → this document
├── webapp/              → UNTOUCHABLE (Astro app)
└── server/
    ├── pom.xml          → build-parent (single source of versions)
    ├── mvnw, mvnw.cmd, .mvn/   → versioned wrapper
    ├── lombok.config    → shared Lombok config
    ├── config-server/
    │   └── src/main/resources/config/    → NATIVE configuration repository
    │       ├── application.yml          (shared: Eureka)
    │       ├── auth-service.yml         (RS256 pair + app.jwt.expiration-seconds + H2)
    │       ├── demo-service.yml
    │       └── gateway-server.yml       (routes; public key in T9)
    ├── discovery-server/
    ├── auth-service/
    │   └── src/main/resources/          → application.yml (name + spring.config.import),
    │                                      schema.sql, data.sql (INSERT demo/BCrypt)
    ├── demo-service/
    └── gateway-server/
```

Tests: `src/test/java/com/demo/<module>/` mirroring the main package.

## Code Style

```java
package com.demo.authservice.adapter.in.web;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
public record TokenResponse(String token, long expiresIn) {}
```

```java
package com.demo.authservice.application.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public TokenResponse issue(String username, String rawPassword) {
        // validates against H2 (user seeded in data.sql with a BCrypt hash)
        // signs RS256 with the private key; expires per app.jwt.expiration-seconds (config server)
        return TokenResponse.builder().token("...").expiresIn(3600).build();
    }
}
```

Lombok rules (builders included in the learning curve):
- **Immutables first**: DTOs = `record`. Lombok only where a record does not fit.
- **`@Builder` + `@Jacksonized`** for DTOs Jackson deserializes; `@Builder.Default` mandatory for defaults; validation in the compact constructor.
- **Avoid `@Data` on domain**; `@Value`/`@Getter`+`@Builder` for immutables.
- `lombok.config` at `server/` root: `lombok.addLombokGeneratedAnnotation = true`.
- Constructor injection (`private final`); `application.yml` in kebab-case; names per layer.

## Testing Strategy

- **Unit (Mockito):** JWT signing/claims/TTL (test private key), validation in the gateway's `JwtDecoder`, authentication against H2.
- **Web slice:** `@WebMvcTest` + MockMvc for `TokenController` and `HelloController`.
- **Context load:** `@SpringBootTest` per module (test profile: config server and Eureka registration disabled → passes from cold).
- **Chain E2E (mandatory, D6):** gateway with a stubbed `demo-service`: no token → 401; valid RS256 token → 200 `Hello world!`; token signed with the wrong key → 401.
- H2 in tests: `data.sql` also runs in the test context (it verifies the INSERT).
- Command `./mvnw -B verify` (JaCoCo produces the report). No Docker and no external services.

## Boundaries

- **Always:**
  - The whole backend inside `server/`; never touch `webapp/`.
  - Versions ONLY in `build-parent`; children with no `<version>` on managed dependencies.
  - `./mvnw -B verify` green before calling a task done.
  - Startup order: config → eureka → auth/demo → gateway.
  - Every business endpoint requires a JWT behind the gateway; whitelist only: token, swagger, health (D20).
- **Ask first:**
  - Adding dependencies/plugins not listed.
  - Changing fixed ports (8888/8761/8080) or the random-port policy.
  - Persistence beyond the auth's in-memory H2.
  - Standing up SonarQube or any other infrastructure.
  - Any change under `webapp/`.
- **Never:**
  - Expose the RS256 private key outside `auth-service`/native DEV config (all cryptographic material in this repo is DEV-ONLY, documented).
  - Declare `<version>` on dependencies managed by the parent.
  - Delete/disable broken tests without approval.

## Glossary

- **Maven Wrapper (`mvnw`)**: versioned scripts that use an exact Maven version on any machine. → D9.
- **Formatting (Spotless)**: canonical code style; `verify` fails if violated, `spotless:apply` fixes it. → D10.
- **Coverage (JaCoCo)**: % of lines exercised by tests (HTML report). Measures "how much the tests touch", not quality. → D11.
- **Solitary Eureka**: 1 instance instead of an HA cluster. → D12.
- **`--release 21`**: installs nothing; with your JDK 25 it pins bytecode/APIs to Java 21 (the standard LTS alongside Boot 3.5). → D13.
- **`sub`/`iss`/`exp`**: *subject* = who the token is issued to (the user), *issuer* = who issued it (`auth-service`), *expiration* = expiry (configurable via config server, D19).
- **HS256 vs RS256**: HS256 = shared secret (whoever validates can also sign); RS256 = key pair (only `auth-service` signs). → D14.
- **SonarLint vs SonarQube**: SonarLint = IDE plugin, local; SonarQube = server with a DB and history (infra, out of scope). → D10.
- **springdoc-openapi**: OpenAPI 3 + Swagger UI standard for Spring; the 2.9.x line for Boot 3.5 (verified).

## Open Questions

**None.** Explicit user delegations: Q7 (`--release 21`) and Q12 (public aggregated swagger at the gateway, the option that works with random ports). Everything else came as an explicit answer (Q2, Q5, Q6, Q11, Q13).

---

*Spec-driven-development: SPECIFY phase ✓ (approved) → PLAN ✓ (tasks/plan.md) → TASKS ✓ (tasks/todo.md) → IMPLEMENT in progress.*

*Language policy: conversation with the user in Spanish; all repository artifacts (code, docs, commits) in English.*
