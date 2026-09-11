package br.com.gems.security.authorization;

/** O enum de ações de um consumidor fictício — a fonte única de que tudo mais é derivado (A2). */
enum AcaoDeTeste implements AuthorizationAction {

    CONSULTAR_ORGANIZACAO( AuthorizationScope.GLOBAL ),
    ALTERAR_ORGANIZACAO( AuthorizationScope.GLOBAL ),
    CONSULTAR_TURMA( AuthorizationScope.TENANT );

    private final AuthorizationScope escopo;

    AcaoDeTeste( AuthorizationScope escopo ) {
        this.escopo = escopo;
    }

    @Override
    public AuthorizationScope scope() {
        return escopo;
    }

}
