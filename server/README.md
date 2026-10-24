# server — Spring Boot microservices skeleton

Sample backend with Spring Boot **3.5.16** (LTS) + Spring Cloud **2025.0.3**: centralized
configuration, Eureka discovery, JWT **RS256** authentication and full build QA. The goal is to
look like a real enterprise app while needing nothing external (no Docker, no config git repo,
no SonarQube).

> **Status:** tasks **T1–T8** complete (parent + config + Eureka + auth with JWT + demo-service +
> gateway routes). Remaining: **T9–T11** (JWT validation at the gateway, aggregated Swagger UI,
> final README pass). The full JWT flow *through the gateway* lands in T9–T10.

## Architecture

Multi-module Maven. A single `build-parent` (root) pins **every** version; child modules declare
no `<version>`.

| Module | Port | Responsibility |
| --- | --- | --- |
| `build-parent` | — | `server/pom.xml`: single source of versions + inherited QA |
| `config-server` | **8888** | Spring Cloud Config (*native* backend: config as code in this repo) |
| `discovery-server` | **8761** | Eureka (single node, solitary) |
| `auth-service` | **random** | In-memory H2 + signed RS256 JWT; TTL configurable via config server |
| `demo-service` | random | `GET /api/demo/hello` → `"Hello world!"` |
| `gateway-server` | **8080** | Single entry point; routes via Eureka and serves aggregated Swagger (T9–T10) |

Startup order: **config → eureka → auth/demo → gateway**. The client only talks to `8080`; auth
and demo are discovered by name through Eureka, on random ports.

## Requirements

- JDK 25 (we compile with `--release 21`, so the bytecode targets Java 21).
- Nothing else: the Maven Wrapper (`./mvnw`) downloads the exact Maven version (3.9.16) itself.

## Start

```powershell
# from server/
./mvnw -B spring-boot:run -pl config-server      # 8888
./mvnw -B spring-boot:run -pl discovery-server   # 8761
./mvnw -B spring-boot:run -pl auth-service       # random port → check the log or Eureka
./mvnw -B spring-boot:run -pl demo-service       # random port → check the log or Eureka
./mvnw -B spring-boot:run -pl gateway-server     # 8080
```

Eureka lives at `http://localhost:8761/` and shows `AUTH-SERVICE` and `DEMO-SERVICE` with their
random ports.

> `spring-boot:run` forks a child process. When restarting, kill the process by its **listening
> port** (not just the Maven wrapper) or an orphan JVM will linger.

## Build and QA

```powershell
./mvnw -B verify                 # compiles + tests + formatting + analysis (the main check)
./mvnw -B verify -pl auth-service -am   # a concrete module + its dependencies
./mvnw spotless:apply            # format by hand
```

`verify` runs four quality gates: **Spotless** (google-java-format 1.36.1), **Checkstyle**
(rules in `config/checkstyle.xml`), **SpotBugs + FindSecBugs** (static analysis with security
rules) and **JaCoCo** (coverage report in `target/site/jacoco/`).

## Try the auth (today, direct port)

`POST /api/auth/token` with the seeded user `demo`/`demo`:

```powershell
# Swagger UI in the browser: http://localhost:<auth-port>/swagger-ui.html
# or CLI (replace <auth> with the port from the log):
Invoke-RestMethod -Method Post -Uri http://localhost:<auth>/api/auth/token `
  -Body '{"username":"demo","password":"demo"}' -ContentType 'application/json'
```

It returns an RS256 JWT. Wrong password → `401` and `{"code":401,"message":"Invalid credentials",...}`.
Once the gateway exists (T8+), do all of this against `http://localhost:8080`.

## Generate your own RS256 key (optional)

The key shipped in the repo is DEV-ONLY. To use your own:

1. **Generate the pair** (JDK 25, nothing to install):

   ```powershell
   openssl genrsa -out priv.pem 2048
   openssl rsa -in priv.pem -pubout -out pub.pem
   ```

   or Java-only (single-file, no OpenSSL):

   ```powershell
   java C:\path\to\GenRsa.java   # writes PKCS#8 (private) + X.509 (public) PEMs
   ```

2. **Paste the private key** into `config-server/src/main/resources/config/auth-service.yml`,
   under `app.jwt.private-key`, keeping the `-----BEGIN/END PRIVATE KEY-----` markers.

3. **Add the public key** (still pending, T9): `config/gateway-server.yml` will use it in the
   resource server to verify signatures.

4. **Restart** `config-server` and `auth-service`. If the config cannot read the key, auth
   degrades to local config and still starts (mode `optional:configserver`).

> Cryptographic material marked **DEV-ONLY**: never use these keys in production. Real rotation /
> JWKS was deliberately dropped (D21) because it clashes with the random ports.

## Change the JWT TTL (no recompile)

1. Edit `app.jwt.expiration-seconds` in `config-server/src/main/resources/config/auth-service.yml`
   (default `3600`).
2. Restart `config-server` and `auth-service`.
3. Request a token: `exp − iat` reflects the new value. **Success criterion 4** of the SPEC,
   already verified (3600 → 1800 → 3600).

## Structure

```text
server/
├── pom.xml                     # build-parent (single source of versions)
├── mvnw, mvnw.cmd, .mvn/       # versioned wrapper
├── lombok.config
├── config/checkstyle.xml       # style rules
├── config-server/
│   └── src/main/resources/config/   # NATIVE config repository
│       ├── application.yml         # shared (Eureka)
│       ├── auth-service.yml        # RS256 pair + expiration-seconds + H2
│       ├── demo-service.yml
│       └── gateway-server.yml      # routes (public key lands in T9)
├── discovery-server/
├── auth-service/
│   └── src/main/resources/         # application.yml, schema.sql, data.sql
├── demo-service/
└── gateway-server/
```

Hexagonal layered design (D2): `adapter.in.web` (HTTP) → `application.service` (use cases and
ports) → `adapter.out` (JPA). The private key never leaves the native config.
