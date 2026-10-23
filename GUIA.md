# GUIA — Qué se ha construido y por qué (día 1 → hoy)

Esta guía está pensada para que puedas **explicar con tus palabras** lo que hay en este repo.
Está escrita en español, sin jerga innecesaria, y ordenada como se construyó.

---

## 1. La idea global en 30 segundos

Monté un **esqueleto de backend de microservicios con Spring Boot**, parecido a lo que se usa en una empresa real, pero minimalista y sin necesitar nada externo (sin Docker, sin base de datos instalada, sin servidores).

El flujo que se quiere lograr es este:

1. Un cliente pide un **token** enviando usuario y contraseña.
2. El servidor de autenticación (**auth**) valida y devuelve un **JWT** firmado.
3. El cliente usa ese token para llamar a un servicio protegido (**demo**).
4. Sin token válido → **401**. Con token → **200 "Hello world!"**.

Ahora mismo (día 1) están las **bases** de eso: el sistema de configuración, el de descubrimiento y el servicio de autenticación que emite tokens. Falta el "cerebro" que une todo (el gateway) y el servicio demo.

---

## 2. Los tres primeros servicios que funcionan

Un proyecto Spring "de empresa" normalmente no es una única aplicación, sino varias aplicaciones pequeñas que se comunican. Estas son las que ya viven en `server/`:

| Servicio | Puerto | Qué hace | Por qué existe |
| --- | --- | --- | --- |
| **config-server** | 8888 | Guarda la configuración de todos los demás servicios **en un solo sitio** (aquí, en ficheros dentro del propio repo) | Evita tener la config repartida y cambiarla sin recompilar |
| **discovery-server** | 8761 | Es **Eureka**: un "teléfono" donde los servicios anuncian su dirección | Como el auth usa un puerto aleatorio, hay que poder localizarlo |
| **auth-service** | aleatorio | Validar usuario/contraseña y **firmar JWT** | Es el que emite los tokens |

### El orden de arranque importa

```
config-server  →  discovery-server  →  auth-service (y más adelante demo y gateway)
```

- El **config-server** va primero porque los demás le piden su configuración.
- El **discovery-server** va segundo porque los servicios le dicen "aquí estoy".
- El **auth** arranca después y necesita a los dos anteriores.

---

## 3. Las decisiones importantes (y por qué)

Estas son las que se tomaron y conviene poder justificar:

- **Spring Boot 3.5 LTS + Spring Cloud 2025.0.3**: versión estable y soportada a largo plazo. Es la combinación oficial compatible entre sí.
- **Config centralizado ("config nativo")**: en lugar de un servidor de configuración externo, la config vive en ficheros dentro del repo (`server/config-server/src/main/resources/config/`). Simple y sin dependencias.
- **Puertos aleatorios en auth y demo**: en producción los servicios van y vienen, así que no se les fija puerto. El gateway los encuentra **por nombre** gracias a Eureka.
- **JWT con RS256 (par de claves)**: la **clave privada** solo la tiene el auth (para firmar); la **pública** la usa quien recibe el token (para verificar). A diferencia de HS256, quien verifica no puede firmar. Es más seguro.
- **La TTL del token es configurable**: cuánto tarda en caducar un token (`exp`) no está escrito en el código, viene de la configuración. Cambias un número, reinicias el auth, y los tokens ya caducan antes. Sin recompilar.
- **QA en el build**: el build no solo compila, también **formatea, revisa estilo, busca errores y mide cobertura de tests**. Si algo no cumple, el build falla.

---

## 4. Cómo funciona el auth por dentro (la parte más "de empresa")

El servicio de autenticación está escrito siguiendo un patrón de diseño llamado **arquitectura hexagonal** (separar la lógica de negocio de los detalles técnicos). Se ve en los paquetes:

```
com.demo.authservice/
├── adapter/in/web/          → "por dónde entra" (HTTP): el controlador, la seguridad, los DTO
├── application/service/     → "el cerebro": la lógica de emitir tokens y los puertos (interfaces)
└── adapter/out/             → "por dónde sale" (datos): la parte de base de datos (JPA/H2)
```

El flujo de emitir un token:

1. Llega `POST /api/auth/token` con `{"username":"demo","password":"demo"}`.
2. El **TokenService** (lógica de negocio) valida la contraseña contra la base de datos H2 en memoria (que arranca con un usuario `demo` precargado con hash BCrypt).
3. Si es correcta, **firma un JWT** con la clave privada y los claims `sub` (usuario), `roles`, `iss` (quién lo emitió) y `exp` (caducidad según configuración).
4. Devuelve el token. Si la contraseña es mala → **401** con un cuerpo de error consistente.

> Nota de seguridad: la "base de datos" es **H2 en memoria** — se borra al reiniciar. Es un ejemplo, no persistencia real.

---

## 5. Los dos "trucos" que conviene poder explicar

### a) El token firmado (JWT RS256)

Un JWT es un texto con tres partes separadas por puntos: `cabecera.datos.firma`.
- La **cabecera** dice "esto es RS256".
- Los **datos** (payload) son los claims: quién, de quién, para cuándo.
- La **firma** la calcula el auth con su clave privada. Cualquiera con la clave pública puede comprobar que nadie lo ha tocado.

### b) La configuración que viaja por red

Los servicios no tienen su configuración escrita en su propio `application.yml`; la **preguntan al config-server** al arrancar. Así, para cambiar la caducidad del token solo se edita un fichero del config-server y se reinicia el auth. Ese es exactamente el "criterio de éxito 4" que se verificó: cambiar 3600 → 1800 segundos y verlo reflejado sin tocar código.

---

## 6. Dónde estamos y qué falta

**Hecho (día 1):**
- `build-parent` (el padre Maven que fija todas las versiones en un solo sitio)
- `config-server` (config centralizada)
- `discovery-server` (Eureka)
- `auth-service` completo: H2 con usuario demo, emisión de JWT RS256, TTL configurable, tests y QA en verde
- READMEs de raíz, `server/` y `webapp/`
- Verificación manual: token emitido y verificado (firma RS256 válida), TTL cambiada sin recompilar

**Pendiente:**
- **demo-service** — el `GET /api/demo/hello` que devolverá "Hello world!"
- **gateway-server** — la puerta única de entrada (8080) que valida el JWT y enruta
- **Swagger UI agregado** — para probar todo desde el navegador sin Postman
- **README final + criterios de éxito globales**

---

## 7. Cómo arrancarlo y probarlo

```powershell
# desde server/
./mvnw -B spring-boot:run -pl config-server      # 8888
./mvnw -B spring-boot:run -pl discovery-server   # 8761
./mvnw -B spring-boot:run -pl auth-service       # puerto aleatorio (míralo en el log o en Eureka :8761)
```

Pedir un token:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:<auth>/api/auth/token `
  -Body '{"username":"demo","password":"demo"}' -ContentType 'application/json'
```

O desde el navegador: `http://localhost:<auth>/swagger-ui.html`.

---

## 8. Un par de "gotchas" que aprendimos por el camino

- **JDK 25 y Lombok**: desde JDK 23, `javac` ya no ejecuta automáticamente el procesador de anotaciones. Se arregló con `<proc>full</proc>` en el padre. Sin eso, los getters/setters de Lombok no se generan y los tests fallan por métodos que "no existen".
- **google-java-format**: una versión más nueva (1.37) rompió el plugin de formato; se fijó a 1.36.1. Lección: en builds reproducibles se fijan versiones, no se dejan flotando.
- **`spring-boot:run` crea un proceso hijo**: al reiniciar, hay que matar por el **puerto** que escucha, no solo el proceso de Maven, o queda un JVM fantasma ocupando el puerto.

---

*Guía de apoyo al proyecto. La fuente de verdad técnica es `SPEC.md`; el detalle por tarea, `tasks/todo.md`.*
