package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record RoutineRequest(
    @NotNull
    @JsonProperty("member_id")
    Long                memberId,

    @NotBlank @Size(max = 80)
    String              name,

    @Size(max = 300)
    @JsonProperty("goal_summary")
    String              goalSummary,

    @JsonProperty("end_date")
    LocalDate           endDate,

    @NotEmpty
    List<@Valid RoutineExerciseItem> exercises
)
{
}