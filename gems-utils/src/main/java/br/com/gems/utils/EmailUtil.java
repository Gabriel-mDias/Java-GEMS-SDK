package br.com.gems.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EmailUtil {

    public static boolean isValid(String email) {
        if (ObjectUtil.isNullOrEmpty(email)) {
            return false;
        }
        String regex = "^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$";
        return email.matches(regex);
    }



}
