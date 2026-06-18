package br.com.gems.mapper.config;

import br.com.gems.mapper.ModelMapperUtils;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Bean
    ModelMapper modelMapper() {
        var modelMapper = new ModelMapper();
        configureDefaultModelMapper( modelMapper );
        return modelMapper;
    }

    public static void configureDefaultModelMapper( ModelMapper modelMapper ) {
        ModelMapperUtils.ignoreLazyFieldsNotInitialized( modelMapper );
        modelMapper.getConfiguration().setAmbiguityIgnored( true );
    }

}
