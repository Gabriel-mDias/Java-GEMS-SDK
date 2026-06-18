package br.com.gems.utils;

import lombok.experimental.UtilityClass;
import java.util.UUID;

@UtilityClass
public class UUIDUtil {

    /**
     * Converte uma String para UUID de forma segura.
     * Retorna null se a String for nula ou vazia.
     * 
     * Substitui a repetição de "id == null ? null : UUID.fromString(id)"
     */
    public static UUID fromStringOrNull(String id) {
        if (ObjectUtil.isNullOrEmpty(id)) {
            return null;
        }
        return UUID.fromString(id);
    }

}
