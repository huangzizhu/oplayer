package com.huangzizhu.exception;

public class ParamInvalidException extends RuntimeException {
    public ParamInvalidException(String message) {
        super(message);
    }
    public ParamInvalidException(String message, Throwable cause) {super(message, cause);}
}
