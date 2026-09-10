package br.com.gems.mapstruct;

import br.com.gems.mapstruct.exemplo.Destino;
import br.com.gems.mapstruct.exemplo.Origem;
import br.com.gems.mapstruct.exemplo.OrigemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class GemsMappingConfigTest {

    /**
     * MS-1: todo campo de destino tem origem, o mapeador é gerado como bean Spring e mapeia.
     * A varredura de pacote — em vez de referenciar a classe gerada pelo nome — é o que prova
     * {@code componentModel = "spring"}: se ele mudasse, não haveria {@code @Component} para achar.
     */
    @Test
    void geraMapeadorComoBeanSpringEMapeiaTodosOsCampos() {
        try (AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext()) {
            contexto.scan("br.com.gems.mapstruct.exemplo");
            contexto.refresh();

            OrigemMapper mapeador = contexto.getBean(OrigemMapper.class);
            Destino destino = mapeador.toDestino(new Origem("Ada", 36));

            assertThat(destino.nome()).isEqualTo("Ada");
            assertThat(destino.idade()).isEqualTo(36);
        }
    }
}
