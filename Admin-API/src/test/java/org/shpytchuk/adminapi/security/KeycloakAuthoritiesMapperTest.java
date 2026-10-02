package org.shpytchuk.adminapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAuthoritiesMapperTest {

    private final KeycloakAuthoritiesMapper mapper = new KeycloakAuthoritiesMapper("derechi-admin");

    @Test
    void prefixesRealmRolesAndKeepsTheAdminClientRolesAsTheyAre() {
        OidcIdToken token = token(Map.of(
                "realm_access", Map.of("roles", List.of("ADMIN_EDITOR")),
                "resource_access", Map.of("derechi-admin", Map.of("roles", List.of("LOST_ITEM:EDIT", "MATCH:NOTIFY")))));

        assertThat(names(mapper.mapAuthorities(List.of(new OidcUserAuthority(token)))))
                .contains("ROLE_ADMIN_EDITOR", "LOST_ITEM:EDIT", "MATCH:NOTIFY");
    }

    @Test
    void ignoresRolesThatAnotherClientGranted() {
        OidcIdToken token = token(Map.of("resource_access", Map.of(
                "derechi-web", Map.of("roles", List.of("LOST_ITEM:DELETE")),
                "derechi-admin", Map.of("roles", List.of("LOST_ITEM:VIEW")))));

        assertThat(names(mapper.mapAuthorities(List.of(new OidcUserAuthority(token)))))
                .contains("LOST_ITEM:VIEW")
                .doesNotContain("LOST_ITEM:DELETE");
    }

    @Test
    void readsRolesFromTheUserInfoEndpointToo() {
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "admin",
                "resource_access", Map.of("derechi-admin", Map.of("roles", List.of("FOUND_ITEM:ARCHIVE")))));

        assertThat(names(mapper.mapAuthorities(List.of(new OidcUserAuthority(token(Map.of()), userInfo)))))
                .contains("FOUND_ITEM:ARCHIVE");
    }

    @Test
    void keepsTheAuthoritiesItWasGiven() {
        SimpleGrantedAuthority scope = new SimpleGrantedAuthority("SCOPE_openid");

        assertThat(names(mapper.mapAuthorities(List.of(scope)))).containsExactly("SCOPE_openid");
    }

    @Test
    void grantsNothingForMissingOrMalformedRoleClaims() {
        OidcIdToken token = token(Map.of(
                "realm_access", "ADMIN_SUPER",
                "resource_access", Map.of("derechi-admin", Map.of("roles", List.of(42, "MATCH:VIEW")))));
        OidcUserAuthority authority = new OidcUserAuthority(token);

        assertThat(names(mapper.mapAuthorities(List.of(authority))))
                .contains("MATCH:VIEW")
                .noneMatch(name -> name.contains("ADMIN_SUPER") || name.contains("42"));
    }

    private static OidcIdToken token(Map<String, Object> claims) {
        OidcIdToken.Builder builder = OidcIdToken.withTokenValue("id-token")
                .subject("admin")
                .issuedAt(Instant.parse("2026-10-01T12:00:00Z"))
                .expiresAt(Instant.parse("2026-10-01T13:00:00Z"));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static List<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
