package com.fitness.app.iam;

import com.fitness.app.iam.model.AppUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.iam.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long>
{
    Optional<AppUser> findByUsername(String username);

    /**
     * One query with nullable filters instead of a Specification and its helper
     * classes. search matches the username only: matching the person's name
     * would mean joining directory's table from iam, which is exactly what the
     * isolation rule forbids.
     *
     * search must never be null, only empty: PostgreSQL types an untyped null
     * parameter as bytea and lower(bytea) does not exist. An empty term yields
     * LIKE '%%', which matches everything. UserService does the normalization.
     */
    @Query("""
           SELECT u
             FROM AppUser u
            WHERE (:role   IS NULL OR u.role   = :role)
              AND (:status IS NULL OR u.status = :status)
              AND LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
           """)
    Page<AppUser> search(UserRole role, UserStatus status, String search, Pageable pageable);
}
