package br.com.gems.auditing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * AU-2 e AU-3: quem alterou, e o que acontece quando não há usuário.
 */
class AuditActorTest {

    @Test
    @DisplayName("AU-3: sem usuário autenticado, o autor é SISTEMA/TECNICO e nada é recusado")
    void semUsuarioOAutorEhTecnicoENadaEhRecusado() {
        AuditActor autor = new SystemAuditActorProvider().currentActor();

        assertThat(autor.name()).isEqualTo("SISTEMA");
        assertThat(autor.type()).isEqualTo(AuditActorType.TECNICO);

        assertThatCode(() -> new SystemAuditActorProvider().currentActor())
                .describedAs("migração, rotina agendada e provisionamento alteram dado legitimamente sem "
                        + "usuário; recusar aqui derrubaria operações corretas — ou levaria alguém a "
                        + "desligar a auditoria para o lote passar")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AU-2: com usuário autenticado, o autor é o nome dele, tipo USUARIO")
    void comUsuarioOAutorEhONomeDele() {
        AuditActor autor = AuditActor.usuario("ada");

        assertThat(autor.name()).isEqualTo("ada");
        assertThat(autor.type()).isEqualTo(AuditActorType.USUARIO);
    }

    @Test
    @DisplayName("Nome em branco ou nulo cai no autor técnico, em vez de gravar autor vazio")
    void nomeEmBrancoCaiNoAutorTecnico() {
        assertThat(AuditActor.usuario(null)).isEqualTo(AuditActor.tecnico());
        assertThat(AuditActor.usuario("   ")).isEqualTo(AuditActor.tecnico());
    }

    @Test
    @DisplayName("O tipo do autor é dado separado, e não convenção sobre o nome")
    void tipoEhDadoSeparado() {
        // Sem o tipo, distinguir uma rotina automática de um usuário efetivamente chamado SISTEMA
        // viraria comparação de string na consulta da trilha.
        AuditActor usuarioChamadoSistema = AuditActor.usuario("SISTEMA");

        assertThat(usuarioChamadoSistema.name()).isEqualTo(AuditActor.SISTEMA);
        assertThat(usuarioChamadoSistema.type()).isEqualTo(AuditActorType.USUARIO);
        assertThat(usuarioChamadoSistema).isNotEqualTo(AuditActor.tecnico());
    }
}
