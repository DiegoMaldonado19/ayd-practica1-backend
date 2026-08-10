package com.fitness.app.directory;

import com.fitness.app.directory.model.Specialty;
import com.fitness.app.directory.model.Trainer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TrainerRepository extends JpaRepository<Trainer, Long>
{
    /**
     * MEMBER OF and not a JOIN over the specialties: a join would return the same
     * trainer once per specialty and break the page count. The employee and person
     * are fetched because the response carries the trainer's name.
     */
    @Query("""
           SELECT t
             FROM Trainer t
             JOIN FETCH t.employee e
             JOIN FETCH e.person p
            WHERE (:specialty IS NULL OR :specialty MEMBER OF t.specialties)
           """)
    Page<Trainer> search(Specialty specialty, Pageable pageable);

    /** The trainer profile of the signed-in person, for classes' trainer-scope check. */
    @Query("SELECT t.trainerId FROM Trainer t WHERE t.employee.person.personId = :personId")
    Optional<Long> findTrainerIdByPersonId(Long personId);
}
