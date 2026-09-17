package org.shpytchuk.adminapi.security;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Права адмінки живуть у Keycloak, тому пари Scope:Action у коді мають збігатися з клієнтськими ролями
 * клієнта {@code derechi-admin} у realm-імпорті — інакше @PreAuthorize мовчки нікого не пустить.
 */
class PermissionsTest {

    private static final String CLIENT_ID = "derechi-admin";

    @Test
    void permissionsMatchTheKeycloakRealmImport() {
        Set<String> declaredInCode = Permissions.all();
        Set<String> declaredInRealm = declaredInRealm();

        assertThat(declaredInCode).isEqualTo(declaredInRealm);
    }

    @Test
    void everyAuthorityIsBuiltFromScopeAndAction() {
        for (String authority : Permissions.all()) {
            String[] parts = authority.split(":");
            assertThat(parts).hasSize(2);
            assertThat(Permissions.authority(Scope.valueOf(parts[0]), Action.valueOf(parts[1])))
                    .isEqualTo(authority);
        }
    }

    /**
     * Архів — лише для супер-адміна, і лише на перегляд: realm-імпорт видає ADMIN_SUPER
     * тільки *_HISTORY:VIEW, а ADMIN_EDITOR і ADMIN_VIEWER не отримують нічого архівного.
     */
    @Test
    void onlyTheSuperAdminRoleGrantsTheArchive() {
        Set<String> archive = archiveAuthorities();
        Set<String> viewOnly = Set.of("LOST_ITEM_HISTORY:VIEW", "FOUND_ITEM_HISTORY:VIEW");

        assertThat(archive).as("права на архів у коді").containsAll(viewOnly);

        Set<String> superAdmin = new LinkedHashSet<>(grantedBy("ADMIN_SUPER"));
        superAdmin.retainAll(archive);

        assertThat(superAdmin).as("архівні права ADMIN_SUPER").isEqualTo(viewOnly);
        assertThat(grantedBy("ADMIN_EDITOR")).doesNotContainAnyElementsOf(archive);
        assertThat(grantedBy("ADMIN_VIEWER")).doesNotContainAnyElementsOf(archive);
    }

    private static Set<String> archiveAuthorities() {
        return Permissions.all().stream()
                .filter(authority -> authority.startsWith("LOST_ITEM_HISTORY:")
                        || authority.startsWith("FOUND_ITEM_HISTORY:"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** Клієнтські ролі, які realm-роль дає напряму (композити realm-ролей тут не розгортаємо). */
    private static Set<String> grantedBy(String realmRole) {
        JsonNode realm = JsonMapper.builder().build().readTree(realmFile().toFile());

        for (JsonNode role : realm.path("roles").path("realm")) {
            if (realmRole.equals(role.path("name").asString())) {
                Set<String> granted = new LinkedHashSet<>();
                role.path("composites").path("client").path(CLIENT_ID)
                        .forEach(name -> granted.add(name.asString()));
                return granted;
            }
        }
        throw new AssertionError("realm-роль " + realmRole + " не знайдено в імпорті");
    }

    private static Set<String> declaredInRealm() {
        JsonNode realm = JsonMapper.builder().build().readTree(realmFile().toFile());
        JsonNode clientRoles = realm.path("roles").path("client").path(CLIENT_ID);

        assertThat(clientRoles.isArray()).as("roles.client.%s у realm-імпорті", CLIENT_ID).isTrue();

        Set<String> authorities = new LinkedHashSet<>();
        clientRoles.forEach(role -> authorities.add(role.path("name").asString()));
        return authorities;
    }

    private static Path realmFile() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("docker/keycloak/realms/derechi-realm.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("docker/keycloak/realms/derechi-realm.json not found");
    }
}
