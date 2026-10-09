package local.knowledge.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import local.knowledge.library.LibraryException;
import local.knowledge.chat.ChatService;
import local.knowledge.model.ModelGateway;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(LibraryException.class)
  ResponseEntity<ApiError> library(LibraryException exception) {
    return ResponseEntity.status(exception.status).body(ApiError.of(exception.code, exception.getMessage()));
  }
  @ExceptionHandler(ChatService.ChatException.class)
  ResponseEntity<ApiError> chat(ChatService.ChatException exception) {
    return ResponseEntity.status(exception.status()).body(ApiError.of(exception.code(), exception.getMessage()));
  }
  @ExceptionHandler(ModelGateway.ModelException.class)
  ResponseEntity<ApiError> model(ModelGateway.ModelException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError.of(exception.code(), exception.getMessage()));
  }
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiError.of("FILE_TOO_LARGE", "文件超过50MB"));
  }
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiError badRequest(IllegalArgumentException exception) { return ApiError.of("INVALID_REQUEST", exception.getMessage()); }
}
