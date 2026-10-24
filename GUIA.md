# GUIA — What was built and why (day 1 → today)

This guide is meant to help you **explain in your own words** what this repo contains.
It is ordered the same way it was built.

---

## 1. The global idea in 30 seconds

I built a **Spring Boot microservices backend skeleton**, similar to what is used in a real
company, but minimal and needing nothing external (no Docker, no installed database, no servers).

The flow we want to reach:

1. A client requests a **token** sending username and password.
2. The authentication service (**auth**) validates it and returns a signed **JWT**.
3. The client uses that token to call a protected service (**demo**).
4. No valid token → **401**. With token → **200 "Hello world!"**.

Right now (day 1) the **foundations** of that are in place: the configuration system, the
discovery system and the authentication service that issues tokens. What is missing is the
"brain" that ties everything together (the gateway) and the demo service.

---

## 2. The first three services that work

A company Spring project is usually not a single application but several small ones that talk to
each other. These are the ones already living in `server/`:

| Service | Port | What it does | Why it exists |
| --- | --- | --- | --- |
| **config-server** | 8888 | Holds the configuration of every other service **in one place** (here, as files in this repo) | Avoids scattered config; change it without recompiling |
| **discovery-server** | 8761 | This is **Eureka**: a "phone book" where services announce their address | Since auth uses a random port, something must be able to locate it |
| **auth-service** | random | Validates username/password and **signs JWTs** | It issues the tokens |

### The startup order matters

```
config-server  →  discovery-server  →  auth-service (later demo and gateway)
```

- **config-server** goes first because the others ask it for their configuration.
- **discovery-server** goes second because services tell it "here I am".
- **auth** starts afterwards and needs both previous ones.

---

## 3. The important decisions (and why)

These are the ones that were taken and are worth being able to justify:

- **Spring Boot 3.5 LTS + Spring Cloud 2025.0.3**: stable, long-term supported. It is the
  officially compatible combination.
- **Centralized config ("native")**: instead of an external config server, the config lives as
  files in this repo (`server/config-server/src/main/resources/config/`). Simple, no dependencies.
- **Random ports on auth and demo**: in production services come and go, so no fixed port is
  assigned. The gateway finds them **by name** through Eureka.
- **JWT with RS256 (key pair)**: the **private key** only belongs to auth (to sign); the **public
  key** is used by whoever receives the token (to verify). Unlike HS256, whoever verifies cannot
  sign. More secure.
- **The token TTL is configurable**: how long a token takes to expire (`exp`) is not written in
  the code, it comes from the configuration. Change a number, restart auth, and tokens now expire
  sooner. No recompiling.
- **QA in the build**: the build does not only compile, it also **formats, checks style, hunts
  bugs and measures test coverage**. If something does not comply, the build fails.

---

## 4. How auth works inside (the most "enterprise" part)

The authentication service is written following a design pattern called **hexagonal architecture**
(separating business logic from technical details). You can see it in the packages:

```
com.demo.authservice/
├── adapter/in/web/          → "where it comes in" (HTTP): controller, security, DTOs
├── application/service/     → "the brain": the token-issuing logic and the ports (interfaces)
└── adapter/out/             → "where it goes out" (data): the JPA/H2 part
```

The flow of issuing a token:

1. `POST /api/auth/token` arrives with `{"username":"demo","password":"demo"}`.
2. The **TokenService** (business logic) validates the password against the in-memory H2 database
   (which boots with a seeded `demo` user hashed with BCrypt).
3. If correct, it **signs a JWT** with the private key and the claims `sub` (user), `roles`, `iss`
   (who issued it) and `exp` (expiry from configuration).
4. It returns the token. Wrong password → **401** with a consistent error body.

> Security note: the "database" is **in-memory H2** — it is wiped on restart. It is a sample, not
> real persistence.

---

## 5. The two "tricks" worth explaining

### a) The signed token (JWT RS256)

A JWT is a text with three parts separated by dots: `header.payload.signature`.
- The **header** says "this is RS256".
- The **payload** holds the claims: who, from whom, until when.
- The **signature** is computed by auth with its private key. Anyone with the public key can
  verify nobody tampered with it.

### b) Configuration that travels over the network

Services do not have their configuration written in their own `application.yml`; they **ask the
config-server** at boot. That is why changing the token expiry is just editing a file in the
config server and restarting auth (exactly "success criterion 4" that was verified: 3600 → 1800
seconds and back without touching code).

---

## 6. Where we are and what is missing

**Done (day 1):**
- `build-parent` (the Maven parent that pins every version in one place)
- `config-server` (centralized config)
- `discovery-server` (Eureka)
- `auth-service` complete: H2 with the demo user, RS256 JWT issuing, configurable TTL, tests and
  green QA
- READMEs for root, `server/` and `webapp/`
- Manual verification: token issued and verified (valid RS256 signature), TTL changed without
  recompiling

**Pending:**
- **demo-service** — the `GET /api/demo/hello` that returns "Hello world!"
- **gateway-server** — the single entry point (8080) that validates the JWT and routes
- **Aggregated Swagger UI** — to test everything from the browser without Postman
- **Final README + global success criteria**

---

## 7. How to start it and try it

```powershell
# from server/
./mvnw -B spring-boot:run -pl config-server      # 8888
./mvnw -B spring-boot:run -pl discovery-server   # 8761
./mvnw -B spring-boot:run -pl auth-service       # random port (log or Eureka :8761)
```

Request a token:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:<auth>/api/auth/token `
  -Body '{"username":"demo","password":"demo"}' -ContentType 'application/json'
```

Or in the browser: `http://localhost:<auth>/swagger-ui.html`.

---

## 8. Three "gotchas" we learned along the way

- **JDK 25 + Lombok**: since JDK 23, `javac` no longer runs the annotation processor automatically.
  Fixed with `<proc>full</proc>` in the parent; without it the Lombok getters/setters are not
  generated and tests fail on methods that "do not exist".
- **google-java-format**: a newer version (1.37) broke the formatting plugin; it was pinned to
  1.36.1. Lesson: in reproducible builds, pin versions instead of leaving them floating.
- **`spring-boot:run` forks a child process**: when restarting, kill by the **listening port**, not
  only the Maven process, or a ghost JVM will keep the port.

---

*Supporting guide for the project. The technical source of truth is `SPEC.md`; per-task detail,
`tasks/todo.md`.*
