package local.knowledge.practice;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PracticeController {
  private final PracticeService service;
  public PracticeController(PracticeService service){this.service=service;}

  @PostMapping("/practice-sessions") Object generate(@RequestBody PracticeService.GenerateRequest request){return service.generate(request);}
  @GetMapping("/knowledge-bases/{kbId}/practice-sessions") Object list(@PathVariable String kbId){return service.list(kbId);}
  @GetMapping("/practice-sessions/{id}") Object get(@PathVariable String id){return service.session(id);}
  @PostMapping("/practice-sessions/{id}/submit") Object submit(@PathVariable String id,@RequestBody PracticeService.SubmitRequest request){return service.submit(id,request);}
  @PostMapping("/practice-sessions/{id}/grade-pending") Object gradePending(@PathVariable String id){return service.gradePending(id);}
  @GetMapping("/practice-sessions/{id}/review") Object review(@PathVariable String id){return service.review(id);}
}
