package me.talim.vayu.exception;

public class InvalidEventPayloadException extends RuntimeException {

    public InvalidEventPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
