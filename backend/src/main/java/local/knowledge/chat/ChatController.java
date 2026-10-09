package local.knowledge.chat;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class ChatController {
  private final ChatService chats;
  public ChatController(ChatService chats) { this.chats = chats; }

  @PostMapping("/knowledge-bases/{id}/conversations") Object create(@PathVariable String id) { return chats.create(id); }
  @GetMapping("/knowledge-bases/{id}/conversations") Object conversations(@PathVariable String id) { return chats.conversations(id); }
  @GetMapping("/conversations/{id}/messages") Object messages(@PathVariable String id) { return chats.messages(id); }
  @PostMapping(path="/conversations/{id}/messages", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter send(@PathVariable String id, @RequestBody Map<String, Object> body) {
    String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
    String mode = body.get("mode") == null ? "STRICT" : String.valueOf(body.get("mode"));
    Integer topK = body.get("topK") instanceof Number n ? n.intValue() : null;
    return chats.send(id, content, mode, topK);
  }
}
