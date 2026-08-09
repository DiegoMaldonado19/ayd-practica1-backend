# ayd-practica1-backend

## Running it

### Everything in containers

```bash
docker compose up -d --wait
```

Postgres runs `db/schema.sql` and `db/data.sql` the first time the data volume is
created, and the application restarts on its own when a class changes.

To rebuild the schema after editing `schema.sql`, drop **only** the data volume:
`down -v` would also take `maven_repo` and cost a full dependency download.

```bash
docker compose down
docker volume rm fitness-app-backend_postgres_data
docker compose up -d --wait
```

### Application on the host, database in a container

Useful for attaching a debugger or for a faster restart loop.

```bash
docker compose up -d postgres --wait   # only the database
mvn clean test                          # unit tests, no database needed
mvn spring-boot:run                     # reaches localhost:5432
```

Nothing to export and nothing to configure. `application.yml` imports the same
`.env` that compose reads, so both ways of running use one single set of
credentials; without a `.env`, the defaults of `compose.yaml` apply and a fresh
clone starts as it is.

| Where it runs | Where the credentials come from |
|---|---|
| `docker compose up` | compose expands `.env` into `SPRING_DATASOURCE_*`, which outranks `application.yml` |
| `mvn spring-boot:run` | Spring imports `./.env` directly |
| Neither file present | `fitness / fitness_dev / fitness_db`, the defaults in both files |

### First sign-in

`admin` / `Admin123*`, seeded by `db/data.sql`. Change it on the server with
`PUT /api/v1/users/me/password`.

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Postman collection: [`Docs/Fitness-App-IAM.postman_collection.json`](Docs/Fitness-App-IAM.postman_collection.json)

Where the two-factor and recovery codes end up depends on `.env`:

| `MAIL_USERNAME` / `MAIL_PASSWORD` | Delivery |
|---|---|
| empty | written to the application log: `docker logs fitness_backend \| grep -i verification` |
| set | sent by mail, and **no longer written to the log** |

Leave them empty for local work. The bootstrap administrator carries
`admin@fitnessapp.local`, a domain that does not exist, so with SMTP configured its
code leaves towards a dead address and the message bounces. Members created through
`POST /members` carry a real address and do receive it.

## Deployment

`docker-push` and `deploy` only run on `main`. The deploy step writes the server's
`.env` from the repository secrets and then brings compose up, so **the file on the
server is generated, never pulled** — which is why `.gitignore` keeps `.env` out of
git. Committing it would make `git pull origin main` fail on the second deploy.

| Secret | Required | What happens if it is missing |
|---|:--:|---|
| `EC2_SSH_KEY`, `SERVER_USER`, `SERVER_HOST`, `REPO_PATH` | yes | No connection to the server. |
| `POSTGRES_PASSWORD` | yes | The deploy aborts instead of falling back to the dev password. |
| `POSTGRES_DB`, `POSTGRES_USER` | no | Fall back to `fitness_db` and `fitness`. |
| `JWT_SECRET` | yes | The deploy aborts. It is also rejected under 32 characters, because HS256 would then fail at startup and every sign-in would answer 500. |
| `CORS_ALLOWED_ORIGINS` | no | Falls back to `http://<SERVER_HOST>:5173`. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | no | No mail server: the verification codes go to the application log. |

`.env.example` is the committed template of that same file.

## Dependencies

### Spring Boot DevTools - DEVELOPER TOOLS
Restarts the running application when the compiled classes change, which is what
makes `docker compose up -d` pick up code changes without a rebuild.

### Spring Web - WEB
Build web, including RESTful, applications using Spring MVC. Uses Apache Tomcat
as the default embedded container.

### Lombok - DEVELOPER TOOLS
Java annotation library which helps to reduce boilerplate code.

### Spring Data JPA - SQL
Persist data in SQL stores with Java Persistence API using Spring Data and
Hibernate.

### PostgreSQL Driver - SQL
A JDBC and R2DBC driver that allows Java programs to connect to a PostgreSQL
database using standard, database independent Java code.

### Spring Security + JJWT - SECURITY
Authentication, BCrypt hashing and the per-role rules of the filter chain. JJWT
issues and verifies the session token the SPA carries.

### Validation - I/O
Bean Validation on the request bodies, which is what produces `VALIDATION_ERROR`.

### Java Mail Sender - I/O
Sends the two-factor and password recovery codes. Without SMTP configured the code
falls back to the application log.

### SpringDoc OpenAPI - WEB
Serves `/swagger-ui.html`, a direct input for the technical manual.
