package br.com.gems.tenant.execution;

import br.com.gems.tenant.JpaTenantContext;
import br.com.gems.tenant.TenantScope;
import org.springframework.core.task.TaskDecorator;

/**
 * Propaga o escopo de tenant para execução assíncrona, agendada e de evento.
 * <p>
 * Promovido de {@code meduc-deploy/persistence/execution/TenantTaskDecorator}, simplificado para
 * depender só de {@link JpaTenantContext}.
 * </p>
 * <p>
 * <strong>O escopo é capturado na submissão e reinstalado na execução.</strong> Sem isso, uma tarefa
 * submetida de dentro de um escopo roda sem contexto na thread do pool e cai em MT-1 — o que já é
 * melhor do que o comportamento anterior do módulo, em que ela cairia no schema {@code public} e
 * gravaria no lugar errado.
 * </p>
 * <p>
 * <strong>A limpeza antes e depois não é redundante.</strong> A thread do pool pode chegar carregando
 * escopo de uma tarefa anterior que não limpou — e nesse caso a tarefa atual herdaria contexto de outra
 * organização por acidente, que é exatamente o que MT-4 proíbe. Limpar na entrada garante que o único
 * escopo possível seja o capturado; limpar no {@code finally} garante que nada sobrevive para a
 * próxima tarefa. Tarefa submetida <strong>sem</strong> escopo roda sem escopo, de propósito: ela deve
 * falhar em MT-1 se tocar dado, não herdar o que sobrou na thread.
 * </p>
 */
public class TenantTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        JpaTenantContext.Snapshot capturado = JpaTenantContext.current().orElse(null);

        return () -> {
            JpaTenantContext.clear();
            try {
                if (capturado == null) {
                    runnable.run();
                } else if (capturado.global()) {
                    TenantScope.runInGlobal(runnable);
                } else {
                    TenantScope.runInTenant(capturado.alias(), runnable);
                }
            } finally {
                JpaTenantContext.clear();
            }
        };
    }
}
