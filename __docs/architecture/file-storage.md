# File storage

Photos of notices are the only files the system stores. They live in MinIO, an
S3-compatible object store, in one bucket. The database holds only the object key.

## MinIO

| | |
|---|---|
| Container | `derechi-minio`, image `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` |
| S3 API | `http://localhost:9000` |
| Console | `http://localhost:9001`, root account `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` (`derechi` / `derechi123` locally) |
| Application account | `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` (`derechi-app` / `derechi-app-secret` locally), objects in `derechi-files` only |
| Bucket | `derechi-files` |

The bucket is created by the one-shot `minio-init` container (`mc mb --ignore-existing`)
which also sets anonymous **download** on it (`mc anonymous set download`). Reads need no
credentials; writes do. The same container, logged in as root, creates the policy
`derechi-app` from `docker/minio/derechi-app-policy.json` (get, put and delete objects in
`derechi-files`, plus `ListBucket` and `GetBucketLocation`), adds the application user
`MINIO_ACCESS_KEY` and attaches the policy to it. The applications use that account, never
the root one. The container runs `MINIO_PROMETHEUS_AUTH_TYPE=public` so Prometheus
can scrape it without a token.

Object keys follow one layout, written by both uploaders:

```
items/yyyy/MM/<uuid>.<ext>        e.g. items/2026/09/3f1c...-....jpg
```

Allowed content types are `image/jpeg`, `image/png`, `image/webp`, `image/gif`; the size
limit is 5 MB. The `image` column on `lost_item` / `found_item` stores the key, never a full
URL, though `StorageProperties.urlOf` tolerates an absolute URL for old rows.

## Two ways in

### Admin panel: server-side upload

`Admin-API` depends on `spring-cloud-aws-starter-s3` and configures it against MinIO:

```yaml
spring.cloud.aws:
  region.static: us-east-1
  credentials:
    access-key: ${MINIO_ACCESS_KEY:derechi-app}
    secret-key: ${MINIO_SECRET_KEY:derechi-app-secret}
  s3:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    path-style-access-enabled: true
```

`ImageStorage` (`Admin-API/src/main/java/org/shpytchuk/adminapi/service/`) wraps
`S3Operations`: `store(MultipartFile)` checks the type and size, generates the key and
uploads with the content type as metadata; `delete(key)` removes an object; `urlOf(key)`
appends the key to `derechi.storage.public-url`, which already points at the bucket.

`UploadController` exposes `POST /admin/uploads` (multipart, returns the key as plain text)
and `DELETE /admin/uploads` (body is the key). The item form's drop zone (`static/js/image-upload.js`) uploads as soon as a file is
picked and calls the delete when the user removes it again. A form that is abandoned after
the upload leaves an orphan object in the bucket; nothing cleans those up today. Both endpoints require
`@perm.canManageItems(authentication)`, which is any `CREATE` or `EDIT` permission on an
item scope.

Properties live under `derechi.storage` (`bucket`, `public-url`, `max-size`, bound to the
`StorageProperties` record). Spring's own multipart limits are set to match
(`max-file-size: 5MB`, `max-request-size: 6MB`).

### Web client: presigned PUT through Caddy

`Web-Client` never sends image bytes through Node or through `Client-API`. A server action
signs a PUT URL for the key (SigV4) with the application account (`S3_ACCESS_KEY` /
`S3_SECRET_KEY`, `derechi-app` locally) and the browser PUTs the file to
`http://<entry>/derechi-files/<key>?X-Amz-...`. The entry on 8080 is Caddy for now (see
[Caddy (API entry)](../deployment/infrastructure.md#caddy-api-entry)); its upload route
(`PUT` and `OPTIONS` on `/derechi-files/**`) forwards it to MinIO with:

- the `Host` header kept as is, which Caddy does by default for an HTTP upstream: the
  signature covers the host, so it must not be rewritten;
- no path rewrite, for the same reason;
- a 5 MiB body cap (`request_body`, `413` above it), because a presigned PUT cannot cap its
  own body;
- no CORS of its own: MinIO answers the preflight and adds the CORS headers itself.

The signed URL must therefore be built for the origin the browser uses, which is the
entry, not MinIO's internal address. In production the browser skips the entry and PUTs
straight to the Backblaze B2 bucket (`S3_PUBLIC_ENDPOINT`), whose own CORS rules apply. `Web-Client/README.md` has the details and the
diagnostics for a failing PUT.

`Client-API` has no upload endpoint: `ItemInput.image` is just the key string, and
`FoundItemService` insists it is present.

## One way out

Reads go through Caddy's `/files` route: `GET /files/**` is rewritten to
`/derechi-files/<key>` on MinIO and gets a
`Cache-Control: public, max-age=31536000, immutable` header. Keys contain a UUID, so an
object is never overwritten under the same key and the immutable header is safe.

Each consumer builds the public URL from its own setting:

| Who | Setting | Default |
|---|---|---|
| `Admin-API` | `derechi.storage.public-url` / `MINIO_PUBLIC_URL` | `http://localhost:9000/derechi-files` locally and in Compose (straight to MinIO, not through Caddy) |
| `Web-Client` | `NEXT_PUBLIC_FILES_URL` | Caddy's `/files` |
| Caddy | `MINIO_URI`, `MINIO_BUCKET` | `http://minio:9000`, `derechi-files` |

`Admin-API` links straight to MinIO (the bucket is public) both from the IDE and in
Compose, not through Caddy.

## Tests

`ImageStorageIT` in `Admin-API` runs against a real MinIO and is skipped when none is
reachable. Everything else mocks `ImageStorage`. See
[../conventions/testing.md](../conventions/testing.md).
