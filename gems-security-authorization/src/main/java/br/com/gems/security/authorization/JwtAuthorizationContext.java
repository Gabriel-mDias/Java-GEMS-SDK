package br.com.gems.security.authorization;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * O que o token diz que o portador pode, já separado por escopo.
 * <p>
 * <strong>Ele não define um tipo de contexto de organização</strong>, e essa ausência é deliberada
 * (arbitragem A3). A organização aparece aqui como o <em>alias</em> comprovado — que é tudo de que
 * autorizar precisa. Quem persiste instala esse alias em {@code JpaTenantContext}, de
 * {@code gems-jpa-multi-tenant}, e quem tem identificador interno de organização o guarda no seu
 * próprio modelo. Declarar um segundo tipo de "contexto de tenant" aqui recriaria dentro da SDK a
 * trifurcação que a arbitragem A3 existe para desfazer.
 * </p>
 *
 * @param globalProfiles perfis fora de organização — informativos; não autorizam endpoint.
 * @param globalActions  ações concretas de escopo global.
 * @param tenantProfiles perfis dentro da organização — idem.
 * @param tenantActions  ações concretas de escopo de organização.
 * @param tenantAlias    o alias da organização comprovada, vazio fora dela.
 */
public record JwtAuthorizationContext(
        Set<String> globalProfiles,
        Set<String> globalActions,
        Set<String> tenantProfiles,
        Set<String> tenantActions,
        Optional<String> tenantAlias) {

    public JwtAuthorizationContext {
        globalProfiles = Set.copyOf( Objects.requireNonNull( globalProfiles, "globalProfiles" ) );
        globalActions = Set.copyOf( Objects.requireNonNull( globalActions, "globalActions" ) );
        tenantProfiles = Set.copyOf( Objects.requireNonNull( tenantProfiles, "tenantProfiles" ) );
        tenantActions = Set.copyOf( Objects.requireNonNull( tenantActions, "tenantActions" ) );
        tenantAlias = Objects.requireNonNull( tenantAlias, "tenantAlias" );
    }

    /** Contexto sem organização — o que um token de escopo global produz. */
    public static JwtAuthorizationContext global( Set<String> profiles, Set<String> actions ) {
        return new JwtAuthorizationContext( profiles, actions, Set.of(), Set.of(), Optional.empty() );
    }

    /** Se há organização comprovada nesta requisição. */
    public boolean hasTenant() {
        return tenantAlias.isPresent();
    }

}
