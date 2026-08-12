package com.fitness.app.report;

import com.fitness.app.report.dto.ClassAttendanceResponse;
import com.fitness.app.report.dto.ClassDemandResponse;
import com.fitness.app.report.dto.GuestPassUsageResponse;
import com.fitness.app.report.dto.MemberDistributionResponse;
import com.fitness.app.report.dto.MemberProgressResponse;
import com.fitness.app.report.dto.MembershipExpiryResponse;
import com.fitness.app.report.dto.NutritionAdherenceResponse;
import com.fitness.app.report.dto.ReportFormat;
import com.fitness.app.report.dto.RevenueGrouping;
import com.fitness.app.report.dto.RevenueResponse;
import com.fitness.app.report.dto.TrainerLoadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Report endpoints. All GET, read-only, admin-only (role A).
 * Supports format=json|csv. Defaults to JSON.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController
{
    private final ReportService reportService;
    private final ReportCsv     reportCsv;

    @GetMapping("/revenue")
    public Object revenue(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(name = "group_by", required = false) RevenueGrouping groupBy,
            @RequestParam(name = "plan_id", required = false) Long planId,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        List<RevenueResponse> rows = reportService.revenue(from, to, groupBy, planId);
        return respondJson(rows, format, "revenue");
    }

    @GetMapping("/memberships")
    public Object memberships(
            @RequestParam(required = false) String status,
            @RequestParam(name = "expiring_in_days", required = false) Integer expiringInDays,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        List<MembershipExpiryResponse> rows = reportService.memberships(status, expiringInDays);
        return respondJson(rows, format, "memberships");
    }

    @GetMapping("/member-distribution")
    public Object memberDistribution(
            @RequestParam(name = "as_of", required = false) LocalDate asOf,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        LocalDate resolvedAsOf = asOf != null ? asOf : LocalDate.now();
        List<MemberDistributionResponse> rows = reportService.memberDistribution(resolvedAsOf);
        return respondJson(rows, format, "member-distribution");
    }

    @GetMapping("/class-attendance")
    public Object classAttendance(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(name = "group_class_id", required = false) Long groupClassId,
            @RequestParam(name = "trainer_id", required = false) Long trainerId,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        List<ClassAttendanceResponse> rows = reportService.classAttendance(from, to, groupClassId, trainerId);
        return respondJson(rows, format, "class-attendance");
    }

    @GetMapping("/class-demand")
    public Object classDemand(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        List<ClassDemandResponse> rows = reportService.classDemand(from, to);
        return respondJson(rows, format, "class-demand");
    }

    @GetMapping("/trainer-load")
    public Object trainerLoad(
            @RequestParam(name = "as_of", required = false) LocalDate asOf,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        LocalDate resolvedAsOf = asOf != null ? asOf : LocalDate.now();
        List<TrainerLoadResponse> rows = reportService.trainerLoad(resolvedAsOf);
        return respondJson(rows, format, "trainer-load");
    }

    @GetMapping("/member-progress")
    public Object memberProgress(
            @RequestParam(name = "member_id") Long memberId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        List<MemberProgressResponse> rows = reportService.memberProgress(memberId, from, to);
        return respondJson(rows, format, "member-progress");
    }

    @GetMapping("/guest-passes")
    public Object guestPasses(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(name = "pass_type", required = false) String passType,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        List<GuestPassUsageResponse> rows = reportService.guestPasses(from, to, passType);
        return respondJson(rows, format, "guest-passes");
    }

    @GetMapping("/nutrition-adherence")
    public Object nutritionAdherence(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "JSON") ReportFormat format)
    {
        List<NutritionAdherenceResponse> rows = reportService.nutritionAdherence(from, to, limit);
        return respondJson(rows, format, "nutrition-adherence");
    }

    /**
     * Helper to respond in JSON or CSV format. Sets appropriate headers for CSV downloads.
     */
    private Object respondJson(List<?> rows, ReportFormat format, String fileName)
    {
        if (format == ReportFormat.CSV)
        {
            String csv = reportCsv.toCsv(rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + ".csv\"")
                    .body(csv);
        }
        else
        {
            return rows;
        }
    }
}
