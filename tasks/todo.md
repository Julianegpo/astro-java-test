# Task List: Spring microservices skeleton (`server/`)

> Implementation detail; global vision and risks in [`plan.md`](plan.md). Source of truth: [`../SPEC.md`](../SPEC.md).
> Golden rules: versions ONLY in `build-parent`; `webapp/` untouched; `./mvnw -B verify` green before marking any task done.

---

## Task 1: `build-parent` + `lombok.config` + Maven Wrapper

**Description:** Create `server/pom.xml` with packaging `pom` as the single source of versions: spring-boot-starter-parent 3.5.16 parent, Spring Cloud 2025.0.3 BOM, springdoc 2.9.1 BOM, `lombok.version=1.18.48`, `<java.version>21` (compiled with the installed JDK 25), empty `<modules>` for now and `pluginManagement` with maven-compiler (`release 21`) and surefire. Add `lombok.config` (`lombok.addLombokGeneratedAnnotation = true`) and generate the Maven Wrapper with `-Dmaven=3.9.16`.

**Acceptance criteria:**
- [x] `server/pom.xml` with `com.demo:build-parent:0.1.0-SNAPSHOT`, `java.version=21`, Lombok/springdoc/Cloud versions pinned; no stray unnecessary `<version>`
- [x] `mvnw`, `mvnw.cmd` and `.mvn/wrapper/` generated and versioned (wrapper 3.9.16 — `mvnw -v` confirms Maven 3.9.16 + JDK 25.0.4.1)
- [x] `lombok.config` in `server/` with `addLombokGeneratedAnnotation`

**Verification:**
- [x] Build: `./mvnw -B verify` from `server/` (parent only, green — BUILD SUCCESS)
- [x] Manual: `./mvnw -v` uses the wrapper (3.9.16) with no global Maven

**Dependencies:** none
**Files likely touched:** `server/pom.xml`, `server/lombok.config`, `server/mvnw`, `server/mvnw.cmd`, `server/.mvn/wrapper/*`
**Estimated scope:** S (3-5 config files)

---

## Task 2: Native `config-server` (8888) + native config repository

**Description:** `config-server` module with `spring-cloud-config-server`, active `native` profile, port 8888, `search-locations: classpath:/config`, and the main class annotated `@EnableConfigServer`. Create `src/main/resources/config/application.yml` (shared: `eureka.client.service-url.defaultZone`, `eureka.instance.prefer-ip-address`). No Eureka dependency (it starts first; D5/startup order). The per-service `<service>.yml` files are created in each service's task (T5/T6, T8).

**Acceptance criteria:**
- [x] `ConfigServerApplication` boots on 8888 with the native profile (test context started with the `native` profile ✓)
- [ ] `GET http://localhost:8888/application/default` returns JSON with the shared properties → *manual, deferred to Checkpoint 1 alongside Eureka (avoids starting twice)*
- [x] `@SpringBootTest` context test passes with the test `spring.cloud.config.server.native.search-locations`

**Verification:**
- [x] Tests: `./mvnw -B verify -pl config-server -am` (BUILD SUCCESS, 1 test OK)
- [ ] Manual: start + `Invoke-RestMethod http://localhost:8888/application/default` → *Checkpoint 1*

**Dependencies:** T1
**Files likely touched:** `server/config-server/pom.xml`, `.../ConfigServerApplication.java`, `.../application.yml`, `.../resources/config/application.yml`, `src/test/java/.../ConfigServerApplicationTests.java`
**Estimated scope:** S (4-6 files)

---

## Task 3: QA in the build (Spotless + Checkstyle + SpotBugs/FindSecBugs + JaCoCo)

**Description:** In the parent: `pluginManagement` + `<plugins>` (inherited by all children) with Spotless 3.10.4 (`google-java-format` **pinned to 1.36.1**, `spotless:check` in `verify`), maven-checkstyle-plugin 3.6.0 with tool checkstyle 14.3.0 and sanitized own rules (`config/checkstyle.xml`: short set — tabs, EOF, LineLength 120, UnusedImports, NeedBraces, EqualsHashCode, ...), spotbugs-maven-plugin 4.10.5.0 with FindSecBugs 1.14.0 (`failOnError`, effort Max, threshold Medium), and JaCoCo 0.8.15 (`prepare-agent` + `report` in `verify`, no gate). JDK 25 toolchain fail-fast worked around.

**Finding (recorded decision):** gjf ≥ 1.37.0 broke binary compatibility: `JavaFormatterOptions$Style` moved from enum to record and dropped `valueOf(String)`, which Spotless 3.10.4's glue invokes → `NoSuchMethodError` wrapped in `InvocationTargetException` (verified with `javap` on both versions). Solution: pin gjf to **1.36.1** (the plugin's own validated default). The fallback to palantir-java-format was not needed. Additionally `server/.mvn/jvm.config` exports `jdk.compiler/com.sun.tools.javac.*` to the build JVM: optional today (Spotless opens the modules via `Unsafe`, with a deprecation warning on JDK 25) but kept as a safety net for when the JDK removes that `Unsafe`.

**Acceptance criteria:**
- [x] The 4 plugins run in `./mvnw -B verify` with no JDK 25 errors (gjf pinned to 1.36.1; no fallback)
- [x] Sanitized rules: 0 Checkstyle violations and 0 SpotBugs bugs on the current code, FindSecBugs active
- [x] Reports generated: `target/site/jacoco/` (index.html + jacoco.xml), `spotbugsXml.xml`, `checkstyle-result.xml`

**Verification:**
- [x] Build: global `./mvnw -B verify` and `-pl config-server -am` (BUILD SUCCESS, `LASTEXITCODE=0`)
- [x] Manual RED/GREEN: break formatting → `verify` fails at `spotless:check`; `./mvnw spotless:apply` fixes it and returns to green

**Dependencies:** T2 (needs a real module to validate)
**Files likely touched:** `server/pom.xml`, `server/config/checkstyle.xml`, possible `spotbugs-exclude.xml`, `server/config-server` (formatting)
**Estimated scope:** M (3-5 files + rule iteration)

---

## Task 4: `discovery-server` Eureka (8761, 1 node)

**Description:** `discovery-server` module with `spring-cloud-starter-netflix-eureka-server`, port 8761, main class `@EnableEurekaServer`, self-registration disabled (`register-with-eureka: false`, `fetch-registry: false`, D12 solitary). No config server (independent to avoid startup cycles).

**Acceptance criteria:**
- [x] Boots on 8761 and serves the Eureka dashboard *(test context: "Started Eureka Server"; browser → Checkpoint 1)*
- [x] It does not register itself nor contact the config server *("Client configured to neither register nor query for data"; no config dependency in the pom)*
- [x] Context test passes cold (no network)

**Verification:**
- [x] Tests: `./mvnw -B verify -pl discovery-server -am` (RED→GREEN: no main class → `IllegalStateException`; with it → 1 test OK, Eureka starts in the context)
- [ ] Manual: browser `http://localhost:8761/` → *Checkpoint 1 (together with config-server)*

**Minor finding:** new files with LF line endings → Spotless requires CRLF (like the rest of the repo); `./mvnw spotless:apply` fixes them. Flow already documented in T3, nothing new.
**Decision (human):** the "EMERGENCY! EUREKA MAY BE INCORRECTLY..." banner on the dashboard = self-preservation mode, expected in dev (0 services → 0 renewals below the solitary node's minimum threshold of 1). **Left enabled**: it self-heals once auth/demo register (T5+, each renews twice per minute). Do not touch `eureka.server.enable-self-preservation`.

**Dependencies:** T1
**Files likely touched:** `server/discovery-server/pom.xml`, `.../DiscoveryServerApplication.java`, `.../application.yml`, context test
**Estimated scope:** S

---

## ✅ Checkpoint 1 (after T1–T4)

- [x] Full `./mvnw -B verify` green with QA active (verified in T3)
- [x] `config-server` (8888) and `discovery-server` (8761) boot and respond *(with the human: correct JSON from `/application/default` with the 2 native properties; Eureka dashboard loads)*
- [x] Review/pause with the human before continuing to T5 *(done; recorded decision about the Eureka self-preservation banner, see T4)*

---

## Task 5: `auth-service`: web skeleton + config client + Eureka + H2/JPA with seed

**Description:** `auth-service` module (spring-boot-starter-web, -data-jpa, h2, spring-security-crypto, actuator, eureka-client, spring-cloud-starter-config, springdoc webmvc-ui, lombok provided). Local `application.yml` with `spring.application.name=auth-service` and `spring.config.import: optional:configserver:http://localhost:8888` (D5 refinement). `schema.sql` (`users` table: username PK, password_hash, roles) + `data.sql` with the direct INSERT of the fixed user `demo` (precomputed BCrypt hash). JPA entity/repo (`UserAccount` + `UserAccountRepository extends JpaRepository`). `ddl-auto=none`. Test config in `src/test/resources` (no config server/Eureka). Create `config/auth-service.yml` on the config server with `server.port: 0`.

**Acceptance criteria:**
- [x] Test context green with H2 + `data.sql` executed *(3/3 tests: context + `findByUsername("demo")` with `BCryptPasswordEncoder.matches("demo", …)` + count=1)*
- [x] Boots with the config server up and registers in Eureka on a random port *(`Located environment: name=auth-service`, `jdbc:h2:mem:authdb` remote; Eureka: AUTH-SERVICE UP on a random port, e.g. 56749)*
- [x] Without the config server, degrades cleanly in local mode (`optional:configserver`) with a random port *(WARN "Could not locate PropertySource" with `optional = true` → boots in 2.5 s on a random port e.g. 59493 and registers via the client's Eureka default)*

**Verification:**
- [x] Tests: `./mvnw -B verify -pl auth-service -am` (RED→GREEN: compile without `adapter.out` → 3/3 OK)
- [x] Manual: start config → eureka → auth; verify registration *(REST API `/eureka/apps` shows AUTH-SERVICE UP with a random port; the dashboard for the human was left for Checkpoint 2)*
- [x] Global regression: `./mvnw -B verify` green with the 4 modules (BUILD SUCCESS, exit 0)

**Findings:**
- **Lombok + JDK 25 (toolchain):** `javac` on JDK ≥23 no longer runs the implicit classpath annotation processing → the entity's `@Getter/@Setter` were not generated and the seed test failed on nonexistent methods. Fix in `build-parent` (maven-compiler-plugin `pluginManagement` with `<proc>full</proc>`), needed for any future module with Lombok. Compiling with `--release 21` did not mitigate it.
- **Injection in tests:** a JUnit 5 constructor with a Spring parameter gives `ParameterResolutionException` in this setup → `@Autowired` field injection in tests (the constructor-injection rule applies to production code).
- **Test config = shadow classpath:** instead of the planned `test` profile, `src/test/resources/application.yml` **replaces** the main one on the test classpath (config import and Eureka disabled). Rationale: guaranteed cold determinism even with services running locally; with a `test` profile the remote import could resolve before being disabled.
- **`server.port: 0` also in the local `application.yml`** (not only in the remote config): if the config server is down, the remote config never arrives and the local port decides — that way degraded mode still honors D3.
- **`ddl-auto: none` locally and in the test shadow** (not only remotely): with embedded H2 the default is `create-drop`, which would wipe the `data.sql` seed (SQL init runs before JPA).
- BCrypt hash of `demo` precomputed with a JDK 25 source-launcher against `spring-security-crypto-6.5.11.jar` from m2 (temp, now deletable).
- *Operational note:* `spring-boot:run` forks — killing only the Maven wrapper leaves the app JVM orphaned (had to free 8888 manually); killing by listening port is the reliable kill.

**Dependencies:** T2, T4
**Files likely touched:** `server/auth-service/pom.xml`, `.../application.yml`, `AuthServiceApplication.java`, `adapter/out/UserAccount.java`, `adapter/out/UserAccountRepository.java`, `schema.sql`, `data.sql`, tests, `server/config-server/src/main/resources/config/auth-service.yml`
**Estimated scope:** M (6-8 files)

---

## Task 6: `auth-service`: RS256 JWT issuing with configurable TTL

**Description:** Generate a dev RSA 2048 pair (Java single-file script on JDK 25 → PKCS#8 + X.509 PEMs) and put it in `config/auth-service.yml` (inline private key + `app.jwt.expiration-seconds: 3600`, D19/D21). `TokenService` (application.service) with nimbus (`RSASSASigner`): validates credentials against H2 with `BCryptPasswordEncoder` and signs the JWT with claims `sub`, `roles`, `iss=auth-service`, `exp` from `app.jwt.expiration-seconds`. `TokenController` (adapter.in.web) `POST /api/auth/token` → record `TokenResponse(token, expiresIn)` with `@Builder @Jacksonized`. SecurityFilterChain allowing token/swagger/health. Tests: Mockito unit tests (signing, claims, expiry, invalid credentials), `@WebMvcTest` of the controller, context with test keys in `src/test/resources`.

**Acceptance criteria:**
- [x] `demo`/`demo` → RS256 JWT with `sub=demo`, `iss=auth-service`, `exp` matching `app.jwt.expiration-seconds`; invalid password → 401/400 *(200 + verified token; bad password → 401 + `{"code":401,"message":"Invalid credentials","timestamp":...}`)*
- [x] Changing `app.jwt.expiration-seconds` on the config server + restarting auth changes the TTL without recompiling (spec success criterion 4) *(3600→1800 and back to 3600 by editing only `config/auth-service.yml` + restart; `exp−iat` = 1800 and later 3600, no code touched)*
- [x] Unit + web + context test coverage green; the private key never present outside config/`auth-service` *(auth-service ~91% line coverage; the private key lives only in `config/auth-service.yml`)*

**Verification:**
- [x] Tests: `./mvnw -B verify -pl auth-service -am` (12/12, 0 SpotBugs, 0 checkstyle, spotless clean)
- [x] Global regression: `./mvnw -B verify` → BUILD SUCCESS, exit 0 (4 modules)
- [x] Manual: `demo`/`demo` → 200; signature RS256 **VALID** against the DEV public key (source-launcher `VerifyJwt.java` on `sha256withRSA`); claims `sub=demo`, `iss=auth-service`, `roles=ROLE_USER`, `exp−iat=3600`; TTL reconfigured without recompiling (see AC2)

**Findings:**
- **Lombok + Jacksonized:** the correct key in `lombok.config` is `lombok.jacksonized.jacksonVersion = 2` (verified on projectlombok.org; the variant `lombok.jacksonized.useJackson3` does NOT exist → "unknown key").
- **SpotBugs `CT_CONSTRUCTOR_THROW`** in `TokenService` (throws from the constructor via guards) → `final` class as the canonical mitigation; Mockito's inline-mock-maker mocks it without issue.
- **No `starter-validation`** (not listed in the SPEC table → Boundaries ask-first): blank/null guard in `TokenService` → unified 401 via `GlobalExceptionHandler` with `ErrorResponse(code,message,timestamp)` (own choice, documented).
- **D2 exit port:** `UserAccounts` (interface + nested `Credentials` record) in `application.service`; `JpaUserAccounts` in `adapter.out`.
- **Test key**: disposable TEST pair (private+public) in `src/test/resources/application.yml` (classpath shadow); the public key lets the context test verify the signature.
- **Correct import**: `org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer` (the `...configuration` package does not exist); `spotless:apply` reorders imports — do not edit pre-spotless text.
- **Eureka stale registration**: after restarting a service, the dashboard/API may return the previous instance (old port) for ~30 s → verify the port is actually listening before requesting, or wait for the registry refresh.

**Dependencies:** T5
**Files likely touched:** `.../application/service/TokenService.java`, `.../adapter/in/web/TokenController.java`, `.../adapter/in/web/TokenResponse.java`, `.../adapter/in/web/SecurityConfig.java`, `config/auth-service.yml` (keys), key-gen test, unit/web tests
**Estimated scope:** M (6-8 files)

---

## ✅ Checkpoint 2 (after T5–T6)

- [x] Eureka dashboard shows `auth-service` with a random port *(with the human)*
- [x] The token flow works over the direct port and the TTL is configurable via config *(with the human: demo/demo token via Swagger UI + TTL 3600→1800→3600 without recompiling)*
- [x] `./mvnw -B verify` green

---

## Task 7: `demo-service`: `GET /api/demo/hello` → `"Hello world!"`

**Description:** `demo-service` module (web, eureka-client, spring-cloud-starter-config, springdoc webmvc-ui, actuator, lombok). `HelloController` (adapter.in.web) returning `"Hello world!"` (plain text). `spring.application.name=demo-service` + `spring.config.import`. Create `config/demo-service.yml` on the config server (`server.port: 0`). Tests: controller unit test + `@WebMvcTest` + cold context.

**Acceptance criteria:**
- [x] `GET /api/demo/hello` returns 200 `"Hello world!"`; registered in Eureka on a random port *(`body = [Hello world!]` as plain text from random port 51295; Eureka: DEMO-SERVICE UP on 51295)*
- [x] Swagger UI accessible on the service's random port (`/swagger-ui.html`) *(`/v3/api-docs` with path `/api/demo/hello` + `/swagger-ui.html` respond on the random port)*
- [x] Tests green from cold *(3/3: unit `HelloControllerTests`, `@WebMvcTest HelloControllerWebTests` with text/plain, context `DemoServiceApplicationTests` with no config server nor Eureka)*

**Verification:**
- [x] Tests: `./mvnw -B verify -pl demo-service -am` (RED→GREEN: without `HelloController` → `cannot find symbol`; with it → BUILD SUCCESS, 0 checkstyle, 0 SpotBugs)
- [x] Manual: `Invoke-RestMethod http://localhost:<demo>/api/demo/hello` → `Hello world!`; Eureka registration and swagger-ui verified with config→eureka→demo started in that order (processes later killed by port)
- [x] Global regression: `./mvnw -B verify` green with the 5 modules (BUILD SUCCESS, exit 0)

**Traceability note:** `spring-cloud-starter-config` included per D5 (T5's config-client pattern) even though SPEC.md's dependency table does not list it for demo-service; this task's description in `tasks/todo.md` does plan it.

**Dependencies:** T2, T4
**Files likely touched:** `server/demo-service/pom.xml`, `.../application.yml`, `DemoServiceApplication.java`, `adapter/in/web/HelloController.java`, tests, `config-server/.../config/demo-service.yml`
**Estimated scope:** S

---

## Task 8: `gateway-server`: routes via Eureka + api-docs loop

**Description:** `gateway-server` module (spring-cloud-starter-gateway-server-webflux — **WebFlux**; starter renamed in 4.3.x per the human decision on the T8 finding —, eureka-client, spring-cloud-starter-config, springdoc webflux-ui, actuator, lombok). Routes in `config/gateway-server.yml` (config server, D5): `POST /api/auth/token` → `lb://auth-service`; `/api/demo/**` → `lb://demo-service`; openapi loop `/v3/api-docs/auth-service` and `/v3/api-docs/demo-service` → `lb://<svc>` with `RewritePath` to `/v3/api-docs` (official springdoc demo pattern; the 2025.0.x `spring.cloud.gateway.server.webflux.*` namespace). Create `config/gateway-server.yml` with port 8080 and the Eureka URL.

**Acceptance criteria:**
- [x] Boots on 8080 (reads routes from the config server) and routes to services by name (discovers random ports via Eureka) *(`GET :8080/api/demo/hello` → `Hello world!` via lb://demo-service:60776 and `POST :8080/api/auth/token` → 483-char token via lb://auth-service:58927; the gateway has no local routes: they came from the config server)*
- [x] `GET http://localhost:8080/v3/api-docs/demo-service` returns the demo-service OpenAPI (no security yet) *(OpenAPI 3.1.0 with path `/api/demo/hello`; the auth loop also verified: `/api/auth/token`)*
- [x] Clean boot with no fatal namespace/compatibility warnings *(namespace `server.webflux` with no notices; see findings: remaining NON-fatal deprecation warnings from the starter itself in 4.3.x → human decision pending, later resolved — see findings)*

**Verification:**
- [x] Tests: `./mvnw -B verify -pl gateway-server -am` (2/2: cold context + `GatewayRouteDefinitionsTests` validating that the 4 routes parse from the correct namespace)
- [x] Manual: start config → eureka → auth → demo → gateway and try the 3 URLs *(initial 503 Service Unavailable = Eureka registry cache (~30 s, same family as T6); after the refresh, all 200. GATEWAY-SERVER registered UP:8080)*
- [x] Global regression: `./mvnw -B verify` green with the 6 modules (BUILD SUCCESS, exit 0)

**Findings:**
- **Spring Cloud Gateway 4.3.x namespace verified empirically**: extracted `META-INF/spring-configuration-metadata.json` from the `spring-cloud-gateway-server-4.3.5.jar` in m2 → `spring.cloud.gateway.*` (legacy, deprecated) and **`spring.cloud.gateway.server.webflux.*`** (current) coexist. Routes use the new namespace. `GatewayRouteDefinitionsTests` is the safety net: with a wrong namespace no routes would be registered and the test would fail in `verify` (not at runtime).
- **Starter deprecation in 4.3.x (RESOLVED with the human)**: `spring-cloud-starter-gateway` logs `spring-cloud-starter-gateway is deprecated... use spring-cloud-starter-gateway-server-webflux instead` on every start (and the `spring-cloud-gateway-server` artifact asks for `spring-cloud-gateway-server-webflux`). Not fatal, but Boundaries ask-first (the new starter was not listed in the SPEC) → **human decision: switch** → dependency replaced by `spring-cloud-starter-gateway-server-webflux` in a separate commit and SPEC.md updated. Verified: boot with no warning.
- **Eureka timing on lb://**: requests against `lb://` routes within ~30 s of boot return 503 (the Eureka server's response cache); after the registry refresh, 200. Same phenomenon as T6's stale registration.
- **Routes test**: `RouteDefinitionLocator.getRouteDefinitions()` returns a `Flux` (Reactor), not a `Stream`; and there are several locator beans → autowire `List<RouteDefinitionLocator>` + `Flux.fromIterable(...).flatMap(...)`.
- **Auth predicate**: `Path=/api/auth/token` **+ `Method=POST`** (the SPEC describes POST; with Path alone, GET would also route to auth).

**Dependencies:** T4, T5, T7
**Files likely touched:** `server/gateway-server/pom.xml`, `.../application.yml`, `GatewayServerApplication.java`, `config-server/.../config/gateway-server.yml`, context test
**Estimated scope:** M

---

## Task 9: `gateway-server`: RS256 JWT validation + whitelist + E2E tests

**Description:** Inline public key in `config/gateway-server.yml` (D21) → `NimbusReactiveJwtDecoder` (RS256). `SecurityWebFilterChain`: token-free whitelist (D20: `POST /api/auth/token`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`), everything else requires a valid Bearer JWT → otherwise 401. `iss` claims validated against `auth-service`. E2E tests with WebTestClient and a stubbed `demo-service`: no token → 401; token signed with the valid key → 200 `"Hello world!"`; token with a foreign key or expired → 401. Decoder unit tests with a test key.

**Acceptance criteria:**
- [ ] `GET /api/demo/hello` without token → 401; with valid token → 200 `Hello world!`; badly-signed/expired token → 401
- [ ] `POST /api/auth/token` and `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health` pass without a JWT
- [ ] Coverage: the chain E2E test (spec success criterion 3) green inside `./mvnw -B verify`

**Verification:**
- [ ] Tests: `./mvnw -B verify -pl gateway-server -am`
- [ ] Manual: SPEC.md PowerShell script (token → hello 200; no token → 401) against 8080

**Dependencies:** T6, T8
**Files likely touched:** `.../adapter/in/security/SecurityConfig.java`, `.../adapter/in/security/JwtDecoderConfig.java`, `config-server/.../config/gateway-server.yml` (public key), `src/test/java/.../GatewayE2ETests.java`, test keys
**Estimated scope:** M (4-6 files)

---

## ✅ Checkpoint 3 (after T7–T9)

- [ ] Full flow through the gateway: token → hello → `Hello world!`; no token → 401
- [ ] `./mvnw -B verify` green (includes E2E)

---

## Task 10: Aggregated Swagger UI at the gateway

**Description:** With springdoc webflux-ui on the gateway: try the automatic route aggregation first (like the official springdoc demo); if 2.9.1 does not list the services by itself, the documented fallback is `springdoc.swagger-ui.urls[]` with `auth-service`/`demo-service` → `/v3/api-docs/<svc>`. Whitelist already covered by D20 (`/swagger-ui/**`, `/v3/api-docs/**`). Verify the UI loads with both groups and that "Try it out" works with the JWT (Authorize → Bearer).

**Acceptance criteria:**
- [ ] `http://localhost:8080/swagger-ui.html` loads and shows the `auth-service` and `demo-service` APIs
- [ ] Requesting a token and running `GET /api/demo/hello` from the UI with `Authorization: Bearer` returns `Hello world!`
- [ ] The aggregation works by discovering random ports (no fixed service URLs)

**Verification:**
- [ ] Build: global `./mvnw -B verify`
- [ ] Manual: full walkthrough from the browser (spec: "no Postman")

**Dependencies:** T9
**Files likely touched:** `server/gateway-server/.../application.yml` (if fallback), possible minimal customizer, README doc (T11)
**Estimated scope:** S

---

## Task 11: `server/README.md` + global success-criteria verification

**Description:** README with: requirements (JDK 25 + wrapper), startup order (config → eureka → auth/demo → gateway), commands (`verify`, `spring-boot:run`), step-by-step Swagger flow, `demo`/`demo` credentials, DEV-ONLY keys note, and how to change the TTL. Walk through SPEC.md's 7 success criteria one by one and record the result.

**Acceptance criteria:**
- [ ] README reproduces the startup and the full flow exactly as they work
- [ ] The 7 success criteria checklist marked in this file
- [ ] `webapp/` unchanged; `./mvnw -B verify` green from cold

**Verification:**
- [ ] Build: `./mvnw -B verify` (clean, from scratch)
- [ ] Manual: follow the README literally from start to finish

**Dependencies:** T10
**Files likely touched:** `server/README.md`, this `todo.md` (final checklist)
**Estimated scope:** S

---

## ✅ Final checkpoint

- [ ] The 7 success criteria of SPEC.md verified one by one
- [ ] `webapp/` untouched
- [ ] Final review with the human
