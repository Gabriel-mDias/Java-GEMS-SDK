package br.com.gems.mapstruct.contraste;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * <strong>Fixture de teste. Não use em produção.</strong>
 *
 * <p>Existe só para dar contraste a
 * {@link br.com.gems.mapstruct.CompilacaoReprovaCampoNaoMapeadoTest}: o mesmo mapeador incompleto
 * é compilado duas vezes, mudando apenas a configuração. Com
 * {@link br.com.gems.mapstruct.GemsMappingConfig} a compilação reprova; com esta, passa com aviso.</p>
 *
 * <p>É o que a prova por mutação demonstraria ao baixar a política do módulo para
 * {@link ReportingPolicy#WARN} — com a diferença de ficar no repositório em vez de existir por um
 * minuto na máquina de quem rodou o teste. Se alguém relaxar a política de verdade, o teste do
 * caminho ERROR fica vermelho.</p>
 */
@MapperConfig(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface ConfiguracaoRelaxadaDeContraste {
}
