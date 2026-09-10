package br.com.gems.exception.exception.enums;

import lombok.Getter;

@Getter
public enum ErrorTypeEnum {

    ERRO_NAO_ESPERADO( "Erro não esperado" ),
    FALHA( "Falha" ),
    ALERTA( "Alerta" ),
    VALIDACAO( "Falha de validação" ),
    ACESSO_NEGADO( "Acesso negado" ),
    SERVICO_INDISPONIVEL( "Serviço externo indisponível" ),
    ;

    private String description;

    private ErrorTypeEnum( String description ) {
        this.description = description;
    }

}
