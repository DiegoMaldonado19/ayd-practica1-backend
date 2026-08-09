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
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Staff file. position drives what the person does at the gym; the sign-in role
 * lives in app_user.role, because an employee may exist before having an account.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Employee
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long             employeeId;

    // Named by hand for the same reason as Member.person: an association without
    // @JoinColumn defaults to person_person_id, not to the FK the DDL declares.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id")
    private Person           person;

    private String           employeeCode;

    @Enumerated(EnumType.STRING)
    private EmployeePosition position;

    private LocalDate        hiredOn;
    private LocalDate        terminatedOn;

    @Enumerated(EnumType.STRING)
    private EmployeeStatus   status;

    /**
     * Null for anyone whose position is not TRAINER. Mapping the inverse side is
     * what lets a page of staff resolve its trainer profiles in the same JOIN
     * FETCH instead of one query per row.
     */
    @OneToOne(mappedBy = "employee", fetch = FetchType.LAZY)
    private Trainer          trainer;
}
