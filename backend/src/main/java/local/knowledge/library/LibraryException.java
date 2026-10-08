package local.knowledge.library;

import org.springframework.http.HttpStatus;

public class LibraryException extends RuntimeException {
  public final String code;
  public final HttpStatus status;
  public LibraryException(HttpStatus status, String code, String message) {
    super(message); this.status = status; this.code = code;
  }
}
