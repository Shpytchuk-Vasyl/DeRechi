package org.shpytchuk.adminapi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * From Keycloak roles to authorities Spring Security:
 * <ul>
 *     <li>{@code realm_access.roles} → {@code ROLE_ADMIN_SUPER}, …</li>
 *     <li>{@code resource_access.<client>.roles} → {@code LOST_ITEM:EDIT}</li>
 * </ul>
 */
public class KeycloakAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthoritiesMapper.class);

    private static final String REALM_ACCESS = "realm_access";
    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private final String clientId;

    public KeycloakAuthoritiesMapper(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);

        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc) {
                addFrom(mapped, oidc.getIdToken() == null ? Map.of() : oidc.getIdToken().getClaims());
                addFrom(mapped, oidc.getUserInfo() == null ? Map.of() : oidc.getUserInfo().getClaims());
            }
        }

        log.debug("Authorities адміна: {}", mapped);
        return mapped;
    }

    private void addFrom(Set<GrantedAuthority> target, Map<String, Object> claims) {
        realmRoles(claims).forEach(role -> target.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
        clientRoles(claims).forEach(role -> target.add(new SimpleGrantedAuthority(role)));
    }

    private List<String> realmRoles(Map<String, Object> claims) {
        return roles(claims.get(REALM_ACCESS));
    }

    private List<String> clientRoles(Map<String, Object> claims) {
        if (claims.get(RESOURCE_ACCESS) instanceof Map<?, ?> resourceAccess) {
            return roles(resourceAccess.get(clientId));
        }
        return List.of();
    }

    private static List<String> roles(Object holder) {
        if (holder instanceof Map<?, ?> map && map.get(ROLES) instanceof Collection<?> roles) {
            return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
}
