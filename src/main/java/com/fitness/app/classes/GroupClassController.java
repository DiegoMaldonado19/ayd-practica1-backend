package com.fitness.app.classes;

import com.fitness.app.classes.dto.ClassSessionResponse;
import com.fitness.app.classes.dto.GroupClassRequest;
import com.fitness.app.classes.dto.GroupClassResponse;
import com.fitness.app.classes.dto.SessionGenerationRequest;
import com.fitness.app.classes.model.DifficultyLevel;
import com.fitness.app.classes.model.Discipline;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** The /group-classes routes of §3.6: the recurring catalog and its dated sessions. */
@RestController
@RequestMapping("/api/v1/group-classes")
@RequiredArgsConstructor
public class GroupClassController
{
    private final GroupClassService   groupClassService;
    private final ClassSessionService classSessionService;

    @GetMapping
    public PagedModel<GroupClassResponse> list(@RequestParam(required = false) Discipline discipline,
                                               @RequestParam(name = "trainer_id", required = false) Long trainerId,
                                               @RequestParam(name = "difficulty_level", required = false)
                                               DifficultyLevel difficultyLevel,
                                               @RequestParam(required = false) Boolean active,
                                               Pageable pageable)
    {
        return new PagedModel<>(groupClassService.search(discipline, trainerId, difficultyLevel, active, pageable));
    }

    @PostMapping
    public ResponseEntity<GroupClassResponse> create(@Valid @RequestBody GroupClassRequest request)
    {
        var groupClass = groupClassService.create(request);

        return ResponseEntity.created(URI.create("/api/v1/group-classes/" + groupClass.groupClassId())).body(groupClass);
    }

    @GetMapping("/{groupClassId}")
    public GroupClassResponse detail(@PathVariable Long groupClassId)
    {
        return groupClassService.findById(groupClassId);
    }

    @PutMapping("/{groupClassId}")
    public GroupClassResponse update(@PathVariable Long groupClassId, @Valid @RequestBody GroupClassRequest request)
    {
        return groupClassService.update(groupClassId, request);
    }

    @DeleteMapping("/{groupClassId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long groupClassId)
    {
        groupClassService.deactivate(groupClassId);
    }

    @GetMapping("/{groupClassId}/sessions")
    public PagedModel<ClassSessionResponse> sessions(@PathVariable Long groupClassId,
                                                      @RequestParam(required = false) LocalDate from,
                                                      @RequestParam(required = false) LocalDate to,
                                                      Pageable pageable)
    {
        return new PagedModel<>(classSessionService.findByGroupClass(groupClassId, from, to, pageable));
    }

    @PostMapping("/{groupClassId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ClassSessionResponse> generateSessions(@PathVariable Long groupClassId,
                                                        @Valid @RequestBody SessionGenerationRequest request)
    {
        return groupClassService.generateSessions(groupClassId, request.from(), request.to());
    }
}
