package br.com.gems.mapstruct.exemplo;

import br.com.gems.mapstruct.GemsMappingConfig;
import org.mapstruct.Mapper;

/**
 * Mapeador de exemplo: todo campo de destino tem origem, então a compilação passa (MS-1).
 */
@Mapper(config = GemsMappingConfig.class)
public interface OrigemMapper {

    Destino toDestino(Origem origem);
}
