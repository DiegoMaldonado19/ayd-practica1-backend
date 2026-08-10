package com.fitness.app.classes;

import com.fitness.app.classes.model.ClassRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRatingRepository extends JpaRepository<ClassRating, Long>
{
    Page<ClassRating> findByClassSessionId(Long classSessionId, Pageable pageable);

    /** Pre-check for a friendlier error than the uq_rating_member constraint violation. */
    boolean existsByClassSessionIdAndMemberId(Long classSessionId, Long memberId);
}
