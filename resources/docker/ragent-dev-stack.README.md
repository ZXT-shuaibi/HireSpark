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

## Start App And Frontend In Compose

```bash
docker compose -f resources/docker/ragent-dev-stack.compose.yaml --profile app up -d
```

The frontend dev server is exposed at:

```text
http://localhost:5173
```

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
