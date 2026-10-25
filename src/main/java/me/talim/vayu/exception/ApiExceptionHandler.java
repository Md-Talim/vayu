package me.talim.vayu.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(
            IdempotencyConflictException exception
    ) {
        return respond(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(InvalidEventPayloadException.class)
    public ResponseEntity<ApiError> handleInvalidEventPayload(
            InvalidEventPayloadException exception
    ) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<ApiError.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.withFieldErrors(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED",
                        "request validation failed",
                        fieldErrors
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<ApiError.FieldError> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(this::toFieldError)
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.withFieldErrors(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED",
                        "request validation failed",
                        fieldErrors
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "request body is missing or malformed"
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "invalid value for parameter '" + exception.getName() + "'"
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingRequestHeader(
            MissingRequestHeaderException exception
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "MISSING_HEADER",
                "required header '" + exception.getHeaderName() + "' is missing"
        );
    }

    private ApiError.FieldError toFieldError(FieldError fieldError) {
        return new ApiError.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ApiError.FieldError toFieldError(ConstraintViolation<?> violation) {
        return new ApiError.FieldError(
                violation.getPropertyPath().toString(),
                violation.getMessage()
        );
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status, code, message));
    }
}
