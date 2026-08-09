package com.fitness.app.directory;

import com.fitness.app.directory.model.Employee;
import com.fitness.app.directory.model.EmployeePosition;
import com.fitness.app.directory.model.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, Long>
{
    boolean existsByPerson_PersonId(Long personId);

    /** Same nullable-filter and empty-search contract as MemberRepository.search. */
    @Query("""
           SELECT e
             FROM Employee e
             JOIN FETCH e.person p
             LEFT JOIN FETCH e.trainer
            WHERE (:position IS NULL OR e.position = :position)
              AND (:status   IS NULL OR e.status   = :status)
              AND (LOWER(p.firstName)      LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.lastName)       LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.documentNumber) LIKE LOWER(CONCAT('%', :search, '%')))
           """)
    Page<Employee> search(EmployeePosition position, EmployeeStatus status, String search, Pageable pageable);
}
