# Fitness App — Backend

API REST del Sistema de Gestión de Gimnasio (Práctica 1, Análisis y Diseño de Sistemas 1 — USAC
CUNOC). Monolito modular sobre Spring Boot, con PostgreSQL y autenticación JWT con doble factor.

El contrato de la API no se transcribe aquí: lo genera SpringDoc desde el propio código y se
consulta en `/swagger-ui.html`. El diseño y la operación están en
[`Docs/Manual-Tecnico.md`](Docs/Manual-Tecnico.md).

---

## Tecnologías

| Componente | Versión | Para qué |
|---|---|---|
| Java | **25** | Lenguaje. `records` para todos los DTOs |
| Spring Boot | **4.1.0** | Parent POM; gestiona las versiones no fijadas abajo |
| Spring Web MVC | (gestionada) | Controladores REST sobre Tomcat embebido |
| Spring Data JPA + Hibernate | (gestionada) | Persistencia. `ddl-auto: validate` |
| Spring Security | (gestionada) | Cadena de filtros, BCrypt, reglas por ruta y rol |
| JJWT | **0.13.0** | Emisión y verificación del token de sesión (HS256) |
| Spring Validation | (gestionada) | Bean Validation sobre los cuerpos de petición |
| Spring Mail | (gestionada) | Envío de códigos 2FA y de recuperación |
| SpringDoc OpenAPI | **3.1.0** | `/swagger-ui.html` y `/v3/api-docs` |
| Apache POI | **5.4.1** | Exportación de reportes a XLSX |
| OpenPDF | **2.0.3** | Exportación de reportes a PDF |
| PostgreSQL | **17** (`postgres:17-alpine`) | Base de datos |
| Lombok | (gestionada) | `@Getter/@Setter/@RequiredArgsConstructor` en entidades y servicios |
| Maven | **3.9.16** (wrapper) | Construcción. Imagen `maven:3.9-eclipse-temurin-25` |
| JUnit 5 + Mockito | (gestionada) | 89 pruebas unitarias en 14 clases, sin contexto de Spring |

PNG e imagen de reportes se generan con `ImageIO`/`Graphics2D` del JDK: **sin dependencia extra**.

---

## Desarrollo local

### Todo en contenedores

```bash
docker compose up -d --wait
```

Postgres ejecuta `src/main/resources/db/schema.sql` y `data.sql` **solo la primera vez** que se crea
el volumen de datos. La aplicación se reinicia sola cuando cambia una clase, porque el contenedor
recompila `src/main` en un bucle y `spring-boot-devtools` detecta las clases nuevas.

Para rehacer el esquema tras editar `schema.sql`, borra **solo** el volumen de datos —
`down -v` también se llevaría `maven_repo` y costaría descargar todas las dependencias de nuevo:

```bash
docker compose down
docker volume rm fitness-app-backend_postgres_data
docker compose up -d --wait
```

### Aplicación en el host, base en contenedor

Útil para adjuntar un depurador o para un ciclo de reinicio más rápido.

```bash
docker compose up -d postgres --wait   # solo la base
mvn clean test                          # pruebas unitarias, no necesitan base
mvn spring-boot:run                     # se conecta a localhost:5432
```

No hay nada que exportar. `application.yml` importa el mismo `.env` que lee compose, así que las dos
formas de ejecutar usan un único juego de credenciales; sin `.env`, aplican los valores por defecto
de `compose.yaml` y un clon recién bajado arranca tal cual.

| Dónde corre | De dónde salen las credenciales |
|---|---|
| `docker compose up` | compose expande `.env` en `SPRING_DATASOURCE_*`, que gana sobre `application.yml` |
| `mvn spring-boot:run` | Spring importa `./.env` directamente |
| Sin ninguno de los dos | `fitness / fitness_dev / fitness_db`, los defaults de ambos archivos |

### Primer inicio de sesión

`admin` / `Admin123*`, sembrado por `db/data.sql`. **Cámbialo en el servidor** con
`PUT /api/v1/users/me/password`.

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Colección de Postman: [`Docs/Fitness-App.postman_collection.json`](Docs/Fitness-App.postman_collection.json)

Dónde caen los códigos de doble factor y de recuperación depende de `.env`:

| `MAIL_USERNAME` / `MAIL_PASSWORD` | Entrega |
|---|---|
| vacíos | al log: `docker logs fitness_backend \| grep -i verification` |
| configurados | por correo, y **ya no** se escriben en el log |

Déjalos vacíos para trabajar en local. El administrador sembrado usa `admin@fitnessapp.local`, un
dominio que no existe, así que con SMTP configurado su código rebota. Los socios creados con
`POST /members` llevan una dirección real y sí lo reciben.

### Frontend contra este backend

El SPA vive en otro repositorio y llama a este servicio **cruzando origen** (`:5173` → `:8080`), así
que los dos valores tienen que casar:

| Repositorio | Variable | Valor en local |
|---|---|---|
| Backend | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
| Frontend | `VITE_API_BASE_URL` | `http://localhost:8080` |

---

## Gitflow

Tres niveles de rama, y ningún commit directo sobre las dos protegidas:

```
                    PR                     PR
rama de trabajo  ──────▶   stage   ───────────▶   main
(sale de stage)          (integración)          (producción)
```

1. **`main`** — lo que está desplegado. Solo entra por PR desde `stage`.
2. **`stage`** — rama de integración. Solo entra por PR desde una rama de trabajo.
3. **Ramas de trabajo** — se crean **desde `stage`**, nunca desde `main`.

Convención de nombres, tal como aparece en el historial: `dmaldonado/<tema>`
(`dmaldonado/iam-module`, `dmaldonado/reports-module`, `dmaldonado/final-audit`).

```bash
git checkout stage && git pull origin stage
git checkout -b dmaldonado/<tema>
# ...trabajo...
git push -u origin dmaldonado/<tema>     # abre PR contra stage
```

Qué dispara cada rama en CI (`.github/workflows/ci-cd.yml`):

| Rama | `build` | `docker-push` | `deploy` |
|---|:--:|:--:|:--:|
| rama de trabajo | sí | no | no |
| `stage` | sí | no | no |
| `main` | sí | sí | sí |

---

## Estructura de paquetes

Un paquete por dominio, y dentro de cada uno la misma forma: `controller` (HTTP), `service` (reglas
de negocio), `repository` (persistencia), `model` (entidades JPA y enums) y `dto` (contrato de
entrada y salida). **Ninguna entidad JPA sale a la red**: todo lo que cruza el borde HTTP es un
`record` de `dto`.

```
com.fitness.app
├── iam            autenticación, JWT, doble factor, cuentas de usuario
├── directory      personas, socios, empleados, entrenadores
├── membership     planes, contratación, congelamiento, renovación, vencimiento
├── access         control de acceso, visitas (check-in/out), pases de invitado
├── classes        clases grupales, sesiones, inscripción, lista de espera, asistencia
├── training       asignación de entrenador, rutinas, mediciones, notas, alertas
├── nutrition      catálogo de alimentos, comidas, meta calórica, resumen diario
├── billing        pagos, confirmación, anulación, comprobantes, promociones
├── report         los 9 reportes y su exportación a CSV/XLSX/PDF/PNG
├── notification   avisos persistidos para el socio
├── common         ErrorCode, BusinessException, GlobalExceptionHandler, ErrorResponse
└── config         SecurityConfig, JwtAuthenticationFilter, CORS, OpenAPI, GymProperties
```

Clases que conviene conocer antes de tocar nada:

| Clase | Por qué importa |
|---|---|
| `config/SecurityConfig` | **Toda** la autorización vive aquí, en matchers por ruta y método. No hay `@PreAuthorize` en el proyecto |
| `config/JwtAuthenticationFilter` | Valida el token y construye el `AuthenticatedUser` que reciben los controladores |
| `common/exception/ErrorCode` | Catálogo único: cada constante lleva su HTTP, su `suggested_action` y su mensaje |
| `common/exception/GlobalExceptionHandler` | Traduce toda falla al mismo `ErrorResponse` |
| `config/GymProperties` | Las reglas que el enunciado deja al equipo (congelamiento, cupos, márgenes) |

La autorización por fila **no** está en `SecurityConfig`: que el rol permita la ruta no basta. Un
entrenador solo ve a los socios asignados y un socio solo su propio expediente; eso lo comprueban
los servicios (`MemberService.findById(id, principal)`), que responden `TRAINER_SCOPE_VIOLATION` o
`FORBIDDEN_RESOURCE`.

---

## Convenciones

- **Idioma**: código, nombres y comentarios en inglés. Documentación en español.
- **Llaves estilo Allman**, y asignaciones alineadas verticalmente.
- **Nomenclatura**: clases y DTOs en `UpperCamelCase`; métodos y variables en `lowerCamelCase`;
  constantes en `SCREAMING_SNAKE_CASE`.
- **El payload va en `snake_case`, el campo Java en `lowerCamelCase`.** Lo resuelve
  `spring.jackson.property-naming-strategy: SNAKE_CASE` para los cuerpos. Ojo: eso **no** aplica a
  los parámetros de consulta, que llevan `@RequestParam(name = "member_id")` explícito.
- **DTOs son `record`**, uno por caso de uso, con la validación declarada en el propio record.
- **El esquema manda sobre Hibernate**: `ddl-auto: validate`. La base la define `db/schema.sql`, no
  las entidades; si una entidad y su tabla se separan, la aplicación no arranca.
- **Un solo formato de error**: todo lo que falla sale como `ErrorResponse`, y el código se decide
  únicamente en `ErrorCode`.
- **Al capturar una excepción**, se pasa como último argumento a SLF4J y sin `{}` propio, para que
  imprima la traza completa con archivo y línea.

---

## Dependencias

| Dependencia | Rol en el proyecto |
|---|---|
| **Spring Web MVC** | Los 30+ controladores REST sobre Tomcat embebido |
| **Spring Data JPA** | Repositorios y entidades. Las consultas de reportes son SQL nativo en `ReportRepository` |
| **PostgreSQL Driver** | Conexión JDBC |
| **Spring Security + JJWT** | Autenticación, BCrypt y las reglas por rol de la cadena de filtros |
| **Validation** | Bean Validation sobre los cuerpos; produce `VALIDATION_ERROR` con `field_errors` |
| **Java Mail Sender** | Códigos de doble factor y recuperación. Sin SMTP, caen al log |
| **SpringDoc OpenAPI** | `/swagger-ui.html`, entrada directa del manual técnico |
| **Apache POI + OpenPDF** | Exportación de reportes a XLSX y PDF |
| **Lombok** | Reduce el boilerplate de entidades y servicios. Excluido del jar final |
| **Spring Boot DevTools** | Reinicia la aplicación cuando cambian las clases compiladas; es lo que hace que `docker compose up -d` recoja cambios sin reconstruir |

---

## Despliegue

`docker-push` y `deploy` solo corren en `main`. El paso de despliegue **genera** el `.env` del
servidor desde los secretos del repositorio y luego levanta compose, por eso `.gitignore` mantiene
`.env` fuera de git: commitearlo haría fallar el `git pull origin main` del segundo despliegue.
`.env.example` es la plantilla versionada de ese mismo archivo.

| Secreto | ¿Obligatorio? | Qué pasa si falta |
|---|:--:|---|
| `EC2_SSH_KEY`, `SERVER_USER`, `SERVER_HOST`, `REPO_PATH` | sí | No hay conexión con el servidor |
| `POSTGRES_PASSWORD` | sí | El despliegue aborta en vez de caer al password de desarrollo |
| `POSTGRES_DB`, `POSTGRES_USER` | no | Caen a `fitness_db` y `fitness` |
| `JWT_SECRET` | sí | El despliegue aborta. También se rechaza con menos de 32 caracteres, porque HS256 fallaría al arrancar y todo inicio de sesión respondería 500 |
| `CORS_ALLOWED_ORIGINS` | no | Cae a `http://<SERVER_HOST>:5173` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | no | Sin servidor de correo: los códigos van al log |

El detalle de la infraestructura (EC2, Docker Hub, los tres jobs) está en el
[Manual Técnico](Docs/Manual-Tecnico.md), sección 8.
