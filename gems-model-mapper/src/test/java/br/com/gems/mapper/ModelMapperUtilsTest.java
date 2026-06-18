package br.com.gems.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelMapperUtilsTest {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SourceDTO {
        private String nome;
        private int idade;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TargetEntity {
        private String nome;
        private int idade;
    }

    @Test
    void mapStrict_WithClassType_MapsCorrectly() {
        SourceDTO source = new SourceDTO("John Doe", 30);
        
        TargetEntity result = ModelMapperUtils.mapStrict(source, TargetEntity.class);
        
        assertEquals("John Doe", result.getNome());
        assertEquals(30, result.getIdade());
    }

    @Test
    void mapStrict_WithExistingInstance_MapsCorrectly() {
        SourceDTO source = new SourceDTO("Jane Doe", 25);
        TargetEntity target = new TargetEntity();
        
        ModelMapperUtils.mapStrict(source, target);
        
        assertEquals("Jane Doe", target.getNome());
        assertEquals(25, target.getIdade());
    }
}
