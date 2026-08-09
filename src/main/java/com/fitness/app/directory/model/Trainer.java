package com.fitness.app.directory.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Specialization of employee. It exists as its own table because only trainers
 * carry a member load cap, and every training table points here rather than at
 * employee.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Trainer
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long          trainerId;

    // @OneToOne and not @ManyToOne: uq_trainer_employee makes it one to one, and
    // that is what lets Employee map the inverse side and resolve a whole page of
    // staff in the same query.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee      employee;

    // SMALLINT in the DDL: mapping it as int would fail ddl-auto=validate.
    private short         maxMemberLoad;

    private String        bio;
    private boolean       active;

    /**
     * An @ElementCollection and not an entity with a composite key: the only
     * endpoint that writes specialties replaces the whole set, which is exactly
     * what Hibernate does here (delete all, insert all). @BatchSize resolves the
     * specialties of a whole page in one extra query instead of one per trainer.
     *
     * ponytail: trainer_specialty.certified_on is left unmapped because no
     * endpoint reads it. Turn Specialty into an @Embeddable carrying certifiedOn
     * when the interface needs the certification date.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "trainer_specialty", joinColumns = @JoinColumn(name = "trainer_id"))
    @Column(name = "specialty")
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private Set<Specialty> specialties = new LinkedHashSet<>();
}
