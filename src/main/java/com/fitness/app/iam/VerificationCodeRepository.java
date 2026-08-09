package com.fitness.app.iam;

import com.fitness.app.iam.model.CodeStatus;
import com.fitness.app.iam.model.CodeType;
import com.fitness.app.iam.model.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long>
{
    /** Backs the "issuing a new code invalidates the previous one" rule. */
    List<VerificationCode> findByAppUserIdAndCodeTypeAndStatus(Long       appUserId,
                                                               CodeType   codeType,
                                                               CodeStatus status);
}
