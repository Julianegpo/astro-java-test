# Task List: Esqueleto microservicios Spring (`server/`)

> Detalle de implementación; visión global y riesgos en [`plan.md`](plan.md). Fuente de verdad: [`../SPEC.md`](../SPEC.md).
> Reglas de oro: versiones SOLO en `build-parent`; `webapp/` intocado; `./mvnw -B verify` verde antes de marcar cualquier tarea.

---

## Task 1: Parent `build-parent` + `lombok.config` + Maven Wrapper

**Description:** Crear `server/pom.xml` con packaging `pom` que sea la única fuente de versiones: `<parent>` spring-boot-starter-parent 3.5.16, BOM Spring Cloud 2025.0.3, BOM springdoc 2.9.1, `lombok.version=1.18.48`, `<java.version>21` (compila con la JDK 25 instalada), `<modules>` vacíos por ahora y `pluginManagement` con maven-compiler (`release 21`) y surefire. Añadir `lombok.config` (`lombok.addLombokGeneratedAnnotation = true`) y generar el Maven Wrapper con `-Dmaven=3.9.16`.

**Acceptance criteria:**
- [x] `server/pom.xml` con `com.demo:build-parent:0.1.0-SNAPSHOT`, `java.version=21`, versiones de Lombok/springdoc/Cloud fijadas; ningún `<version>` suelto innecesario
- [x] `mvnw`, `mvnw.cmd` y `.mvn/wrapper/` generados y versionados (wrapper 3.9.16 — `mvnw -v` confirma Maven 3.9.16 + JDK 25.0.4.1)
- [x] `lombok.config` en `server/` con `addLombokGeneratedAnnotation`

**Verification:**
- [x] Build: `./mvnw -B verify` desde `server/` (solo parent, verde — BUILD SUCCESS)
- [x] Manual: `./mvnw -v` usa el wrapper (3.9.16) sin Maven global

**Dependencies:** ninguna
**Files likely touched:** `server/pom.xml`, `server/lombok.config`, `server/mvnw`, `server/mvnw.cmd`, `server/.mvn/wrapper/*`
**Estimated scope:** S (3-5 archivos de config)

---

## Task 2: `config-server` nativo (8888) + repo nativo de configuración

**Description:** Módulo `config-server` con `spring-cloud-config-server`, perfil `native` activo, puerto 8888, `search-locations: classpath:/config`, y el main anotado `@EnableConfigServer`. Crear `src/main/resources/config/application.yml` (común: `eureka.client.service-url.defaultZone`, `eureka.instance.prefer-ip-address`). Sin dependencia de Eureka (arranca el primero; D5/orden de arranque). Los `<servicio>.yml` específicos se crean en las tareas de cada servicio (T5/T6, T8).

**Acceptance criteria:**
- [x] `ConfigServerApplication` arranca en 8888 con perfil nativo (contexto de test arrancado con perfil `native` ✓)
- [ ] `GET http://localhost:8888/application/default` devuelve JSON con las properties comunes → *manual, diferido al Checkpoint 1 junto a Eureka (evita arrancar dos veces)*
- [x] `@SpringBootTest` de contexto pasa con `spring.cloud.config.server.native.search-locations` de test

**Verification:**
- [x] Tests: `./mvnw -B verify -pl config-server -am` (BUILD SUCCESS, 1 test OK)
- [ ] Manual: arranque + `Invoke-RestMethod http://localhost:8888/application/default` → *Checkpoint 1*

**Dependencies:** T1
**Files likely touched:** `server/config-server/pom.xml`, `.../ConfigServerApplication.java`, `.../application.yml`, `.../resources/config/application.yml`, `src/test/java/.../ConfigServerApplicationTests.java`
**Estimated scope:** S (4-6 archivos)

---

## Task 3: QA en el build (Spotless + Checkstyle + SpotBugs/FindSecBugs + JaCoCo)

**Description:** En el parent: `pluginManagement` + `<plugins>` (heredado por todos los hijos) con Spotless 3.10.4 (`google-java-format` **fijado a 1.36.1**, `spotless:check` en `verify`), maven-checkstyle-plugin 3.6.0 con tool checkstyle 14.3.0 y reglas propias saneadas (`config/checkstyle.xml`: set corto — pestañas, EOF, LineLength 120, UnusedImports, NeedBraces, EqualsHashCode, ...), spotbugs-maven-plugin 4.10.5.0 con FindSecBugs 1.14.0 (`failOnError`, effort Max, threshold Medium), y JaCoCo 0.8.15 (`prepare-agent` + `report` en `verify`, sin gate). Fail-fast de la toolchain JDK 25 superado.

**Hallazgo (decisión registrada):** gjf ≥ 1.37.0 rompió compatibilidad binaria: `JavaFormatterOptions$Style` pasó de enum a record y eliminó `valueOf(String)`, que la glue de Spotless 3.10.4 invoca → `NoSuchMethodError` envuelto en `InvocationTargetException` (verificado con `javap` en ambas versiones). Solución: fijar gjf a **1.36.1** (la versión por defecto que valida el propio plugin). No hizo falta el fallback a palantir-java-format. Además `server/.mvn/jvm.config` exporta `jdk.compiler/com.sun.tools.javac.*` al JVM de la build: hoy es opcional (Spotless abre los módulos vía `Unsafe`, con warning de deprecación en JDK 25) pero se conserva como defensa para cuando el JDK elimine ese `Unsafe`.

**Acceptance criteria:**
- [x] Los 4 plugins corren en `./mvnw -B verify` sin errores de JDK 25 (gjf fijado a 1.36.1; sin fallback)
- [x] Reglas saneadas: 0 violaciones Checkstyle y 0 bugs SpotBugs sobre el código actual, FindSecBugs activo
- [x] Reportes generados: `target/site/jacoco/` (index.html + jacoco.xml), `spotbugsXml.xml`, `checkstyle-result.xml`

**Verification:**
- [x] Build: `./mvnw -B verify` global y `-pl config-server -am` (BUILD SUCCESS, `LASTEXITCODE=0`)
- [x] Manual RED/GREEN: romper formato → `verify` falla en `spotless:check`; `./mvnw spotless:apply` corrige y vuelve a verde

**Dependencies:** T2 (necesita un módulo real para validar)
**Files likely touched:** `server/pom.xml`, `server/config/checkstyle.xml`, posibles `spotbugs-exclude.xml`, `server/config-server` (formateo)
**Estimated scope:** M (3-5 archivos + iteración de reglas)

---

## Task 4: `discovery-server` Eureka (8761, 1 nodo)

**Description:** Módulo `discovery-server` con `spring-cloud-starter-netflix-eureka-server`, puerto 8761, main `@EnableEurekaServer`, auto-registro desactivado (`register-with-eureka: false`, `fetch-registry: false`, D12 solitario). Sin config server (independiente para evitar ciclos de arranque).

**Acceptance criteria:**
- [x] Arranca en 8761 y sirve el dashboard Eureka *(contexto de test: "Started Eureka Server"; navegador → Checkpoint 1)*
- [x] No intenta registrarse en sí mismo ni contactar al config server *("Client configured to neither register nor query for data"; sin dependencia de config en el pom)*
- [x] Context test pasa en frío (sin red)

**Verification:**
- [x] Tests: `./mvnw -B verify -pl discovery-server -am` (RED→GREEN: sin clase principal → `IllegalStateException`; con ella → 1 test OK, Eureka arranca en contexto)
- [ ] Manual: navegador `http://localhost:8761/` → *Checkpoint 1 (junto a config-server)*

**Hallazgo menor:** ficheros nuevos con fin de línea LF → Spotless los exige CRLF (como el resto del repo); `./mvnw spotless:apply` los corrige. Flujo ya documentado en T3, sin novedad.
**Decisión (humano):** banner "EMERGENCY! EUREKA MAY BE INCORRECTLY..." en el dashboard = modo autopreservación, esperado en dev (0 servicios → 0 renewals < umbral mínimo 1 del nodo solitario). **Se deja activo**: se autocurará cuando auth/demo se registren (T5+, cada uno renueva 2/min). No tocar `eureka.server.enable-self-preservation`.

**Dependencies:** T1
**Files likely touched:** `server/discovery-server/pom.xml`, `.../DiscoveryServerApplication.java`, `.../application.yml`, test de contexto
**Estimated scope:** S

---

## ✅ Checkpoint 1 (tras T1–T4)

- [x] `./mvnw -B verify` completo en verde con QA activo (verificado en T3)
- [x] `config-server` (8888) y `discovery-server` (8761) arrancan y responden *(con el humano: JSON de `/application/default` correcto con las 2 properties nativas; dashboard Eureka carga)*
- [x] Revisión/pausa con el humano antes de continuar con T5 *(hecha; decisión registrada sobre el banner de autopreservación, ver T4)*

---

## Task 5: `auth-service`: esqueleto web + config client + Eureka + H2/JPA con seed

**Description:** Módulo `auth-service` (spring-boot-starter-web, -data-jpa, h2, spring-security-crypto, actuator, eureka-client, spring-cloud-starter-config, springdoc webmvc-ui, lombok provided). `application.yml` local con `spring.application.name=auth-service` y `spring.config.import: optional:configserver:http://localhost:8888` (refinamiento D5). `schema.sql` (tabla `users`: username PK, password_hash, roles) + `data.sql` con INSERT del usuario `demo` (hash BCrypt de `demo` precalculado). Entidad/repo JPA (`UserAccount` + `UserAccountRepository extends JpaRepository`). `ddl-auto=none`. Profile `test` sin config server/Eureka. Crear `config/auth-service.yml` en el config server con `server.port: 0`.

**Acceptance criteria:**
- [x] Contexto de test en verde con H2 + `data.sql` ejecutado *(3/3 tests: contexto + `findByUsername("demo")` con `BCryptPasswordEncoder.matches("demo", …)` + count=1)*
- [x] Arranca con config server arriba y se registra en Eureka con puerto aleatorio *(`Located environment: name=auth-service`, `jdbc:h2:mem:authdb` remoto; Eureka: AUTH-SERVICE UP en puerto aleatorio, p.ej. 56749)*
- [x] Sin config server, degrada limpiamente en local (`optional:configserver`) con puerto aleatorio *(WARN "Could not locate PropertySource" con `optional = true` → arranca en 2,5 s con puerto aleatorio p.ej. 59493 y se registra vía default del cliente Eureka)*

**Verification:**
- [x] Tests: `./mvnw -B verify -pl auth-service -am` (RED→GREEN: compile sin `adapter.out` → 3/3 OK)
- [x] Manual: arrancar config → eureka → auth; verificar registro *(API REST `/eureka/apps` muestra AUTH-SERVICE UP con puerto aleatorio; el dashboard a la vista del humano queda para Checkpoint 2)*
- [x] Regresión global: `./mvnw -B verify` en verde con los 4 módulos (BUILD SUCCESS, exit 0)

**Hallazgos:**
- **Lombok + JDK 25 (toolchain):** `javac` de JDK ≥23 ya no ejecuta el annotation processing implícito de la classpath → los `@Getter/@Setter` de la entidad no se generaban y el test de seed fallaba por métodos inexistentes. Fix en `build-parent` (pluginManagement `maven-compiler-plugin` con `<proc>full</proc>`), necesario para cualquier módulo futuro con Lombok. Compilar con `--release 21` no lo mitigaba.
- **Inyección en tests:** constructor de JUnit 5 con parámetro de Spring da `ParameterResolutionException` en este setup → `@Autowired` por campo en tests (la regla de constructor-injection del SPEC es para código de producción).
- **Config de test = shadow classpath:** en lugar del profile `test` previsto, `src/test/resources/application.yml` **sustituye** al principal en el classpath de test (config import y Eureka desactivados). Motivo: determinismo en frío garantizado aunque haya servicios corriendo en local; con profile `test` el import remoto podría resolverse antes de desactivarse.
- **`server.port: 0` también en el `application.yml` local** (no solo en el config remoto): si el config server está caído, la config remota no llega y el puerto local decide — así el modo degradado cumple D3.
- **`ddl-auto: none` local y en el shadow de test** (no solo en remoto): con H2 embebido el default es `create-drop`, que borraría el seed de `data.sql` (el init de SQL corre antes de JPA).
- Hash BCrypt de `demo` precalculado con source-launcher de la JDK 25 sobre `spring-security-crypto-6.5.11.jar` de m2 (temp, ya borrable).
- *Nota operativa:* `spring-boot:run` hace fork — matar solo el wrapper Maven deja la app JVM huérfana (hubo que liberar el 8888 a mano); matar por puerto escuchado es el kill fiable.

**Dependencies:** T2, T4
**Files likely touched:** `server/auth-service/pom.xml`, `.../application.yml`, `AuthServiceApplication.java`, `adapter/out/UserAccount.java`, `adapter/out/UserAccountRepository.java`, `schema.sql`, `data.sql`, tests, `server/config-server/src/main/resources/config/auth-service.yml`
**Estimated scope:** M (6-8 archivos)

---

## Task 6: `auth-service`: emisión JWT RS256 con TTL configurable

**Description:** Generar par RSA 2048 dev (script Java single-file con la JDK 25 → PEM PKCS#8 + X.509) y ponerlo en `config/auth-service.yml` (clave privada inline + `app.jwt.expiration-seconds: 3600`, D19/D21). `TokenService` (application.service) con nimbus (`RSASSASigner`): valida credenciales contra H2 con `BCryptPasswordEncoder` y firma JWT con claims `sub`, `roles`, `iss=auth-service`, `exp` desde `app.jwt.expiration-seconds`. `TokenController` (adapter.in.web) `POST /api/auth/token` → record `TokenResponse(token, expiresIn)` con `@Builder @Jacksonized`. SecurityFilterChain permitiendo token/swagger/health. Tests: unit Mockito (firma, claims, expiración, credenciales inválidas), `@WebMvcTest` del controller, contexto con claves de test en `src/test/resources`.

**Acceptance criteria:**
- [x] `demo`/`demo` → JWT RS256 con `sub=demo`, `iss=auth-service`, `exp` acorde a `app.jwt.expiration-seconds`; password inválido → 401/400 *(200 + token verificado; password malo → 401 + `{"code":401,"message":"Invalid credentials","timestamp":...}`)*
- [x] Cambiar `app.jwt.expiration-seconds` en el config server + reiniciar auth cambia la TTL sin recompilar (criterio de éxito 4 de la spec) *(3600→1800 y vuelta a 3600 solo editando `config/auth-service.yml` + reinicio; `exp−iat` = 1800 y después 3600, sin tocar código)*
- [x] Cobertura de tests unit + web + contexto en verde; clave privada jamás presente fuera de config/`auth-service` *(auth-service ~91% líneas; la privada vive solo en `config/auth-service.yml`)*

**Verification:**
- [x] Tests: `./mvnw -B verify -pl auth-service -am` (12/12, 0 bugs SpotBugs, 0 checkstyle, spotless limpio)
- [x] Regresión global: `./mvnw -B verify` → BUILD SUCCESS, exit 0 (4 módulos)
- [x] Manual: token `demo`/`demo` → 200; firma RS256 **VALID** con la clave pública DEV (source-launcher `VerifyJwt.java` sobre `sha256withRSA`); claims `sub=demo`, `iss=auth-service`, `roles=ROLE_USER`, `exp−iat=3600`; TTL reconfigurada sin recompilar (ver AC2)

**Hallazgos:**
- **Lombok + Jacksonized:** la clave correcta en `lombok.config` es `lombok.jacksonized.jacksonVersion = 2` (verificada en projectlombok.org; la variante `lombok.jacksonized.useJackson3` NO existe → "unknown key").
- **SpotBugs `CT_CONSTRUCTOR_THROW`** en `TokenService` (lanza en constructor vía guards) → clase `final` como mitigación canónica; Mockito inline-mock-maker la mockea sin problema.
- **Sin `starter-validation`** (no listado en tabla SPEC → Boundaries ask-first): guard de blank/null en `TokenService` → 401 unificado vía `GlobalExceptionHandler` con `ErrorResponse(code,message,timestamp)` (elección propia, documentada).
- **Puerto de salida D2**: `UserAccounts` (interface + record anidado `Credentials`) en `application.service`; `JpaUserAccounts` en `adapter.out`.
- **Clave de test**: par TEST desechable (privada+pública) en `src/test/resources/application.yml` (shadow de classpath); la pública permite verificar la firma en el test de contexto.
- **Import correcto**: `org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer` (el paquete `...configuration` no existe); `spotless:apply` reordena imports — no editar sobre texto pre-spotless.
- **Eureka con registro stale**: tras reiniciar un servicio, el dashboard/API puede devolver la instancia anterior (puerto viejo) hasta ~30 s → verificar que el puerto escucha de verdad antes de petición, o esperar al refresh del registry.

**Dependencies:** T5
**Files likely touched:** `.../application/service/TokenService.java`, `.../adapter/in/web/TokenController.java`, `.../adapter/in/web/TokenResponse.java`, `.../adapter/in/web/SecurityConfig.java`, `config/auth-service.yml` (claves), test de gen de claves, tests unit/web
**Estimated scope:** M (6-8 archivos)

---

## ✅ Checkpoint 2 (tras T5–T6)

- [x] Dashboard Eureka muestra `auth-service` con puerto aleatorio *(con el humano)*
- [x] El flujo de token funciona por puerto directo y la TTL es configurable por config *(con el humano: token demo/demo por Swagger UI + TTL 3600→1800→3600 sin recompilar)*
- [x] `./mvnw -B verify` en verde

---

## Task 7: `demo-service`: `GET /api/demo/hello` → `"Hello world!"`

**Description:** Módulo `demo-service` (web, eureka-client, spring-cloud-starter-config, springdoc webmvc-ui, actuator, lombok). `HelloController` (adapter.in.web) que devuelve `"Hello world!"` (texto plano). `spring.application.name=demo-service` + `spring.config.import`. Crear `config/demo-service.yml` en el config server (`server.port: 0`). Tests: unit del controller + `@WebMvcTest` + contexto en frío.

**Acceptance criteria:**
- [x] `GET /api/demo/hello` devuelve 200 `"Hello world!"`; registrado en Eureka con puerto aleatorio *(`body = [Hello world!]` como texto plano desde el puerto aleatorio 51295; Eureka: DEMO-SERVICE UP en 51295)*
- [x] Swagger UI accesible en el puerto aleatorio del servicio (`/swagger-ui.html`) *(`/v3/api-docs` con path `/api/demo/hello` + `/swagger-ui.html` responden en el puerto aleatorio)*
- [x] Tests verdes en frío *(3/3: unit `HelloControllerTests`, `@WebMvcTest HelloControllerWebTests` con text/plain, contexto `DemoServiceApplicationTests` sin config server ni Eureka)*

**Verification:**
- [x] Tests: `./mvnw -B verify -pl demo-service -am` (RED→GREEN: sin `HelloController` → `cannot find symbol`; con él → BUILD SUCCESS, 0 checkstyle, 0 SpotBugs)
- [x] Manual: `Invoke-RestMethod http://localhost:<demo>/api/demo/hello` → `Hello world!`; registro Eureka y swagger-ui verificados con config→eureka→demo arrancados en ese orden (procesos matados por puerto después)
- [x] Regresión global: `./mvnw -B verify` en verde con los 5 módulos (BUILD SUCCESS, exit 0)

**Nota de trazabilidad:** `spring-cloud-starter-config` incluido según D5 (patrón de config client de T5) aunque la tabla de dependencias de SPEC.md no lo lista para demo-service; la descripción de esta tarea en `tasks/todo.md` sí lo prevé.

**Dependencies:** T2, T4
**Files likely touched:** `server/demo-service/pom.xml`, `.../application.yml`, `DemoServiceApplication.java`, `adapter/in/web/HelloController.java`, tests, `config-server/.../config/demo-service.yml`
**Estimated scope:** S

---

## Task 8: `gateway-server`: rutas vía Eureka + bucle api-docs

**Description:** Módulo `gateway-server` (spring-cloud-starter-gateway — **WebFlux** —, eureka-client, spring-cloud-starter-config, springdoc webflux-ui, actuator, lombok). Rutas en `config/gateway-server.yml` (config server, D5): `POST /api/auth/token` → `lb://auth-service`; `/api/demo/**` → `lb://demo-service`; bucle openapi `/v3/api-docs/auth-service` y `/v3/api-docs/demo-service` → `lb://<svc>` con `RewritePath` a `/v3/api-docs` (patrón del demo oficial de springdoc; namespace `spring.cloud.gateway.*` de 2025.0.x). Crear `config/gateway-server.yml` con puerto 8080 y URL de Eureka.

**Acceptance criteria:**
- [x] Arranca en 8080 (lee rutas del config server) y enruta a los servicios por nombre (descubre puertos aleatorios vía Eureka) *(`GET :8080/api/demo/hello` → `Hello world!` vía lb://demo-service:60776 y `POST :8080/api/auth/token` → token 483 chars vía lb://auth-service:58927; el gateway no tiene rutas locales: vinieron del config server)*
- [x] `GET http://localhost:8080/v3/api-docs/demo-service` devuelve el OpenAPI del demo-service (sin seguridad aún) *(OpenAPI 3.1.0 con path `/api/demo/hello`; también verificado el bucle de auth: `/api/auth/token`)*
- [x] Arranque limpio sin warnings fatales de namespace/compatibilidad *(namespace `server.webflux` sin avisos; ver hallazgos: quedan warnings NO fatales de deprecación del propio starter en 4.3.x → decisión pendiente del humano)*

**Verification:**
- [x] Tests: `./mvnw -B verify -pl gateway-server -am` (2/2: contexto en frío + `GatewayRouteDefinitionsTests` que valida que las 4 rutas parsean desde el namespace correcto)
- [x] Manual: arrancar config → eureka → auth → demo → gateway y probar las 3 URLs *(503 Service Unavailable inicial = registry cache de Eureka (~30 s, misma familia que T6); tras el refresh, todo 200. GATEWAY-SERVER registrado UP:8080)*
- [x] Regresión global: `./mvnw -B verify` en verde con los 6 módulos (BUILD SUCCESS, exit 0)

**Hallazgos:**
- **Namespace de Spring Cloud Gateway 4.3.x verificado empíricamente**: extraído `META-INF/spring-configuration-metadata.json` del jar `spring-cloud-gateway-server-4.3.5.jar` del m2 → coexisten `spring.cloud.gateway.*` (legacy, deprecado) y **`spring.cloud.gateway.server.webflux.*`** (actual). Las rutas usan el namespace nuevo. `GatewayRouteDefinitionsTests` es la red de seguridad: si el namespace estuviera mal, no registraría ninguna ruta y el test fallaría en `verify` (no en runtime).
- **Deprecación del starter en 4.3.x (decisión pendiente del humano)**: `spring-cloud-starter-gateway` loguea en arranque `spring-cloud-starter-gateway is deprecated... use spring-cloud-starter-gateway-server-webflux instead` (y el artifact `spring-cloud-gateway-server` pide `spring-cloud-gateway-server-webflux`). No es fatal (AC3 cumple: "sin warnings fatales") y el módulo queda 100% funcional, pero cambiar el starter toca la tabla de dependencias de SPEC.md → Boundaries ask-first: **preguntar antes de cambiar**.
- **Timing de Eureka en lb://**: peticiones contra rutas `lb://` en los ~30 s posteriores al arranque dan 503 (response cache del Eureka server); tras el refresh del registry, 200. Mismo fenómeno que el registro stale de T6.
- **Test de rutas**: `RouteDefinitionLocator.getRouteDefinitions()` devuelve `Flux` (Reactor), no `Stream`; y hay varios beans locator → autowire `List<RouteDefinitionLocator>` + `Flux.fromIterable(...).flatMap(...)`.
- **Predicado auth**: `Path=/api/auth/token` **+ `Method=POST`** (la SPEC describe POST; con Path solo, GET también enrutaría hacia el auth).

**Dependencies:** T4, T5, T7
**Files likely touched:** `server/gateway-server/pom.xml`, `.../application.yml`, `GatewayServerApplication.java`, `config-server/.../config/gateway-server.yml`, test de contexto
**Estimated scope:** M

---

## Task 9: `gateway-server`: validación JWT RS256 + whitelist + tests E2E

**Description:** Clave pública inline en `config/gateway-server.yml` (D21) → `NimbusReactiveJwtDecoder` (RS256). `SecurityWebFilterChain`: whitelist sin token (D20: `POST /api/auth/token`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`), todo lo demás exige Bearer JWT válido → si no, 401. Claims `iss` validados contra `auth-service`. Tests E2E con WebTestClient y `demo-service` stubbeado: sin token → 401; token firmado con la clave válida → 200 `"Hello world!"`; token con clave ajena o expirado → 401. Unidades del decoder con clave de test.

**Acceptance criteria:**
- [ ] `GET /api/demo/hello` sin token → 401; con token válido → 200 `Hello world!`; token mal firmado/expirado → 401
- [ ] `POST /api/auth/token` y `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health` pasan sin JWT
- [ ] Cobertura: test E2E de la cadena (criterio de éxito 3 de la spec) en verde dentro de `./mvnw -B verify`

**Verification:**
- [ ] Tests: `./mvnw -B verify -pl gateway-server -am`
- [ ] Manual: script PowerShell del SPEC.md (token → hello 200; sin token → 401) contra 8080

**Dependencies:** T6, T8
**Files likely touched:** `.../adapter/in/security/SecurityConfig.java`, `.../adapter/in/security/JwtDecoderConfig.java`, `config-server/.../config/gateway-server.yml` (pública), `src/test/java/.../GatewayE2ETests.java`, claves de test
**Estimated scope:** M (4-6 archivos)

---

## ✅ Checkpoint 3 (tras T7–T9)

- [ ] Flujo completo por el gateway: token → hello → `Hello world!`; sin token → 401
- [ ] `./mvnw -B verify` en verde (incluye E2E)

---

## Task 10: Swagger UI agregado en el gateway

**Description:** Con springdoc webflux-ui en el gateway: probar la agregación automática de rutas (como el demo oficial de springdoc); si 2.9.1 no lista los servicios solo, fallback `springdoc.swagger-ui.urls[]` con `auth-service`/`demo-service` → `/v3/api-docs/<svc>` (propiedad documentada). Whitelist ya cubierta por D20 (`/swagger-ui/**`, `/v3/api-docs/**`). Verificar que el UI carga con ambos grupos y que "Try it out" funciona con el JWT (Authorize → Bearer).

**Acceptance criteria:**
- [ ] `http://localhost:8080/swagger-ui.html` carga y muestra los APIs de `auth-service` y `demo-service`
- [ ] Pedir un token y ejecutar `GET /api/demo/hello` desde el UI con `Authorization: Bearer` devuelve `Hello world!`
- [ ] El agregado funciona descubriendo puertos aleatorios (sin URLs fijas de servicios)

**Verification:**
- [ ] Build: `./mvnw -B verify` global
- [ ] Manual: recorrido completo desde el navegador (spec: "sin Postman")

**Dependencies:** T9
**Files likely touched:** `server/gateway-server/.../application.yml` (si fallback), posible customizer mínimo, doc en README (T11)
**Estimated scope:** S

---

## Task 11: `server/README.md` + verificación global de criterios de éxito

**Description:** README con: requisitos (JDK 25 + wrapper), orden de arranque (config → eureka → auth/demo → gateway), comandos (`verify`, `spring-boot:run`), flujo Swagger paso a paso, credenciales `demo`/`demo`, nota SOLO-DEV de las claves, y cómo cambiar la TTL. Recorrer los 7 criterios de éxito de SPEC.md uno a uno y dejar constancia.

**Acceptance criteria:**
- [ ] README reproduce el arranque y el flujo completo tal cual funcionan
- [ ] Checklist de los 7 criterios de éxito marcado en este archivo
- [ ] `webapp/` sin cambios; `./mvnw -B verify` en verde en frío

**Verification:**
- [ ] Build: `./mvnw -B verify` (limpio, desde cero)
- [ ] Manual: seguir el README literalmente de principio a fin

**Dependencies:** T10
**Files likely touched:** `server/README.md`, este `todo.md` (checklist final)
**Estimated scope:** S

---

## ✅ Checkpoint final

- [ ] 7/7 criterios de éxito de SPEC.md verificados
- [ ] `webapp/` intacto
- [ ] Revisión final con el humano
