package com.fitness.app.report;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.config.GymProperties;
import com.fitness.app.report.dto.ClassAttendanceResponse;
import com.fitness.app.report.dto.ClassDemandResponse;
import com.fitness.app.report.dto.GuestPassUsageResponse;
import com.fitness.app.report.dto.MemberDistributionResponse;
import com.fitness.app.report.dto.MemberProgressResponse;
import com.fitness.app.report.dto.MembershipExpiryResponse;
import com.fitness.app.report.dto.NutritionAdherenceResponse;
import com.fitness.app.report.dto.RevenueGrouping;
import com.fitness.app.report.dto.RevenueResponse;
import com.fitness.app.report.dto.TrainerLoadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Report queries service. Validates parameters and delegates to ReportRepository.
 * All queries are read-only.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService
{
    private final ReportRepository reportRepository;
    private final GymProperties   gymProperties;

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT     = 100;

    public List<RevenueResponse> revenue(LocalDate from, LocalDate to, RevenueGrouping groupBy, Long planId)
    {
        RevenueGrouping resolvedGroupBy = groupBy != null ? groupBy : RevenueGrouping.MONTH;
        return reportRepository.revenue(from, to, resolvedGroupBy, planId);
    }

    public List<MembershipExpiryResponse> memberships(String status, Integer expiringInDays)
    {
        Integer resolvedDays = expiringInDays != null
                ? expiringInDays
                : gymProperties.membership().expiryNoticeDays();
        return reportRepository.memberships(status, resolvedDays);
    }

    public List<MemberDistributionResponse> memberDistribution(LocalDate asOf)
    {
        return reportRepository.memberDistribution(asOf);
    }

    public List<ClassAttendanceResponse> classAttendance(LocalDate from, LocalDate to, Long groupClassId, Long trainerId)
    {
        return reportRepository.classAttendance(from, to, groupClassId, trainerId);
    }

    public List<ClassDemandResponse> classDemand(LocalDate from, LocalDate to)
    {
        return reportRepository.classDemand(from, to);
    }

    public List<TrainerLoadResponse> trainerLoad(LocalDate asOf)
    {
        return reportRepository.trainerLoad(asOf);
    }

    public List<MemberProgressResponse> memberProgress(Long memberId, LocalDate from, LocalDate to)
    {
        // Validate member exists
        if (!reportRepository.memberExists(memberId))
        {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        return reportRepository.memberProgress(memberId, from, to);
    }

    public List<GuestPassUsageResponse> guestPasses(LocalDate from, LocalDate to, String passType)
    {
        return reportRepository.guestPasses(from, to, passType);
    }

    public List<NutritionAdherenceResponse> nutritionAdherence(LocalDate from, LocalDate to, Integer limit)
    {
        int resolvedLimit = limit != null ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        return reportRepository.nutritionAdherence(from, to, resolvedLimit);
    }
}
