package com.queuemate.matching.recruitment;

import com.queuemate.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import static com.queuemate.matching.recruitment.BoardApi.*;

@RestController
@RequestMapping("/api/v1/recruitments")
public class BoardController {
    private final BoardService service;
    public BoardController(BoardService service) {this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Row create(CurrentUser u,@Valid @RequestBody Write b) {return service.create(u.userId(),b);}
    @PostMapping("/search") public Page search(CurrentUser u,@Valid @RequestBody Search b) {return service.search(u.userId(),b);}
    @GetMapping("/mine") public List<Row> mine(CurrentUser u) {return service.mine(u.userId());}
    @GetMapping("/{id}") public Row get(CurrentUser u,@PathVariable UUID id) {return service.get(u.userId(),id);}
    @PutMapping("/{id}") public Row edit(CurrentUser u,@PathVariable UUID id,@Valid @RequestBody Write b) {return service.edit(u.userId(),id,b);}
    @PostMapping("/{id}/actions") public Row action(CurrentUser u,@PathVariable UUID id,@Valid @RequestBody Action b) {return service.action(u.userId(),id,b);}
    @PostMapping("/{id}/join") public Row join(CurrentUser u,@PathVariable UUID id,@Valid @RequestBody Join b) {return service.join(u.userId(),id,b.sourceId());}
    @PostMapping("/{id}/respond") public Row respond(CurrentUser u,@PathVariable UUID id,@Valid @RequestBody Respond b) {return service.respond(u.userId(),id,b);}
    @GetMapping("/{id}/suggestions") public Suggestions suggestions(CurrentUser u,@PathVariable UUID id) {return service.suggestions(u.userId(),id);}
    @PostMapping("/impressions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void impressions(CurrentUser u,@Valid @RequestBody Impressions b) {service.impressions(u.userId(),b.ids());}
}
