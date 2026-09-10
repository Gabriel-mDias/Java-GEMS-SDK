package br.com.gems.security.authorization;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * As ações que o sistema conhece, separadas por escopo.
 * <p>
 * <strong>A forma canônica é {@link #of(Class)}, sobre um enum.</strong> A origem desta classe no
 * Meduc lia um JSON versionado, que precisava ser mantido em paridade byte-a-byte com um enum de
 * backend e com um arquivo de frontend — três representações, uma disciplina, e um teste de paridade
 * para vigiar a digitação. Derivar o catálogo do enum troca a disciplina pelo compilador: uma ação
 * declarada é uma ação existente, e uma ação escrita errado não compila.
 * </p>
 * <p>
 * O construtor canônico continua público. Ele é o que o teste usa para montar um catálogo sintético,
 * e é a saída de quem carrega as ações de outra origem — a validação é a mesma nos dois caminhos.
 * </p>
 *
 * @param globalActions ações válidas fora de organização.
 * @param tenantActions ações válidas dentro de uma organização.
 */
public record AuthorizationCatalog(Set<String> globalActions, Set<String> tenantActions) {

    /** O formato de uma ação concreta. Perfil genérico ({@code GESTOR}) não passa aqui. */
    static final Pattern ACTION = Pattern.compile( "^[A-Z][A-Z0-9]*_[A-Z0-9_]+$" );

    public AuthorizationCatalog {
        globalActions = immutableValid( globalActions, "globalActions" );
        tenantActions = immutableValid( tenantActions, "tenantActions" );
        var overlap = new HashSet<>( globalActions );
        overlap.retainAll( tenantActions );
        if ( !overlap.isEmpty() ) {
            throw new IllegalArgumentException(
                    "Os catálogos global e tenant devem ser disjuntos; em ambos: " + overlap );
        }
    }

    /**
     * Deriva o catálogo do enum de ações do consumidor — a fonte única (arbitragem A2).
     *
     * @param actions o enum que implementa {@link AuthorizationAction}.
     * @throws IllegalArgumentException se o enum não declarar ação alguma, ou declarar uma ação em
     *         formato que não é o de uma ação concreta.
     */
    public static <E extends Enum<E> & AuthorizationAction> AuthorizationCatalog of( Class<E> actions ) {
        Objects.requireNonNull( actions, "actions" );
        var constantes = actions.getEnumConstants();
        if ( constantes == null || constantes.length == 0 ) {
            throw new IllegalArgumentException(
                    actions.getName() + " não declara ação alguma; um catálogo vazio autoriza nada e "
                            + "reprova todo endpoint de negócio." );
        }
        return new AuthorizationCatalog( nomesDe( constantes, AuthorizationScope.GLOBAL ),
                nomesDe( constantes, AuthorizationScope.TENANT ) );
    }

    /** As ações do escopo pedido. */
    public Set<String> actionsOf( AuthorizationScope scope ) {
        return scope == AuthorizationScope.TENANT ? tenantActions : globalActions;
    }

    private static <E extends Enum<E> & AuthorizationAction> Set<String> nomesDe( E[] constantes,
            AuthorizationScope escopo ) {
        return java.util.Arrays.stream( constantes )
                .filter( acao -> escopo == Objects.requireNonNull( acao.scope(),
                        () -> acao.name() + " não declara escopo" ) )
                .map( AuthorizationAction::name )
                .collect( Collectors.toUnmodifiableSet() );
    }

    private static Set<String> immutableValid( Set<String> actions, String field ) {
        Objects.requireNonNull( actions, field );
        var invalidas = actions.stream()
                .filter( action -> action == null || !ACTION.matcher( action ).matches() )
                .toList();
        if ( !invalidas.isEmpty() ) {
            throw new IllegalArgumentException( field + " contém ação inválida: " + invalidas );
        }
        return Set.copyOf( actions );
    }

}
