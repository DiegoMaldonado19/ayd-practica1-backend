package com.fitness.app.classes;

import com.fitness.app.classes.model.DifficultyLevel;
import com.fitness.app.classes.model.Discipline;
import com.fitness.app.classes.model.GroupClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroupClassRepository extends JpaRepository<GroupClass, Long>
{
    boolean existsByCode(String code);

    /** One query with nullable filters, the MembershipPlanRepository/EmployeeRepository pattern. */
    @Query("""
           SELECT g
             FROM GroupClass g
            WHERE (:discipline IS NULL OR g.discipline = :discipline)
              AND (:trainerId IS NULL OR g.trainerId = :trainerId)
              AND (:difficultyLevel IS NULL OR g.difficultyLevel = :difficultyLevel)
              AND (:active IS NULL OR g.active = :active)
           """)
    Page<GroupClass> search(Discipline discipline, Long trainerId, DifficultyLevel difficultyLevel, Boolean active,
                            Pageable pageable);
}
