# Ragent Dev Stack

This compose file is for local Career Agent integration work. It starts the shared services needed by the current default configuration:

- PostgreSQL with pgvector, initialized from `resources/database/schema_pg.sql`
- Redis with the password used by `application.yaml`
- RustFS for S3-compatible object storage
- RocketMQ name server, broker, and dashboard
- Optional app and frontend containers via the `app` profile
- Optional Milvus stack via the `milvus` profile

## Start Dependencies Only

```bash
docker compose -f resources/docker/ragent-dev-stack.compose.yaml up -d postgres redis rustfs rmqnamesrv rmqbroker rocketmq-dashboard
```

Then run the backend locally:

```bash
mvn -pl bootstrap -am spring-boot:run
```

Swagger UI is available at:

```text
http://localhost:9090/api/ragent/swagger-ui.html
```

OpenAPI groups are available from Swagger UI and through these API docs endpoints:

```text
http://localhost:9090/api/ragent/v3/api-docs/career-user
http://localhost:9090/api/ragent/v3/api-docs/career-admin
http://localhost:9090/api/ragent/v3/api-docs/career-runtime
http://localhost:9090/api/ragent/v3/api-docs/career-export
```

Health probes are exposed only through Actuator health endpoints:

```text
http://localhost:9090/api/ragent/actuator/health
http://localhost:9090/api/ragent/actuator/health/liveness
http://localhost:9090/api/ragent/actuator/health/readiness
```

## Start App And Frontend In Compose

```bash
docker compose -f resources/docker/ragent-dev-stack.compose.yaml --profile app up -d
```

The frontend dev server is exposed at:

```text
http://localhost:5173
```

## Runtime Configuration

The `app` profile keeps the Maven hot-start workflow and wires the backend to compose services with environment variables:

| Variable | Default in compose | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/ragent?client_encoding=UTF8` | PostgreSQL connection used by Career data, tasks, traces, and export records. |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | PostgreSQL user. |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | PostgreSQL password. |
| `SPRING_DATA_REDIS_HOST` | `redis` | Redis host for Sa-Token, rate limits, recovery snapshots, and single-flight. |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port. |
| `SPRING_DATA_REDIS_PASSWORD` | `123456` | Redis password. |
| `ROCKETMQ_NAME_SERVER` | `rmqnamesrv:9876` | RocketMQ name server for async task messaging. |
| `RUSTFS_URL` | `http://rustfs:9000` | S3-compatible object storage endpoint. |
| `RUSTFS_ACCESS_KEY_ID` | `rustfsadmin` | RustFS access key. |
| `RUSTFS_SECRET_ACCESS_KEY` | `rustfsadmin` | RustFS secret key. |
| `MILVUS_URI` | `http://milvus-standalone:19530` | Optional Milvus endpoint when the `milvus` profile is enabled. |
| `RAGENT_RENDER_FONT_PATH` | `/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc` | Preferred licensed CJK font path for PDF/DOCX rendering. |

The backend listens on `9090`, the frontend dev server listens on `5173`, PostgreSQL on `5432`, Redis on `6379`, RustFS on `9000`, and RocketMQ dashboard on `8082`.

## Optional Milvus

The application defaults to PostgreSQL vector storage. Start Milvus only when testing the Milvus vector path:

```bash
docker compose -f resources/docker/ragent-dev-stack.compose.yaml --profile milvus up -d
```

## Font Governance

PDF rendering now registers configured font resources instead of relying only on CSS fallback. For stable CJK output, configure a licensed local TTF/OTF/TTC path:

```bash
RAGENT_RENDER_FONT_PATH=/path/to/NotoSansCJK-Regular.ttc
```

The `app` profile installs `fonts-noto-cjk` in the Maven container and points `RAGENT_RENDER_FONT_PATH` to the installed Noto CJK font. If no configured font exists, rendering falls back to system/renderer fonts unless `career.render.font.fail-on-missing-fonts=true`.
