package no.testframework.runnerlib.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import no.testframework.runnerlib.execution.IdempotencyConflictException;
import no.testframework.runnerlib.execution.QueueFullException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }


    @ExceptionHandler(QueueFullException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, String> queueFull(QueueFullException e) {
        return Map.of("error", e.getMessage());
    }



    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> idempotencyConflict(IdempotencyConflictException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validation(MethodArgumentNotValidException e) {
        return Map.of("error", "Validation error", "detail", e.getMessage());
    }
}
