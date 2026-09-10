package br.com.gems.auditing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AU-4 e a seleção do que entra na trilha.
 * <p>
 * O teste de campo sigiloso é o alvo da mutação T061.
 * </p>
 */
class AuditPolicyTest {

    @Auditable
    static class Usuario {
        String nome;
        @SensitiveField
        String senha;
    }

    static class NaoAuditada {
        String nome;
    }

    @Auditable
    static class Base {
        @SensitiveField
        String documento;
    }

    static class Herdeira extends Base {
        String apelido;
    }

    @Test
    @DisplayName("AU-4: campo sigiloso registra a mudança, sem os valores")
    void campoSigilosoRegistraAMudancaSemOsValores() {
        List<AuditChange> mudancas = AuditPolicy.changedFields(
                new String[] { "nome", "senha" },
                new Object[] { "Ada", "antiga" },
                new Object[] { "Ada Lovelace", "nova" },
                List.of(0, 1), Usuario.class);

        assertThat(mudancas).hasSize(2);

        AuditChange nome = mudancas.get(0);
        assertThat(nome.sensitive()).isFalse();
        assertThat(nome.oldValue()).isEqualTo("Ada");
        assertThat(nome.newValue()).isEqualTo("Ada Lovelace");

        AuditChange senha = mudancas.get(1);
        assertThat(senha.sensitive()).isTrue();
        assertThat(senha.field())
                .describedAs("a mudança precisa constar: auditar troca de senha é o caso de uso")
                .isEqualTo("senha");
        assertThat(senha.oldValue()).isNull();
        assertThat(senha.newValue()).isNull();
    }

    @Test
    @DisplayName("AU-4: o marcador é encontrado quando declarado na superclasse")
    void marcadorNaSuperclasseEhEncontrado() {
        List<AuditChange> mudancas = AuditPolicy.changedFields(
                new String[] { "documento" },
                new Object[] { "111" }, new Object[] { "222" },
                List.of(0), Herdeira.class);

        assertThat(mudancas.get(0).sensitive())
                .describedAs("entidade de domínio costuma herdar campos de uma base; procurar só na "
                        + "classe concreta vazaria o valor sigiloso herdado")
                .isTrue();
    }

    @Test
    @DisplayName("Campo marcado como sujo pelo ORM mas com o mesmo valor não entra na trilha")
    void campoSujoComMesmoValorNaoEntra() {
        List<AuditChange> mudancas = AuditPolicy.changedFields(
                new String[] { "nome" },
                new Object[] { "Ada" }, new Object[] { "Ada" },
                List.of(0), Usuario.class);

        assertThat(mudancas)
                .describedAs("o ORM marca propriedades sujas com folga; registrar o que ele diz encheria "
                        + "a trilha de mudanças de nada para nada")
                .isEmpty();
    }

    @Test
    @DisplayName("Valor complexo vira o nome do tipo, e não o toString dele")
    void valorComplexoViraONomeDoTipo() {
        assertThat(AuditPolicy.serialize(new StringBuilder("x"))).isEqualTo("<StringBuilder>");
        assertThat(AuditPolicy.serialize(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(AuditPolicy.serialize(null)).isNull();
    }

    @Test
    @DisplayName("Valor longo é truncado no limite da coluna, e não descartado")
    void valorLongoEhTruncado() {
        String longo = "x".repeat(AuditPolicy.TAMANHO_MAXIMO_DO_VALOR + 500);

        assertThat(AuditPolicy.serialize(longo)).hasSize(AuditPolicy.TAMANHO_MAXIMO_DO_VALOR);
    }

    @Test
    @DisplayName("AU-1: a trilha é opt-in — entidade sem o marcador não é auditável")
    void trilhaEhOptIn() {
        assertThat(AuditPolicy.isAuditable(Usuario.class)).isTrue();
        assertThat(AuditPolicy.isAuditable(NaoAuditada.class)).isFalse();
        assertThat(AuditPolicy.isAuditable(null)).isFalse();
    }

    @Test
    @DisplayName("AU-1: o marcador é herdado pelas subclasses")
    void marcadorEhHerdado() {
        assertThat(AuditPolicy.isAuditable(Herdeira.class)).isTrue();
    }
}
