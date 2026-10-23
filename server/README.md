# server — esqueleto de microservicios Spring Boot

Backend de ejemplo con Spring Boot **3.5.16** (LTS) + Spring Cloud **2025.0.3**: configuración centralizada, descubrimiento Eureka, autenticación JWT **RS256** y QA de build completo. El objetivo es que se parezca a una app empresarial real sin necesitar nada externo (sin Docker, sin repo git de config, sin SonarQube).

> **Estado:** tareas **T1–T6** completas (parent + config + Eureka + auth con JWT). Quedan **T7–T11** (demo-service, gateway, Swagger agregado). El flujo JWT completo *por el gateway* llega en T8–T10.

## Arquitectura

Multi-módulo Maven. Un único `build-parent` (raíz) fija **todas** las versiones; los módulos hijos no declaran `<version>`.

| Módulo | Puerto | Responsabilidad |
| --- | --- |---|
| `build-parent` | — | `server/pom.xml`: única fuente de versiones + QA heredado |
| `config-server` | **8888** | Spring Cloud Config (backend *native*: config por código en este repo) |
| `discovery-server` | **8761** | Eureka (1 nodo, solitario) |
| `auth-service` | **aleatorio** | H2 en memoria + JWT RS256 firmado; TTL configurable por config server |
| `demo-service` | aleatorio | `GET /api/demo/hello` → `"Hello world!"` (pendiente: T7) |
| `gateway-server` | **8080** | Única puerta de entrada; valida JWT y sirve Swagger agregado (pendiente: T8–T10) |

Orden de arranque: **config → eureka → auth/demo → gateway**. El cliente solo ve el `8080`; auth y demo se descubren por nombre vía Eureka, con puerto aleatorio.

## Requisitos

- JDK 25 (compilamos con `--release 21`, así que el bytecode es Java 21).
- Nada más: el Maven Wrapper (`./mvnw`) descarga la versión exacta de Maven (3.9.16) sola.

## Arranque

```powershell
# desde server/
./mvnw -B spring-boot:run -pl config-server      # 8888
./mvnw -B spring-boot:run -pl discovery-server   # 8761
./mvnw -B spring-boot:run -pl auth-service       # puerto aleatorio → mira el log o Eureka
```

Eureka queda en `http://localhost:8761/` y muestra `AUTH-SERVICE` con su puerto aleatorio.

> `spring-boot:run` lanza un proceso hijo. Si reinicias, mata el proceso por el **puerto que escucha** (no solo el wrapper Maven) o quedará un JVM huérfano.

## Build y QA

```powershell
./mvnw -B verify                 # compila + tests + formateo + análisis (la verificación principal)
./mvnw -B verify -pl auth-service -am   # un módulo concreto + sus dependencias
./mvnw spotless:apply            # formatear a mano
```

`verify` ejecuta cuatro puertas de calidad: **Spotless** (google-java-format 1.36.1), **Checkstyle** (reglas en `config/checkstyle.xml`), **SpotBugs + FindSecBugs** (análisis estático con reglas de seguridad) y **JaCoCo** (reporte de cobertura en `target/site/jacoco/`).

## Probar el auth (hoy, puerto directo)

`POST /api/auth/token` con el usuario sembrado `demo`/`demo`:

```powershell
# Swagger UI en el navegador: http://localhost:<puerto-auth>/swagger-ui.html
# o por CLI (sustituye <auth> por el puerto del log):
Invoke-RestMethod -Method Post -Uri http://localhost:<auth>/api/auth/token `
  -Body '{"username":"demo","password":"demo"}' -ContentType 'application/json'
```

Devuelve un JWT RS256. Contraseña incorrecta → `401` y `{"code":401,"message":"Invalid credentials",...}`. Cuando exista el gateway (T8+), todo esto se hace contra `http://localhost:8080`.

## Generar tu propia clave RS256 (opcional)

La clave que viene en el repo es SOLO-DEV. Para usar la tuya:

1. **Genera el par** (JDK 25, sin instalar nada):

   ```powershell
   openssl genrsa -out priv.pem 2048
   openssl rsa -in priv.pem -pubout -out pub.pem
   ```

   o solo con Java (single-file, sin OpenSSL):

   ```powershell
   java C:\path\a\GenRsa.java   # genera PKCS#8 (privada) + X.509 (pública) en PEM
   ```

2. **Pega la privada** en `config-server/src/main/resources/config/auth-service.yml`, en `app.jwt.private-key`, respetando los guiones `-----BEGIN/END PRIVATE KEY-----`.

3. **Añade la pública** (aún pendiente de T8–T9): en `config/gateway-server.yml` la usará el resource-server para verificar la firma.

4. **Reinicia** `config-server` y `auth-service`. Si la clave no la lee bien el config, el auth degrada a config local y arranca igualmente (modo `optional:configserver`).

> Material criptográfico marcado **SOLO-DEV**: no usar estas claves en producción. Rotación real / JWKS quedó descartado a propósito (D21) por el choque con los puertos aleatorios.

## Cambiar la TTL del JWT (sin recompilar)

1. Edita `app.jwt.expiration-seconds` en `config-server/src/main/resources/config/auth-service.yml` (por defecto `3600`).
2. Reinicia `config-server` y `auth-service`.
3. Pide un token: `exp − iat` refleja el nuevo valor. **Criterio de éxito 4** de la SPEC, comprobado (3600 → 1800 → 3600).

## Estructura

```text
server/
├── pom.xml                     # build-parent (única fuente de versiones)
├── mvnw, mvnw.cmd, .mvn/       # wrapper versionado
├── lombok.config
├── config/checkstyle.xml       # reglas de estilo
├── config-server/
│   └── src/main/resources/config/   # repo NATIVO de configuración
│       ├── application.yml         # común (Eureka)
│       └── auth-service.yml        # par RS256 + expiration-seconds + H2
├── discovery-server/
└── auth-service/
    └── src/main/resources/         # application.yml, schema.sql, data.sql
```

Diseño por capas hexagonales (D2): `adapter.in.web` (HTTP) → `application.service` (casos de uso y puertos) → `adapter.out` (JPA). La clave privada jamás sale del config nativo.
