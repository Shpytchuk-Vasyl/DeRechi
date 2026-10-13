# Image uploads

Photos live in MinIO (S3 API) in the bucket `derechi-files`. The database stores only the object **key**; the public URL is assembled from it at render time. Two upload paths exist, one per front end, and both produce keys of the same shape:

```
items/yyyy/MM/<uuid>.<ext>
```

See [File storage](../architecture/file-storage.md) for the infrastructure side.

## From the admin panel

1. The form's dropzone (`static/js/image-upload.js`, FilePond) accepts `image/jpeg`, `image/png`, `image/webp`, `image/gif` and posts the file as soon as it is picked to `POST /admin/uploads` (multipart, field `file`).
2. `UploadController.upload` requires `@perm.canManageItems` (CREATE or EDIT on any item scope) and delegates to `ImageStorage.store`.
3. `ImageStorage` checks the content type against its extension map and the size against `derechi.storage.max-size` (5 MB; Spring's multipart limit is set to the same 5 MB with a 6 MB request cap), generates the key and uploads through Spring Cloud AWS `S3Operations` with the content type as metadata.
4. The key is returned as plain text and placed in the form's hidden `image` field. Removing the file in the dropzone calls `DELETE /admin/uploads` with the key in the body, which deletes the object.
5. Rejections raise `RejectedUploadException` with a message key (`upload.unsupportedType`, `upload.tooLarge`); the controller answers 400 with the localized text, which FilePond shows under the file.

Configuration (`derechi.storage.*` bound to `StorageProperties`): `bucket`, `public-url` (`MINIO_PUBLIC_URL`, default `http://localhost:9000/derechi-files`; it must already point at the bucket), `max-size`. The S3 client itself is configured under `spring.cloud.aws.*` with path-style access and the application account `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` (locally `derechi-app` / `derechi-app-secret`, created by `minio-init`), which may only work with objects in the bucket; the root account (`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`) is not used by services.

Rendering: `StorageProperties.urlOf(key)` returns `<public-url>/<key>` (the bucket is never added: MinIO gets it from `public-url`, Caddy's `/files` route adds it in its rewrite), or the key unchanged when it is already an absolute URL. The templates get it as `${uploads.urlOf(item.image)}`.

## From the web client

The browser uploads straight to the storage; no byte passes through Node or `Client-API`. Locally and in Compose it goes through [Caddy](../deployment/infrastructure.md#caddy-api-entry) on 8080 to MinIO; in production `S3_PUBLIC_ENDPOINT` is Backblaze B2 itself, so uploads skip Caddy:

1. A server action validates type and size, generates the key in the same layout, and signs a presigned PUT (SigV4) for `<S3_PUBLIC_ENDPOINT>/derechi-files/<key>`.
2. The browser PUTs the file when the notice is submitted, not when it is picked, so an abandoned form leaves nothing in the bucket.
3. Caddy matches `PUT` and `OPTIONS` on `/derechi-files/**` and forwards them to MinIO without a rewrite. For an HTTP upstream it keeps the `Host` header, which the signature covers along with the path. The body is capped at 5 MiB (`413` above it, the web client's limit to the byte). The preflight and the CORS headers come from MinIO, not from Caddy.
4. The mutation sends the key in `ItemInput.image`.

The full write-up, including how to read the 403 and 404 variants on the PUT, is in `Web-Client/README.md`.

## Reading images

Caddy rewrites `GET /files/<key>` to `/derechi-files/<key>` on the storage (MinIO, B2 in production) and sets `Cache-Control: public, max-age=31536000, immutable`. Keys contain a UUID, so immutability is safe. The bucket has anonymous download enabled by `minio-init` in `docker-compose.yml`.

`Admin-API` does not go through Caddy: `MINIO_PUBLIC_URL` points straight at the bucket (`http://localhost:9000/derechi-files` locally and in Compose), which works because the bucket allows anonymous download. Those responses do not carry Caddy's `Cache-Control` header.

## Known gaps

- Nothing deletes the object when a notice is deleted or archived; orphans accumulate.
- There is no image processing: no resizing, no thumbnail, no EXIF stripping.
- `Client-API` trusts the key it receives; it does not check that the object exists.

## Where to look

- `Admin-API/src/main/java/org/shpytchuk/adminapi/controller/UploadController.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/service/ImageStorage.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/config/property/StorageProperties.java`, `config/StorageConfig.java`
- `Admin-API/src/main/resources/static/js/image-upload.js`
- `docker/caddy/api.Caddyfile` (the upload matcher and `/files`)
- Tests: `UploadControllerTests` (who may upload, the localized rejection), `ImageStorageIT` (runs only when the local MinIO on port 9000 is up, `@EnabledIf("minioIsUp")`), and in `Web-Client` `presign.test.ts` / `presign.integration.test.ts`
