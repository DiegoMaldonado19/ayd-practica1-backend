package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.MemberStatus;
import jakarta.validation.constraints.NotNull;

/** Logical deletion or reactivation of the file. The row is never deleted. */
public record MemberStatusRequest(@NotNull MemberStatus status)
{
}
