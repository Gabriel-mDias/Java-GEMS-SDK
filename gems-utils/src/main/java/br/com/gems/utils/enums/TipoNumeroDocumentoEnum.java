package br.com.gems.utils.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TipoNumeroDocumentoEnum {

    CPF( "cpf" ),
    CNPJ( "cnpj" ),
    ;

    private String descricao;

}
