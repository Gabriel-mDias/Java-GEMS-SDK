package br.com.gems.mapstruct;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Configuração de mapeamento compartilhada da GEMS SDK.
 *
 * <p>Todo mapeador do projeto consumidor a referencia:</p>
 *
 * <pre>{@code
 * @Mapper(config = GemsMappingConfig.class)
 * public interface OrganizacaoMapper {
 *     OrganizacaoResponse toResponse(Organizacao origem);
 * }
 * }</pre>
 *
 * <p>Convenção de pacote: {@code <dominio>/mapper/XxxMapper}.</p>
 *
 * <p><strong>{@link ReportingPolicy#ERROR} não se relaxa.</strong> Campo de destino sem origem
 * reprova a compilação nomeando o campo. Ao ser adotada num projeto existente, a política vai
 * reprovar builds — cada reprovação é um campo que hoje chega nulo em silêncio. Baixar para
 * {@code WARN} na primeira dificuldade desmonta a razão de este módulo existir; a alternativa
 * correta é tratar os campos, um a um.</p>
 *
 * <p>{@code gems-model-mapper} permanece de primeira classe e continua suportado. MapStruct é o
 * recomendado para projeto novo, não o obrigatório.</p>
 */
@MapperConfig(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface GemsMappingConfig {
}
