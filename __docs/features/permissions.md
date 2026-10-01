# Permissions

Admin access is two-dimensional: a **scope** (what kind of data) times an **action** (what you do with it). A permission is the string `SCOPE:ACTION`. Keycloak hands them out as client roles; `Admin-API` checks them on every controller method and in every template.

## The matrix

From `Admin-API/src/main/java/org/shpytchuk/adminapi/security/Scope.java`:

| Scope | VIEW | CREATE | EDIT | DELETE | ARCHIVE | NOTIFY |
|---|---|---|---|---|---|---|
| `MATCH` | x | | | | | x |
| `LOST_ITEM` | x | x | x | x | x | |
| `FOUND_ITEM` | x | x | x | x | x | |
| `LOST_ITEM_HISTORY` | x | x | x | x | | |
| `FOUND_ITEM_HISTORY` | x | x | x | x | | |

`Permissions.authority(scope, action)` refuses a combination the scope does not support, and `Permissions.all()` enumerates the 20 valid strings. `PermissionsTest` parses the Keycloak realm export and asserts that the set in code equals the set of `derechi-admin` client roles, so changing one without the other fails the build. It also pins the rule that only `ADMIN_SUPER` sees the archive, and only for viewing.

## Roles in Keycloak

The realm export is `docker/keycloak/realms/derechi-realm.json`. The 20 permissions exist as **client roles** on the `derechi-admin` client, named exactly like the authority (`LOST_ITEM:EDIT`). Three **composite realm roles** bundle them; each one includes the previous:

| Realm role | Adds client roles | Includes |
|---|---|---|
| `ADMIN_VIEWER` | `MATCH:VIEW`, `LOST_ITEM:VIEW`, `FOUND_ITEM:VIEW` | |
| `ADMIN_EDITOR` | `MATCH:NOTIFY`, `LOST_ITEM:CREATE`, `LOST_ITEM:EDIT`, `FOUND_ITEM:CREATE`, `FOUND_ITEM:EDIT` | `ADMIN_VIEWER` |
| `ADMIN_SUPER` | `LOST_ITEM:DELETE`, `FOUND_ITEM:DELETE`, `LOST_ITEM:ARCHIVE`, `FOUND_ITEM:ARCHIVE`, `LOST_ITEM_HISTORY:VIEW`, `FOUND_ITEM_HISTORY:VIEW` | `ADMIN_EDITOR`, `ADMIN` |

Note what is **not** granted to anyone in the shipped realm: `*_HISTORY:CREATE`, `*_HISTORY:EDIT`, `*_HISTORY:DELETE`. The code supports them, the realm does not hand them out, so nobody can edit or purge the archive today.

`USER` and `ADMIN` are plain realm roles with no client roles; `USER` is what the public web client will rely on.

Dev users: `admin@derechi.local` (`ADMIN_SUPER`), `moderator@derechi.local` (`ADMIN_EDITOR`), `viewer@derechi.local` (`ADMIN_VIEWER`), `user@derechi.local` (`USER` only). Passwords equal the part before `@`.

## How a request is checked

1. After OIDC login, `KeycloakAuthoritiesMapper` reads the ID token and the userinfo claims. `realm_access.roles` become `ROLE_<name>` authorities; `resource_access.derechi-admin.roles` become authorities verbatim (`LOST_ITEM:EDIT`). The client id comes from `derechi.admin.client-id`.
2. Controller methods are annotated

   ```java
   @RequirePermission(scope = Scope.LOST_ITEM, action = Action.EDIT)
   ```

   which is a meta-annotation for `@PreAuthorize("@perm.can(authentication, '{scope}', '{action}')")`. Placeholder substitution needs the `AnnotationTemplateExpressionDefaults` bean declared in `SecurityConfig`.
3. The bean `perm` is `PermissionChecker`; `can` wraps the authorities in `AdminPermissions` and looks the string up. A miss raises `AccessDeniedException`, which `GlobalExceptionHandler` turns into the 403 page.
4. Templates receive the same `AdminPermissions` as `perms` from `GlobalModelAdvice` and hide what the user cannot do: `${perms.can('MATCH', 'NOTIFY')}`.

`PermissionChecker.canManageItems` is the one derived rule: upload and revert on `/admin/uploads` are allowed to anyone with CREATE or EDIT on any item scope, because the form that uploads is already guarded.

## Known gaps

- Realm roles (`ROLE_ADMIN_SUPER`) are mapped but never checked; everything hangs on the client roles.
- Permissions are evaluated from the token at login. A role change in Keycloak takes effect on the next login, not immediately.
- There is no per-record ownership; a scope grants access to all rows.

## Where to look

- `Admin-API/src/main/java/org/shpytchuk/adminapi/security/`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/config/SecurityConfig.java`
- `docker/keycloak/realms/derechi-realm.json`
- Tests: `PermissionsTest`, and the 403 cases in `LostItemControllerTests` and `LostItemHistoryControllerTests`

Extending: [Add an admin permission](../extending/add-an-admin-permission.md). Realm editing rules: [Keycloak realm changes](../processes/keycloak-realm-changes.md).
