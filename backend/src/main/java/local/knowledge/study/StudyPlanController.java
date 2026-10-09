package local.knowledge.study;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StudyPlanController {
  private final StudyPlanService service;
  public StudyPlanController(StudyPlanService service) { this.service=service; }
  @GetMapping("/knowledge-bases/{kbId}/study-plans") Object list(@PathVariable String kbId){return service.list(kbId);}
  @PostMapping("/study-plans/generate") Object generate(@RequestBody StudyPlanService.PlanRequest request){return service.generate(request);}
  @GetMapping("/study-plans/{id}") Object get(@PathVariable String id){return service.plan(id);}
  @PatchMapping("/study-plans/{id}") Object edit(@PathVariable String id,@RequestBody Map<String,Object> body){
    return service.edit(id, text(body,"goal"),text(body,"currentLevel"), date(body.get("deadline")),integer(body.get("dailyMinutes")),tasks(body.get("tasks")));
  }
  @PostMapping("/study-plans/{id}/replan") Object replan(@PathVariable String id,@RequestBody(required=false) Map<String,Object> body){return service.replan(id,body==null?"":String.valueOf(body.getOrDefault("progress","")));}
  @GetMapping("/knowledge-bases/{kbId}/knowledge-explorer") Object explore(@PathVariable String kbId){return service.explore(kbId);}
  @PatchMapping("/study-plans/{id}/tasks/{taskId}/complete") Object complete(@PathVariable String id,@PathVariable String taskId,@RequestBody Map<String,Boolean> body){return service.markCompleted(id,taskId,Boolean.TRUE.equals(body.get("completed")));}
  private static String text(Map<String,Object> m,String key){Object v=m.get(key);return v==null?"":String.valueOf(v);}
  private static Integer integer(Object v){if(v instanceof Number n)return n.intValue();try{return Integer.valueOf(String.valueOf(v));}catch(Exception e){return null;}}
  private static LocalDate date(Object v){if(v instanceof LocalDate d)return d;try{return LocalDate.parse(String.valueOf(v));}catch(Exception e){return null;}}
  private static List<StudyPlanService.TaskInput> tasks(Object value){if(!(value instanceof List<?> list))return List.of();return list.stream().filter(Map.class::isInstance).map(Map.class::cast).map(t->new StudyPlanService.TaskInput(text(t,"id"),text(t,"title"),text(t,"description"),date(t.get("scheduledDate")),integer(t.get("estimatedMinutes")),Boolean.TRUE.equals(t.get("completed")),t.get("chunkIds") instanceof List<?> refs?refs.stream().map(String::valueOf).toList():List.of())).toList();}
  private static Object val(Map<String,Object> m,String a,String b){return m.containsKey(a)?m.get(a):m.get(b);}
}
