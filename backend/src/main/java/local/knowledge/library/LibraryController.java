package local.knowledge.library;

import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class LibraryController {
  private final LibraryService service;
  public LibraryController(LibraryService service) { this.service = service; }

  @GetMapping("/knowledge-bases") Object bases(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int pageSize) { return service.bases(page,pageSize); }
  @PostMapping("/knowledge-bases") Object create(@RequestBody Map<String,String> body) { return service.createBase(body.get("name"),body.get("description")); }
  @GetMapping("/knowledge-bases/{id}") Object base(@PathVariable String id) { return service.base(id); }
  @PatchMapping("/knowledge-bases/{id}") Object edit(@PathVariable String id,@RequestBody Map<String,String> body) { return service.editBase(id,body); }
  @DeleteMapping("/knowledge-bases/{id}") void deleteBase(@PathVariable String id,@RequestParam(defaultValue="false") boolean confirm) { service.deleteBase(id,confirm); }
  @GetMapping("/knowledge-bases/{id}/documents") Object documents(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status,@RequestParam(required=false) String query) { return service.documents(id,page,pageSize,status,query); }
  @PostMapping(path="/knowledge-bases/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) Object upload(@PathVariable String id,@RequestPart("file") MultipartFile file) throws IOException { return service.upload(id,file); }
  @GetMapping("/documents/{id}") Object document(@PathVariable String id) { return service.document(id); }
  @GetMapping("/documents/{id}/preview") Object preview(@PathVariable String id) { return service.preview(id); }
  @GetMapping("/documents/{id}/download") ResponseEntity<Resource> download(@PathVariable String id) throws IOException {
    var item=service.download(id);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(item.name(),java.nio.charset.StandardCharsets.UTF_8).replace("+","%20")).body(item.resource());
  }
  @PostMapping("/documents/{id}/retry") Object retry(@PathVariable String id) { return service.retry(id); }
  @DeleteMapping("/documents/{id}") void deleteDocument(@PathVariable String id) { service.deleteDocument(id); }
}
