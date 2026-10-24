# astro-java-test

Monorepo with two independent, unrelated apps:

| Folder | What it is | Status |
| --- | --- | --- |
| [`webapp/`](webapp/) | **Astro 7** app (default starter, untouched) | stock template |
| [`server/`](server/) | **Spring Boot 3.5 microservices backend** (config/Eureka/auth) | in progress — T1–T8 done, T9–T11 pending |

- The backend source of truth is [`SPEC.md`](SPEC.md) (22 closed decisions).
- Progress lives in [`tasks/plan.md`](tasks/plan.md) and [`tasks/todo.md`](tasks/todo.md).
- Narrative guide of everything built: [`GUIA.md`](GUIA.md).

> The RS256 private key under `server/config-server/src/main/resources/config/auth-service.yml`
> is **DEV-ONLY** (sample local service). It has no secret value: it protects nothing real and
> generating your own takes 10 seconds (see "Generate your own key" in `server/`'s README).

## Quick start

Backend (from `server/`, in this order):

```powershell
./mvnw -B spring-boot:run -pl config-server     # 8888
./mvnw -B spring-boot:run -pl discovery-server  # 8761
./mvnw -B spring-boot:run -pl auth-service      # random port (see the log)
./mvnw -B spring-boot:run -pl demo-service      # random port (see the log)
./mvnw -B spring-boot:run -pl gateway-server    # 8080
```

Frontend (from `webapp/`):

```powershell
npm install
npm run dev        # http://localhost:4321
```

Details, JWT flow and Swagger in [`server/README.md`](server/README.md).
