package com.fitness.app.directory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * The gym's customer file. It holds no plan, no expiry date and no trainer: those
 * change over time and belong to membership and trainer_assignment.
 *
 * person is a real @ManyToOne and not a plain Long: the isolation rule of
 * 02-Modulos §1 forbids mapping *another* module's entity, and Person belongs to
 * directory. That is what lets the listing filter by name in a single query.
 *
 * @JoinColumn is the one place this project names a column by hand: the naming
 * strategy derives the *field* names, but an association without it defaults to
 * person_person_id, which is not what the DDL calls the foreign key.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Member
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long         memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id")
    private Person       person;

    private String       memberCode;
    private LocalDate    joinedOn;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    private String       emergencyContactName;
    private String       emergencyContactPhone;
    private LocalDate    terminatedOn;
    private String       notes;
}
