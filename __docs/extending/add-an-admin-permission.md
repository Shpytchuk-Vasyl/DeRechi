# Add an admin permission

Two different jobs hide behind this title:

- **(a)** a new `SCOPE:ACTION` pair, because a new page or button appeared (a new action on an existing scope, or a whole new scope);
- **(b)** a new composite role, because a new kind of staff member appeared and the existing `ADMIN_VIEWER` / `ADMIN_EDITOR` / `ADMIN_SUPER` bundles do not fit.

Both end in the Keycloak realm export, which has its own rules; read [Keycloak realm changes](../processes/keycloak-realm-changes.md) first.

## Before you start

Read [Permissions](../features/permissions.md). Remember that `PermissionsTest` compares `Permissions.all()` with the client roles in `docker/keycloak/realms/derechi-realm.json`; the code and the realm must change together or the build goes red.

## (a) A new scope or action

The example adds `LOST_ITEM:EXPORT` for a CSV download button.

1. **Enums.** `Admin-API/src/main/java/org/shpytchuk/adminapi/security/Action.java` gets `EXPORT`; `Scope.java` lists it on the scopes that support it:

   ```java
   LOST_ITEM(Action.VIEW, Action.CREATE, Action.EDIT, Action.DELETE, Action.ARCHIVE, Action.EXPORT),
   ```

   For a new scope, add a constant with its action set. `Permissions.all()` derives the authority strings from these two enums; nothing else lists them.

2. **Realm export.** In `derechi-realm.json`, under `roles.client.derechi-admin`, add a role object named exactly `LOST_ITEM:EXPORT` (copy a neighbour; ids must be unique, so give it a fresh UUID). Then add it to the composite that should grant it, under `roles.realm[].composites.client.derechi-admin`. The convention so far:

   | Composite | Gets |
   |---|---|
   | `ADMIN_VIEWER` | `VIEW` on items and matches |
   | `ADMIN_EDITOR` | `CREATE`, `EDIT`, `NOTIFY` |
   | `ADMIN_SUPER` | `DELETE`, `ARCHIVE`, archive `VIEW`, and everything above |

   Each composite includes the previous one through `composites.realm`, so put a permission in exactly one of them.

3. **Reload the realm.** Keycloak imports the file only when the realm does not exist yet. For a dev box: `docker compose down keycloak`, drop the `keycloak` database (or `docker compose down -v` for everything), `docker compose up -d keycloak`. Alternatively make the change in the Keycloak console and export it back into the JSON; the process page explains the export command.

4. **Guard the controller.**

   ```java
   @GetMapping("/export")
   @RequirePermission(scope = Scope.LOST_ITEM, action = Action.EXPORT)
   public ResponseEntity<Resource> export(...) { ... }
   ```

   Nothing else is needed on the server: `@RequirePermission` expands to `@PreAuthorize("@perm.can(authentication, 'LOST_ITEM', 'EXPORT')")`.

5. **Guard the template.**

   ```html
   <a th:if="${perms.can('LOST_ITEM', 'EXPORT')}" th:href="@{/admin/lost-items/export}" ...>
   ```

6. **Message keys.** A new button or page needs its label in all five bundles (`messages.properties`, `_uk`, `_pl`, `_de`, `_fr`); `MessagesTest` fails otherwise. A new scope also needs `entity.<SCOPE>`, `page.<scope>` and a `nav.*` entry.

7. **Tests.** `PermissionsTest` goes green once the realm JSON matches. In the controller test add one case with the permission (`oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.EXPORT))`, see `LostItemControllerTests` for the helper) expecting 200, and one without it expecting 403 (`hidesTheTableFromAnAdminWithoutViewPermission` is the pattern).

## (b) A new composite role

The example adds `ADMIN_AUDITOR`, who may view everything including the archive but change nothing.

1. In `derechi-realm.json`, under `roles.realm`, add a role object `ADMIN_AUDITOR` with `"composite": true` and a `composites` block listing the client roles (`MATCH:VIEW`, `LOST_ITEM:VIEW`, `FOUND_ITEM:VIEW`, `LOST_ITEM_HISTORY:VIEW`, `FOUND_ITEM_HISTORY:VIEW`) and, if it builds on another realm role, `composites.realm`.
2. Add a dev user (`auditor@derechi.local` / `auditor`) under `users` with `realmRoles: ["ADMIN_AUDITOR", "USER"]`, so the role can be exercised locally and in the docs.
3. Reload the realm as above.
4. `KeycloakAuthoritiesMapper` needs nothing: it flattens whatever client roles the composite expands to. The realm role itself becomes `ROLE_ADMIN_AUDITOR`, which nothing checks.
5. If `PermissionsTest.onlyTheSuperAdminRoleGrantsTheArchive` now fails, that is the test doing its job: it pins that only `ADMIN_SUPER` sees the archive. Decide whether the rule changes and update the test deliberately.
6. Add the role to the table in [Permissions](../features/permissions.md).

## Migration needed?

No. Permissions live in Keycloak, not in the `derechi` database.

## Web-Client impact

None. The public client authenticates against `derechi-web` and never sees admin roles.

## Checklist

- [ ] `Scope` / `Action` updated (case a)
- [ ] Client role added to `derechi-admin` in the realm JSON, with a unique id
- [ ] Added to exactly one composite realm role, or a new composite created with a dev user
- [ ] Realm re-imported locally and the dev user can see or not see the button as intended
- [ ] `@RequirePermission` on every new handler, `perms.can` on every new control
- [ ] Message keys in all five bundles
- [ ] `PermissionsTest` and the 200/403 controller cases green
- [ ] Permissions feature page updated
