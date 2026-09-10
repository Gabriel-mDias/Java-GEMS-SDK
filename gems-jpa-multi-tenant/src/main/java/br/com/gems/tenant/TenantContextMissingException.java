package br.com.gems.tenant;

/**
 * Lançada quando uma operação de persistência é alcançada sem contexto de tenant.
 * <p>
 * É a recusa contratada em MT-1. Até a versão 2.0.1 deste módulo, o contexto ausente devolvia
 * silenciosamente o schema {@code public}: a operação prosseguia e lia ou gravava no lugar errado, sem
 * erro algum. A troca de um valor padrão por esta exceção é a razão pela qual o módulo muda de
 * comportamento nesta rodada.
 * </p>
 * <p>
 * <strong>Não</strong> herda da hierarquia de {@code gems-exception}: este módulo não depende dela, e
 * traduzir a recusa para um envelope de erro é escolha do consumidor. Quem quiser um status HTTP
 * específico registra o próprio handler.
 * </p>
 * <p>
 * <strong>Ao capturá-la, cuidado com o nível.</strong> O Hibernate chama
 * {@link TenantIdentifierResolver} de dentro da sua própria máquina e envolve o que sobe dali —
 * tipicamente numa {@code PersistenceException}. Um teste ou handler que espere esta classe no topo da
 * pilha vai falhar: ela chega como <em>causa</em>.
 * </p>
 */
public class TenantContextMissingException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public TenantContextMissingException(String message) {
        super(message);
    }
}
