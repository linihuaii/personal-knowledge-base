package local.knowledge.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import local.knowledge.library.LibraryException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(LibraryException.class)
  ResponseEntity<ApiError> library(LibraryException exception) {
    return ResponseEntity.status(exception.status).body(ApiError.of(exception.code, exception.getMessage()));
  }
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiError badRequest(IllegalArgumentException exception) { return ApiError.of("INVALID_REQUEST", exception.getMessage()); }
}
