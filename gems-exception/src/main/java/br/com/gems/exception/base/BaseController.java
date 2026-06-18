package br.com.gems.exception.base;

import jakarta.servlet.http.HttpServletRequest;

public abstract class BaseController {

    public static final String REQUEST_BODY_ATTRIBUTE = "request-body";

    public void setBodyToExceptionLog( Object bodyObject, HttpServletRequest request ) {
        request.setAttribute( REQUEST_BODY_ATTRIBUTE, bodyObject );
    }

}
