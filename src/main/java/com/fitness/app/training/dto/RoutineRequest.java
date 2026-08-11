package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Creates or replaces a routine "con sus ejercicios por día" (§3.7). The member is in
 * the body because the route is /routines, not /members/{id}/routines. startDate and
 * status are not here: they default to today and DRAFT, and status changes by its own
 * PATCH.
 */
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