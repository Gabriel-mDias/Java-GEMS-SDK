package br.com.gems.utils;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;

@UtilityClass
public class DateUtil {

    public static boolean isValidBirthDate(LocalDate date){
        if( ObjectUtil.isNullOrEmpty(date)){
            return false;
        }

        return LocalDate.now().isAfter(date);
    }

    public static boolean isValidBirthDate(LocalDateTime date){
        if( ObjectUtil.isNullOrEmpty(date)){
            return false;
        }

        return LocalDateTime.now().isAfter(date);
    }

}
