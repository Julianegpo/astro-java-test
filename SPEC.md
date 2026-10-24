# Spec: Esqueleto de backend microservicios Spring (`server/`)

Estado: **APROBADA (SPECIFY cerrada) — en fase PLAN/IMPLEMENT.** Plan en `tasks/plan.md`, tareas en `tasks/todo.md`. Sin preguntas abiertas.
Regla del usuario: **no se asume nada** — las decisiones delegadas (Q7, Q12) están marcadas como tales; el resto procede de respuestas explícitas.

---

## Mapa de capacidades (Phase 0)

| Module id | Responsabilidad | Depende de |
|---|---|---|
| `build-parent` | `server/pom.xml` (packaging `pom`): **único lugar** que fija versiones (BOM Boot + Spring Cloud + Lombok + springdoc + plugins) | — |
| `config-server` | Spring Cloud Config Server, backend **native** (config "por código" en este mismo repo) | `build-parent` |
| `discovery-server` | Eureka Server (1 nodo) | `build-parent` |
| `auth-service` | **H2 en memoria** (JPA) con usuario sembrado por SQL; endpoint **POST** que firma y emite JWT **RS256** con TTL configurable por config server | `build-parent`, `config-server` |
| `demo-service` | GET que devuelve `"Hello world!"` | `build-parent`, `config-server`, `discovery-server` |
| `gateway-server` | Spring Cloud Gateway: única puerta de entrada; **verifica el JWT en todas las rutas** (salvo whitelist), enruta vía Eureka y **sirve Swagger UI agregado** | `build-parent`, `config-server`, `discovery-server`, `auth-service` (contrato JWT) |

Orden de build: `build-parent` → [`config-server`, `discovery-server`] → [`auth-service`, `demo-service`] → `gateway-server`

## Decisiones (todas cerradas)

| # | Decisión | Origen |
|---|---|---|
| D1 | Spring Boot **3.5.x (LTS)** + Spring Cloud **2025.0.x (Northfields)** — único train GA oficial con Boot 3.5 que incluye netflix+gateway+config (matriz oficial verificada) | Usuario |
| D2 | groupId **`com.demo`**, paquetes `com.demo.<modulo>`, capas `adapter.in.web` / `application.service` / `domain` / `adapter.out` | Usuario |
| D3 | Puertos: fijos **config 8888, eureka 8761, gateway 8080**; **aleatorios** (`server.port=0`) en `auth-service` y `demo-service` (se descubren vía Eureka; el cliente solo ve el 8080) | Usuario + evaluación |
| D4 | Config Server **native**: todo por código en este mismo repo, sin repo git de config ni config dispersa por servicios | Usuario |
| D5 | Los servicios **sí** leen su config del Config Server: `spring.config.import: optional:configserver:http://localhost:8888` + `spring.application.name` en su `application.yml` local (patrón estándar actual, verificado en el demo oficial de springdoc; **sin `bootstrap.yml`** — refinamiento de plan sobre el borrador original). Aquí viajan URL de Eureka, claves RS256 y TTL del JWT | Recomendación aceptada + verificación en fase de plan |
| D6 | Todo el tráfico de negocio **a través del gateway**: `POST /api/auth/token`, `GET /api/demo/hello` → `lb://...`. Sin JWT → 401 | Usuario |
| D7 | Tests con **Mockito** | Usuario |
| D8 | **Lombok 1.18.48** forzado (`lombok.version` en el padre; JDK 25 necesita ≥1.18.40). `lombok.config` en `server/` | Usuario + verificado |
| D9 | **Maven Wrapper: sí** (`mvn -N wrapper:wrapper`, versionado) | Recomendación aceptada |
| D10 | QA del build: **Spotless + SpotBugs(FindSecBugs) + Checkstyle** — "todo lo posible para mejorar la calidad global" | Usuario (Q2) |
| D11 | **JaCoCo** con reporte en `verify`; umbral de cobertura a definir más adelante | Recomendación aceptada |
| D12 | **Eureka solitario** (1 nodo) | Recomendación aceptada |
| D13 | Compilar con **`--release 21`** usando la JDK 25 instalada (no instala nada; solo fija bytecode/APIs a Java 21, el LTS estándar junto a Boot 3.5) | Delegado al asistente (Q7) → **21** |
| D14 | JWT **RS256**: clave privada solo en `auth-service`, pública en el gateway | Usuario (D14) |
| D15 | Librería JWT: **nimbus vía `spring-security-oauth2-resource-server`/`oauth2-jose`** (pila estándar de Spring Security) | Usuario |
| D16 | `auth-service`: **H2 en memoria** + **Spring Data JPA** + `schema.sql` + `data.sql` con INSERT directo de usuario **fijo `demo`/`demo`** con hash **BCrypt** | Usuario (Q13) |
| D17 | **Swagger UI vía springdoc-openapi 2.9.x** (línea para Boot 3.5, verificada): UI por servicio y **agregada en el gateway** → `http://localhost:8080/swagger-ui.html`. Sin Postman | Usuario |
| D18 | `auth-service`/`demo-service` = **spring-boot-starter-web** (servlet); `gateway-server` = **WebFlux** (reactivo, propio de spring-cloud-gateway) | Consecuencia técnica verificada |
| D19 | **Claims del JWT**: `sub` (usuario), `roles`, `iss` (`auth-service`), `exp` = **`app.jwt.expiration-seconds`**, property leída del config server (`auth-service.yml`, por defecto 3600). Cambiar TTL = editar el fichero + reiniciar `auth-service`, **sin recompilar** | Usuario (Q6: pidió exp configurable vía config server) |
| D20 | **Whitelist del gateway (sin JWT)**: `POST /api/auth/token`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`. Todo lo demás → 401 | Usuario (Q5: "las que mencionas") |
| D21 | **Claves RS256 fijas en el config nativo**: par en el repo de config (privada → `auth-service.yml`, pública → `gateway-server.yml`). JWKS/rotación real descartado (choca con puertos aleatorios; Vault = fuera de alcance) | Usuario (Q11) |
| D22 | Swagger **público vía gateway** y agregado con discovery de Eureka — la opción que "funciona de verdad" con los puertos aleatorios: los servicios se localizan por nombre, no por puerto. **Config verificada en fase de plan** contra el demo oficial `springdoc-openapi-demos/demo-microservices`: rutas `/{svc}/** → lb://` + bucle `/v3/api-docs/{svc} → /v3/api-docs` + springdoc webflux-ui en el gateway; fallback documentado `springdoc.swagger-ui.urls[]` si la agregación automática de 2.9.1 no bastara | Delegado al asistente (Q12) ✓ verificado |

## Objective

Esqueleto bajo `.\server\` lo más cercano posible a una aplicación empresarial real: Spring Boot 3.5 LTS + Config/Eureka/Gateway, `auth-service` con H2 que emite JWT RS256 (TTL configurable por config server), `demo-service` de ejemplo, QA de build completo y Swagger UI agregado en el gateway para probar sin Postman.

Flujo objetivo:
1. Abrir `http://localhost:8080/swagger-ui.html`.
2. `POST /api/auth/token` con `demo`/`demo` → JWT (caducidad según config).
3. `GET /api/demo/hello` con `Authorization: Bearer <jwt>` → `Hello world!`.
4. Misma llamada sin token → `401`.

Criterios de éxito:
1. `./mvnw -B verify` limpio en frío (JDK 25 + red): compila, testea, formatea y analiza.
2. Orden de arranque config → eureka → auth/demo → gateway; los aleatorios aparecen en Eureka.
3. Flujo completo JWT vía gateway (pasos 1–4), cubierto también por test E2E (401 sin/bad token, 200 con token).
4. Cambiar `app.jwt.expiration-seconds` en `auth-service.yml` del config nativo y reiniciar `auth-service` altera la TTL sin recompilar.
5. Cero `<version>` en dependencias de módulos hijos; versiones solo en `build-parent`.
6. Swagger UI accesible por el gateway y endpoints probables desde el navegador.
7. `webapp/` intacto.

## Tech Stack

**Equipo (verificado):** JDK Amazon Corretto 25.0.4.1 (`C:\dev\tools\Amazon Corretto\jdk25.0.4_10`), Maven 3.10.0 (`C:\dev\tools\apache-maven-3.10.0`), Windows 11.

**Versiones clave (fijadas en fase de plan desde Maven Central / docs oficiales, oct-2026):**
- Spring Boot **3.5.16** (LTS; docs oficiales: "compatible with versions up to and including Java 25" → la JDK 25 instalada es válida) + Spring Cloud **2025.0.3** (último train de Boot 3.5 → netflix/gateway/config 4.3.x).
- Lombok **1.18.48** · springdoc-openapi **2.9.1** (swagger-ui 5.32.14) · H2 (gestionado por Boot) · JaCoCo **0.8.15** · Spotless **3.10.4** · SpotBugs **4.10.5.0** (+FindSecBugs) · Checkstyle plugin **3.6.0** + tool **14.3.0** · Maven Wrapper **3.9.16**.

**Dependencias por módulo** (todas sin `<version>` en hijos):

| Módulo | Dependencias principales |
|---|---|
| `config-server` | `spring-cloud-config-server` |
| `discovery-server` | `spring-cloud-starter-netflix-eureka-server` |
| `auth-service` | `spring-boot-starter-web`, `-data-jpa`, `h2`, `spring-security-crypto`, `spring-boot-starter-security`, `oauth2-resource-server`, `springdoc-openapi-starter-webmvc-ui`, `actuator`, `lombok`(provided) |
| `demo-service` | `spring-boot-starter-web`, `spring-cloud-starter-netflix-eureka-client`, `springdoc-openapi-starter-webmvc-ui`, `actuator`, `lombok`(provided) |
| `gateway-server` | `spring-cloud-starter-gateway-server-webflux` (renombre oficial del starter en 4.3.x — el viejo `spring-cloud-starter-gateway` está deprecado y avisa en cada arranque; decisión del humano en T8), `spring-cloud-starter-netflix-eureka-client`, `oauth2-resource-server`, `springdoc-openapi-starter-webflux-ui`, `actuator`, `lombok`(provided) |
| todos (test) | `spring-boot-starter-test` (incluye Mockito) |

## Commands

Todo desde `server/`:

```powershell
./mvnw -B verify                          # build + tests + formateo + análisis (verificación principal)
./mvnw -B verify -pl demo-service -am     # un módulo concreto (+ dependencias)
./mvnw spotless:apply                     # formatear a mano
./mvnw -B spring-boot:run -pl config-server     # 8888
./mvnw -B spring-boot:run -pl discovery-server  # 8761
./mvnw -B spring-boot:run -pl auth-service      # puerto aleatorio (ver log)
./mvnw -B spring-boot:run -pl demo-service      # puerto aleatorio (ver log)
./mvnw -B spring-boot:run -pl gateway-server    # 8080
mvn -N wrapper:wrapper                    # generar wrapper (una vez; se versiona)
```

Prueba sin Postman: navegador → `http://localhost:8080/swagger-ui.html`.
Alternativa CLI:

```powershell
$jwt = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/token -Body '{"username":"demo","password":"demo"}' -ContentType 'application/json').token
Invoke-RestMethod -Uri http://localhost:8080/api/demo/hello -Headers @{ Authorization = "Bearer $jwt" }  # "Hello world!"
Invoke-WebRequest -Uri http://localhost:8080/api/demo/hello   # 401 esperado
```

## Project Structure

```
astro-java-test/
├── SPEC.md              → este documento
├── webapp/              → INTOCABLE (app Astro)
└── server/
    ├── pom.xml          → build-parent (única fuente de versiones)
    ├── mvnw, mvnw.cmd, .mvn/   → wrapper versionado
    ├── lombok.config    → config común de Lombok
    ├── config-server/
    │   └── src/main/resources/config/    → repo NATIVO de configuración
    │       ├── application.yml          (común: Eureka)
    │       ├── auth-service.yml         (par RS256 + app.jwt.expiration-seconds + H2)
    │       ├── demo-service.yml
    │       └── gateway-server.yml       (clave pública RS256 + whitelist)
    ├── discovery-server/
    ├── auth-service/
    │   └── src/main/resources/          → application.yml (name + spring.config.import),
    │                                      schema.sql, data.sql (INSERT demo/BCrypt)
    ├── demo-service/
    └── gateway-server/
```

Tests: `src/test/java/com/demo/<modulo>/` espejando el paquete principal.

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
        // valida contra H2 (usuario sembrado en data.sql con hash BCrypt)
        // firma RS256 con la clave privada; expira según app.jwt.expiration-seconds (config server)
        return TokenResponse.builder().token("...").expiresIn(3600).build();
    }
}
```

Reglas Lombok (aprendizaje de builders incluido):
- **Inmutables primero**: DTOs = `record`. Lombok donde el record no encaja.
- **`@Builder` + `@Jacksonized`** para DTOs que Jackson deserializa; `@Builder.Default` obligatorio si hay defaults; validación en constructor compacto.
- **Evitar `@Data` en dominio**; `@Value`/`@Getter`+`@Builder` para inmutables.
- `lombok.config` en `server/` raíz: `lombok.addLombokGeneratedAnnotation = true`.
- Inyección por constructor (`private final`); `application.yml` kebab-case; nombres por capas.

## Testing Strategy

- **Unit (Mockito):** firma/claims/TTL del JWT (clave privada de test), validación en el `JwtDecoder` del gateway, autenticación contra H2.
- **Slice web:** `@WebMvcTest` + MockMvc para `TokenController` y `HelloController`.
- **Context load:** `@SpringBootTest` por módulo (perfil de test: config server y registro Eureka desactivados → pasa en frío).
- **E2E de la cadena (obligatoria, D6):** gateway con `demo-service` stubbeado: sin token → 401; token RS256 válido → 200 `Hello world!`; token firmado con clave incorrecta → 401.
- H2 en tests: `data.sql` se ejecuta también en contexto de test (verifica el INSERT).
- Comando `./mvnw -B verify` (JaCoCo genera reporte). Sin Docker ni servicios externos.

## Boundaries

- **Always:**
  - Todo el backend dentro de `server/`; jamás tocar `webapp/`.
  - Versiones SOLO en `build-parent`; hijos sin `<version>` en dependencias gestionadas.
  - `./mvnw -B verify` en verde antes de dar tarea por hecha.
  - Orden de arranque: config → eureka → auth/demo → gateway.
  - Todo endpoint de negocio exige JWT detrás del gateway; whitelist solo: token, swagger, health (D20).
- **Ask first:**
  - Añadir dependencias/plugins no listados.
  - Cambiar puertos fijos (8888/8761/8080) o la política de aleatorios.
  - Persistencia más allá de la H2 en memoria del auth.
  - Levantar SonarQube u otra infraestructura.
  - Cualquier cambio en `webapp/`.
- **Never:**
  - Publicar la clave privada RS256 fuera de `auth-service`/config nativo DEV (todo el material criptográfico de este repo es SOLO-DEV, documentado).
  - Declarar `<version>` en dependencias gestionadas por el padre.
  - Borrar/desactivar tests rotos sin aprobación.

## Glossary

- **Maven Wrapper (`mvnw`)**: scripts versionados que usan una versión exacta de Maven en cualquier máquina. → D9.
- **Formateo (Spotless)**: estilo de código canónico; `verify` falla si no se cumple, `spotless:apply` lo arregla. → D10.
- **Cobertura (JaCoCo)**: % de líneas ejercitadas por tests (reporte HTML). Mide "cuánto tocan los tests", no calidad. → D11.
- **Eureka solitario**: 1 instancia en vez de clúster de alta disponibilidad. → D12.
- **`--release 21`**: no instala nada; con tu JDK 25 fija bytecode/APIs a Java 21 (LTS estándar junto a Boot 3.5). → D13.
- **`sub`/`iss`/`exp`**: *subject* = a quién se emite (usuario), *issuer* = quién lo emitió (`auth-service`), *expiration* = caducidad (configurable vía config server, D19).
- **HS256 vs RS256**: HS256 = secreto compartido (el que valida puede firmar); RS256 = par de claves (solo `auth-service` firma). → D14.
- **SonarLint vs SonarQube**: SonarLint = plugin de IDE, local; SonarQube = servidor con BD e histórico (infra, fuera de alcance). → D10.
- **springdoc-openapi**: estándar OpenAPI 3 + Swagger UI en Spring; línea 2.9.x para Boot 3.5 (verificada).

## Open Questions

**Ninguna.** Delegaciones explícitas del usuario: Q7 (`--release 21`) y Q12 (swagger público agregado en gateway, opción que funciona con puertos aleatorios). Todo lo demás, respuesta explícita (Q2, Q5, Q6, Q11, Q13).

---

*Spec-driven-development: fases SPECIFY ✓ (aprobada) → PLAN ✓ (tasks/plan.md) → TASKS ✓ (tasks/todo.md) → IMPLEMENT en curso.*
