# Manual Técnico — Fitness App (Backend)

Sistema de Gestión de Gimnasio · Práctica 1, Análisis y Diseño de Sistemas 1.

Este manual describe **cómo está construido y cómo se opera** el backend. El contrato de la
API, endpoint por endpoint, no se transcribe aquí: lo genera SpringDoc a partir del propio
código y se consulta en `/swagger-ui.html` (redirige a la interfaz) o en crudo en
`/v3/api-docs`. Al salir del código, siempre está sincronizado con lo que corre.

| | |
|---|---|
| Stack | Java 25 · Spring Boot 4.1.0 · PostgreSQL 17 |
| Tamaño | 292 archivos Java · 31 controladores · **128 endpoints** · 109 DTOs |
| Base de datos | **31 tablas** · 97 restricciones `CHECK` con nombre `ck_` |
| Catálogo de errores | 80 códigos en `ErrorCode` |
| Pruebas | 88 unitarias en 14 clases · colección de Postman con 456 aserciones |

---

## 1. Arquitectura

Monolito modular sobre Spring Boot. **Un paquete por dominio**, y dentro de cada uno la misma
estructura: `controller` (HTTP), `service` (reglas de negocio), `repository` (persistencia),
`model` (entidades JPA) y `dto` (contrato de entrada y salida). Ninguna entidad JPA sale a la
red: todo lo que cruza el borde HTTP es un `record` de `dto`.

```
com.fitness.app
├── iam            autenticación, JWT, doble factor, cuentas de usuario
├── directory      personas, socios, empleados, entrenadores
├── membership     planes, contratación, congelamiento, renovación
├── access         control de acceso, visitas, pases de invitado
├── classes        clases grupales, sesiones, inscripción, lista de espera
├── training       asignación de entrenador, rutinas, mediciones, notas, alertas
├── nutrition      catálogo de alimentos, comidas, meta calórica, resumen
├── billing        promociones y pagos
├── report         los 9 reportes y su exportación
├── notification   avisos persistidos para el socio
├── common         ErrorCode, BusinessException, GlobalExceptionHandler, ErrorResponse
└── config         seguridad, JWT, CORS, OpenAPI, propiedades del gimnasio
```

**Dos decisiones que explican casi todo el código:**

1. **El esquema manda sobre Hibernate.** `spring.jpa.hibernate.ddl-auto: validate`: la base la
   define `src/main/resources/db/schema.sql`, no las entidades. Si una entidad y su tabla se
   separan, la aplicación **no arranca**, que es justo lo que se quiere.
2. **Un solo formato de error.** Todo lo que falla sale como `ErrorResponse`, lo produzca una
   regla de negocio, una anotación de validación o una restricción de la base. El detalle vive
   en el §4.

### Reportes y exportación

Los 9 reportes desembocan en un único punto, `ReportExporter`, que decide el formato. Un
`ReportTable` aplana cualquier DTO de reporte en cabeceras y celdas usando el mismo
`ObjectMapper` de la aplicación, y cuatro renderizadores lo escriben. Añadir un formato es una
constante del enum y una rama; **no cuesta nada por reporte**.

| Formato | Cómo se produce | `Content-Type` |
|---|---|---|
| `JSON` | serialización normal | `application/json` |
| `CSV` | `ReportCsv`, RFC 4180 | `text/csv; charset=UTF-8` |
| `XLSX` | `ReportXlsx`, Apache POI. Los importes se escriben como **celdas numéricas**, no como texto, para poder sumarlos | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `PDF` | `ReportPdf`, OpenPDF, A4 apaisado | `application/pdf` |
| `PNG` | `ReportPng`, `ImageIO` y `Graphics2D` del JDK, **sin dependencia** | `image/png` |

`GET /api/v1/reports/{reporte}?format=XLSX`. El valor no distingue mayúsculas y uno desconocido
responde `400 VALIDATION_ERROR`.

---

## 2. Modelo de datos

31 tablas. El diagrama completo está en `src/main/resources/db/schema.dbml` (formato DBML,
se abre en dbdiagram.io); el DDL autoritativo es `schema.sql`.

| Dominio | Tablas |
|---|---|
| Identidad | `person`, `app_user`, `verification_code` |
| Directorio | `member`, `employee`, `trainer`, `trainer_specialty` |
| Membresías | `membership_plan`, `membership`, `membership_freeze` |
| Acceso | `facility_visit`, `guest_pass` |
| Facturación | `promotion`, `payment` |
| Clases | `group_class`, `class_session`, `class_enrollment`, `waitlist_entry`, `class_rating` |
| Entrenamiento | `trainer_assignment`, `exercise`, `routine`, `routine_exercise`, `progress_measurement`, `trainer_note`, `trainer_alert` |
| Nutrición | `food`, `meal`, `meal_item`, `nutrition_goal` |
| Notificaciones | `notification` |

**Reglas que viven en la base**, no en el código, porque son invariantes que ninguna ruta debe
poder violar: 97 `CHECK` con nombre (`ck_membership_dates`, `ck_promotion_pct`,
`ck_food_macros`…), la restricción `uq_group_class_slot` (un entrenador no puede tener dos
clases el mismo día a la misma hora) y el **índice único parcial** `uq_membership_in_force`,
que impide dos contratos vigentes por socio precisamente porque solo aplica a las filas
`ACTIVE` o `FROZEN`.

Cuando una de esas reglas salta, `GlobalExceptionHandler` la traduce a `409 CONSTRAINT_VIOLATION`
y escribe el nombre de la restricción **en el log, nunca en la respuesta**.

> **Nota de operación:** no hay Flyway ni Liquibase. `schema.sql` y `data.sql` los ejecuta el
> contenedor de Postgres **solo cuando el volumen está vacío**. Cambiar el esquema obliga a
> recrear el volumen `postgres_data`, y con `ddl-auto: validate` un despliegue contra un
> esquema viejo falla al arrancar en vez de corromper datos.

---

## 3. Seguridad

**Autenticación por JWT.** `POST /auth/login` devuelve un token de **60 minutos**
(`app.security.jwt.access-minutes`). `JwtAuthenticationFilter` lo valida en cada petición y
`SecurityConfig` decide el acceso por ruta.

**Cuatro roles:** `ADMIN`, `RECEPTIONIST`, `TRAINER`, `MEMBER`. Las reglas se declaran una sola
vez, en `SecurityConfig`; por ejemplo `/api/v1/reports/**` es exclusivo de `ADMIN`.

**Doble factor.** Si la cuenta lo tiene activo, el login responde `202` con un `challenge_id` y
un destino enmascarado (`a***n@fitnessapp.local`) en lugar del token; el token se emite al
verificar el código en `POST /auth/challenges/{id}/verifications`.

- Código de 6 dígitos generado con `SecureRandom`.
- Se guarda **con BCrypt**, nunca en claro.
- Vence a los **10 minutos** y admite **3 intentos**; agotados, el código pasa a `EXPIRED` y la
  respuesta sugiere `RESEND_CODE`.

**Autorización por fila.** Que el rol permita la ruta no basta: un entrenador solo ve a los
socios que tiene asignados y un socio solo su propio expediente. Los servicios lo comprueban al
leer, y devuelven `TRAINER_SCOPE_VIOLATION` o `FORBIDDEN_RESOURCE`.

---

## 4. Manejo de errores

Toda respuesta de error tiene la misma forma, con los campos nulos omitidos:

```json
{
  "error_code": "MEMBERSHIP_EXPIRED",
  "message": "La membresía venció.",
  "suggested_action": "RENEW_MEMBERSHIP",
  "timestamp": "2026-08-13T06:18:44.238Z",
  "path": "/api/v1/trainer-assignments",
  "field_errors": { "start_date": "must be a date in the present or in the future" },
  "trace_id": "406606d6-…"
}
```

`ErrorCode` es el catálogo completo: cada constante lleva su estado HTTP, su
`suggested_action` y su mensaje, y ese es el **único** sitio donde se decide el código de una
falla. `trace_id` aparece solo en el `500`, que es el único caso en el que hace falta cruzar la
respuesta con el log.

| Situación | Handler | Respuesta |
|---|---|---|
| Regla de negocio | `handleBusiness` | la del `ErrorCode` |
| Anotación de validación | `handleValidation` | `400` + `field_errors` |
| JSON ilegible | `handleUnreadableBody` | `400` |
| Parámetro inválido o ausente | `handleInvalidParameter` | `400` |
| Ruta inexistente | `handleUnknownRoute` | `404 ROUTE_NOT_FOUND` |
| `CHECK` o índice único | `handleDataIntegrity` | `409 CONSTRAINT_VIOLATION` |
| Cualquier otra cosa | `handleUnexpected` | `500` + `trace_id` |

En todos, la excepción se pasa **como último argumento a SLF4J y sin `{}` propio**: así SLF4J
imprime la traza completa, que es la que lleva archivo y número de línea de cada marco.

---

## 5. Procesos programados

Dos tareas `@Scheduled` en `MembershipService`. Ningún endpoint las dispara: se demuestran con
sus pruebas unitarias.

| Cron | Método | Qué hace |
|---|---|---|
| `0 5 0 * * *` (00:05) | `expireMemberships()` | Marca `EXPIRED` los contratos que llegaron a su fecha de fin, cancela sus inscripciones futuras y notifica al socio |
| `0 0 7 * * *` (07:00) | `notifyExpiringMemberships()` | Avisa a quienes vencen dentro de `gym.membership.expiry-notice-days` (5 por defecto) |

Como la columna `status` solo se pone al día a las 00:05, **el reporte de membresías deriva el
estado de `end_date`** en lugar de leer la columna. Los servicios de negocio también verifican
por fecha, de modo que el control de acceso es correcto aunque la columna vaya con retraso.

---

## 6. Políticas parametrizables

Las reglas que el enunciado deja al equipo están en `application.yml`, bajo `gym:`, y no
repartidas por el código:

```yaml
gym:
  freeze:      { max-days-per-cycle: 15, max-count-per-cycle: 2, cycle-days: 90 }
  guest-pass:  { max-free-per-person: 1 }
  classes:     { cancellation-window-hours: 2, waitlist-confirmation-minutes: 60 }
  membership:  { expiry-notice-days: 5 }
  nutrition:   { default-tolerance-percent: 10 }
```

---

## 7. Despliegue

**Todo en contenedores** (`compose.yaml`): `fitness_postgres` (postgres:17-alpine, volumen
`postgres_data`) y `fitness_backend`. La imagen se construye con el `Dockerfile` sobre
`maven:3.9-eclipse-temurin-25`.

```bash
docker compose up -d --build
```

**Aplicación en el host y base en contenedor**, para desarrollar:

```bash
docker compose up -d postgres
mvn spring-boot:run
```

`application.yml` importa `./.env` si existe, así que `mvn spring-boot:run` toma las mismas
credenciales que compose sin exportar nada. Las variables mínimas están en `.env.example`;
`JWT_SECRET` debe tener al menos 32 bytes.

**Integración y entrega continuas** (`.github/workflows/ci-cd.yml`): `build` compila y corre las
pruebas en cada push; en `main`, `docker-push` publica la imagen y `deploy` la despliega por SSH.
El job de despliegue verifica que `POSTGRES_PASSWORD` y `JWT_SECRET` existan y tengan longitud
suficiente antes de escribir el `.env` del servidor.

---

## 8. Pruebas

| Qué | Cómo se corre | Estado |
|---|---|---|
| 88 pruebas unitarias, 14 clases | `mvn test` | 88/88, `BUILD SUCCESS` |
| Colección de Postman, 456 aserciones | `node scripts/validate-api.js` | 453/456 |

Las unitarias son JUnit 5 con Mockito **sin contexto de Spring**: se ejecutan en segundos y
prueban reglas de negocio, no cableado. La colección
(`Docs/Fitness-App.postman_collection.json`) recorre la API contra un backend vivo; el runner
añade en memoria un script que rellena los códigos de doble factor leyéndolos del log, porque
son datos que solo conoce el usuario.

---

## 9. Limitaciones conocidas

Se declaran de frente porque son preguntas previsibles:

1. **El canal SMS es un apéndice sin implementar.** `VerificationCodeService` escribe el código
   al log con el motivo `"no SMS provider is configured"`. **El canal de correo sí envía.**
2. **Las tareas programadas no se pueden disparar por HTTP.** Añadir un endpoint solo para
   demostrarlas sería una función que el sistema no necesita; la evidencia son sus pruebas
   unitarias.
3. **`meal_item` no guarda copia de los macros**: los recalcula contra la fila actual de `food`.
   Para que corregir el catálogo no reescriba comidas ya registradas, **los valores
   nutricionales de un alimento ya consumido son inmutables** (`409 FOOD_IN_USE`); el nombre, el
   código y la categoría sí se pueden corregir. La alternativa —copiar los macros a cada línea—
   exige cambiar el esquema, y sin herramienta de migración eso rompe el despliegue.
4. **Sin migraciones.** Ver la nota del §2.
5. **Tres requests de la colección fallan por su propio diseño**, no por el backend: una necesita
   un entrenador con la carga agotada, otra apunta a un id de comida quemado y la tercera exige
   una membresía inactiva cuando todo el módulo de nutrición requiere una activa.
