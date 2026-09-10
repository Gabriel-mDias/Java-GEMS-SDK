package br.com.gems.auditing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AU-6: nenhum serviço de domínio alcança o escritor da trilha.
 * <p>
 * <strong>A garantia é do compilador, não deste teste.</strong> O escritor, o listener e o integrador
 * têm visibilidade de pacote: uma classe fora de {@code br.com.gems.auditing} não consegue nem
 * referenciá-los. O que este teste faz é impedir que essa garantia seja desfeita sem ninguém notar —
 * tornar o escritor público é um caractere de diferença, e passaria despercebido em revisão.
 * </p>
 * <p>
 * É o que a rodada chama de gate que quebra o build em vez de confiar em disciplina: o
 * {@code backend/AGENTS.md} já proíbe por escrito que o domínio chame auditoria diretamente, e a
 * proibição escrita não impediu nada até agora.
 * </p>
 */
class SuperficiePublicaTest {

    @Test
    @DisplayName("AU-6: o escritor transacional não é público")
    void escritorNaoEhPublico() {
        assertThat(Modifier.isPublic(TransactionalAuditWriter.class.getModifiers()))
                .describedAs("público, o escritor pode ser injetado num serviço de domínio, e AU-6 volta "
                        + "a depender de revisão de código")
                .isFalse();
    }

    @Test
    @DisplayName("A ligação com o ORM não é superfície pública")
    void ligacaoComOOrmNaoEhPublica() {
        // Expor listener e integrador congelaria a forma de integração com o Hibernate, que é detalhe
        // de ligação e muda entre versões dele.
        assertThat(Modifier.isPublic(HibernateAuditListener.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(AuditHibernateIntegrator.class.getModifiers())).isFalse();
    }

    @Test
    @DisplayName("Os pontos de extensão do consumidor são públicos")
    void pontosDeExtensaoSaoPublicos() {
        List<Class<?>> superficie = List.of(Auditable.class, SensitiveField.class, AuditChange.class,
                AuditOperation.class, AuditActor.class, AuditActorType.class, AuditActorProvider.class,
                AuditTrailDestination.class, AuditPolicy.class, AuditWriteException.class);

        assertThat(superficie).allSatisfy(tipo ->
                assertThat(Modifier.isPublic(tipo.getModifiers()))
                        .describedAs("%s é contrato com o consumidor", tipo.getSimpleName())
                        .isTrue());
    }

    @Test
    @DisplayName("O destino da trilha não tem padrão embutido — sem configuração, recusa")
    void destinoNaoTemPadraoEmbutido() {
        assertThatThrownBy(() -> new FixedSchemaAuditTrailDestination(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gems.auditing.schema");

        assertThatThrownBy(() -> new FixedSchemaAuditTrailDestination(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("O destino configurado é usado como está")
    void destinoConfiguradoEhUsado() {
        assertThat(new FixedSchemaAuditTrailDestination("auditoria").schemaFor("Matricula"))
                .isEqualTo("auditoria");
    }
}
