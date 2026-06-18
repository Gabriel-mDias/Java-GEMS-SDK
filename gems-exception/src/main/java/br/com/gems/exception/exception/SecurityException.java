package br.com.gems.exception.exception;

public class SecurityException extends RuntimeException {

  public SecurityException(String message) {
    super(message);
  }

  public SecurityException(String message, Throwable e){
    super(message, e);
  }

}
