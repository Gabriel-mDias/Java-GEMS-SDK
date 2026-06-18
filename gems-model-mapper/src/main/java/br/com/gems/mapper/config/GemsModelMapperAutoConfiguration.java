package br.com.gems.mapper.config;

import br.com.gems.mapper.ModelMapperUtils;
import org.modelmapper.ModelMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GemsModelMapperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        ModelMapperUtils.ignoreLazyFieldsNotInitialized(mapper);
        return mapper;
    }
}
