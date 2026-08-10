package com.fitness.app.report;

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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Report queries using native SQL. The only class in the project that directly queries
 * multiple tables from different modules. See 02-Modulos §2.10 for the justification.
 */
@Repository
@RequiredArgsConstructor
public class ReportRepository
{
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Revenue aggregated by period and plan. Only CONFIRMED payments count.
     */
    public List<RevenueResponse> revenue(LocalDate from, LocalDate to, RevenueGrouping groupBy, Long planId)
    {
        String truncUnit = groupBy == RevenueGrouping.WEEK ? "week" : "month";

        String sql = """
                SELECT DATE_TRUNC(:truncUnit, p.paid_at)::DATE AS period,
                       COALESCE(mp.name, 'GUEST_PASS') AS plan_name,
                       SUM(p.gross_amount) AS gross_amount,
                       SUM(p.discount_amount) AS discount_amount,
                       SUM(p.gross_amount - p.discount_amount) AS net_amount
                  FROM payment p
                  LEFT JOIN membership m ON p.membership_id = m.membership_id
                  LEFT JOIN membership_plan mp ON m.membership_plan_id = mp.membership_plan_id
                 WHERE p.status = 'CONFIRMED'
                   AND p.paid_at >= :from
                   AND p.paid_at < :to
                   AND (CAST(:planId AS BIGINT) IS NULL OR mp.membership_plan_id = :planId)
                 GROUP BY DATE_TRUNC(:truncUnit, p.paid_at), COALESCE(mp.name, 'GUEST_PASS')
                 ORDER BY period DESC, plan_name
                """;

        var params = new MapSqlParameterSource()
                .addValue("truncUnit", truncUnit)
                .addValue("from", from.atStartOfDay())
                .addValue("to", to.atStartOfDay())
                .addValue("planId", planId);

        return jdbcTemplate.query(sql, params, revenueRowMapper());
    }

    /**
     * Memberships expiring or recently expired.
     */
    public List<MembershipExpiryResponse> memberships(String status, Integer expiringInDays)
    {
        String sql = """
                SELECT m.membership_id,
                       CONCAT(p.first_name, ' ', p.last_name) AS member_name,
                       mp.name AS plan_name,
                       m.status,
                       m.end_date,
                       CAST((m.end_date - CURRENT_DATE) AS INT) AS days_to_expiry
                  FROM membership m
                  JOIN membership_plan mp ON m.membership_plan_id = mp.membership_plan_id
                  JOIN member mb ON m.member_id = mb.member_id
                  JOIN person p ON mb.person_id = p.person_id
                 WHERE (CAST(:status AS VARCHAR) IS NULL OR m.status = :status)
                   AND (CAST(:expiringInDays AS INT) IS NULL
                        OR m.end_date <= CURRENT_DATE + :expiringInDays)
                 ORDER BY m.end_date
                """;

        var params = new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("expiringInDays", expiringInDays);

        return jdbcTemplate.query(sql, params, membershipExpiryRowMapper());
    }

    /**
     * Distribution of members by plan and status as of a given date.
     */
    public List<MemberDistributionResponse> memberDistribution(LocalDate asOf)
    {
        String sql = """
                WITH latest_membership AS (
                    SELECT DISTINCT ON (member_id)
                           member_id,
                           membership_plan_id,
                           status
                      FROM membership
                     WHERE start_date <= :asOf
                     ORDER BY member_id, start_date DESC
                )
                SELECT mp.plan_name,
                       lm.status,
                       COUNT(lm.member_id) AS member_count
                  FROM latest_membership lm
                  JOIN membership_plan mp ON lm.membership_plan_id = mp.membership_plan_id
                 GROUP BY mp.plan_name, lm.status
                 ORDER BY mp.plan_name, lm.status
                """;

        var params = new MapSqlParameterSource()
                .addValue("asOf", asOf);

        return jdbcTemplate.query(sql, params, memberDistributionRowMapper());
    }

    /**
     * Attendance metrics per class session.
     */
    public List<ClassAttendanceResponse> classAttendance(LocalDate from, LocalDate to, Long groupClassId, Long trainerId)
    {
        String sql = """
                SELECT cs.class_session_id,
                       gc.class_name,
                       CONCAT(p.first_name, ' ', p.last_name) AS trainer_name,
                       cs.session_date,
                       cs.start_time,
                       COUNT(CASE WHEN ce.status = 'ATTENDED' THEN 1 END) AS attended_count,
                       COUNT(CASE WHEN ce.status = 'ABSENT' THEN 1 END) AS absent_count,
                       COUNT(CASE WHEN ce.status = 'CANCELLED' THEN 1 END) AS cancelled_count,
                       CASE
                           WHEN COUNT(ce.enrollment_id) > 0
                           THEN ROUND(100.0 * COUNT(CASE WHEN ce.status = 'ATTENDED' THEN 1 END) / COUNT(ce.enrollment_id), 2)
                           ELSE 0
                       END AS attendance_rate
                  FROM class_session cs
                  JOIN group_class gc ON cs.group_class_id = gc.group_class_id
                  JOIN trainer t ON gc.trainer_id = t.trainer_id
                  JOIN employee e ON t.employee_id = e.employee_id
                  JOIN person p ON e.person_id = p.person_id
                  LEFT JOIN class_enrollment ce ON cs.class_session_id = ce.class_session_id
                 WHERE cs.session_date >= :from
                   AND cs.session_date <= :to
                   AND (CAST(:groupClassId AS BIGINT) IS NULL OR cs.group_class_id = :groupClassId)
                   AND (CAST(:trainerId AS BIGINT) IS NULL OR gc.trainer_id = :trainerId)
                 GROUP BY cs.class_session_id, gc.class_name, p.first_name, p.last_name, cs.session_date, cs.start_time
                 ORDER BY cs.session_date, cs.start_time
                """;

        var params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("groupClassId", groupClassId)
                .addValue("trainerId", trainerId);

        return jdbcTemplate.query(sql, params, classAttendanceRowMapper());
    }

    /**
     * Class demand: enrollments, occupancy, waitlist activations.
     */
    public List<ClassDemandResponse> classDemand(LocalDate from, LocalDate to)
    {
        String sql = """
                SELECT gc.group_class_id,
                       gc.class_name,
                       COUNT(DISTINCT cs.class_session_id) AS total_sessions,
                       COUNT(ce.enrollment_id) AS total_enrollments,
                       CASE
                           WHEN COUNT(DISTINCT cs.class_session_id) > 0
                           THEN ROUND(100.0 * COUNT(ce.enrollment_id) / (COUNT(DISTINCT cs.class_session_id) * gc.max_capacity), 2)
                           ELSE 0
                       END AS average_occupancy_rate,
                       COUNT(DISTINCT CASE WHEN we.waitlist_entry_id IS NOT NULL THEN cs.class_session_id END) AS waitlist_activations_count
                  FROM group_class gc
                  LEFT JOIN class_session cs ON gc.group_class_id = cs.group_class_id
                       AND cs.session_date >= :from
                       AND cs.session_date <= :to
                  LEFT JOIN class_enrollment ce ON cs.class_session_id = ce.class_session_id
                  LEFT JOIN waitlist_entry we ON cs.class_session_id = we.class_session_id
                 WHERE gc.active = TRUE
                 GROUP BY gc.group_class_id, gc.class_name, gc.max_capacity
                 ORDER BY total_enrollments DESC
                """;

        var params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);

        return jdbcTemplate.query(sql, params, classDemandRowMapper());
    }

    /**
     * Trainer caseload: assigned members, capacity, availability.
     */
    public List<TrainerLoadResponse> trainerLoad(LocalDate asOf)
    {
        String sql = """
                SELECT t.trainer_id,
                       CONCAT(p.first_name, ' ', p.last_name) AS trainer_name,
                       COUNT(CASE WHEN ta.start_date <= :asOf AND (ta.end_date IS NULL OR ta.end_date > :asOf) THEN 1 END) AS assigned_member_count,
                       t.max_member_load,
                       (t.max_member_load - COUNT(CASE WHEN ta.start_date <= :asOf AND (ta.end_date IS NULL OR ta.end_date > :asOf) THEN 1 END)) AS available_slots
                  FROM trainer t
                  JOIN employee e ON t.employee_id = e.employee_id
                  JOIN person p ON e.person_id = p.person_id
                  LEFT JOIN trainer_assignment ta ON t.trainer_id = ta.trainer_id
                 WHERE t.active = TRUE
                 GROUP BY t.trainer_id, p.first_name, p.last_name, t.max_member_load
                 ORDER BY assigned_member_count DESC
                """;

        var params = new MapSqlParameterSource()
                .addValue("asOf", asOf);

        return jdbcTemplate.query(sql, params, trainerLoadRowMapper());
    }

    /**
     * Member progress: historical measurements with weight change over time.
     */
    public List<MemberProgressResponse> memberProgress(Long memberId, LocalDate from, LocalDate to)
    {
        String sql = """
                SELECT pm.progress_measurement_id,
                       pm.measured_on,
                       pm.weight_kg,
                       pm.weight_kg - LAG(pm.weight_kg) OVER (ORDER BY pm.measured_on) AS weight_change_kg,
                       pm.body_fat_percent,
                       pm.muscle_mass_kg,
                       pm.notes
                  FROM progress_measurement pm
                 WHERE pm.member_id = :memberId
                   AND pm.measured_on >= :from
                   AND pm.measured_on <= :to
                 ORDER BY pm.measured_on ASC
                """;

        var params = new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("from", from)
                .addValue("to", to);

        return jdbcTemplate.query(sql, params, memberProgressRowMapper());
    }

    /**
     * Guest pass usage: conversions and payments.
     */
    public List<GuestPassUsageResponse> guestPasses(LocalDate from, LocalDate to, String passType)
    {
        String sql = """
                SELECT gp.guest_pass_id,
                       CONCAT(p.first_name, ' ', p.last_name) AS guest_name,
                       gp.pass_type,
                       gp.checked_in_at::DATE AS visit_date,
                       EXISTS(SELECT 1 FROM member m WHERE m.person_id = gp.person_id) AS converted_to_member,
                       COALESCE(pay.gross_amount, 0) AS amount_paid
                  FROM guest_pass gp
                  JOIN person p ON gp.person_id = p.person_id
                  LEFT JOIN payment pay ON gp.guest_pass_id = pay.guest_pass_id
                       AND pay.status = 'CONFIRMED'
                 WHERE gp.checked_in_at::DATE >= :from
                   AND gp.checked_in_at::DATE <= :to
                   AND (CAST(:passType AS VARCHAR) IS NULL OR gp.pass_type = :passType)
                 ORDER BY gp.checked_in_at DESC
                """;

        var params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("passType", passType);

        return jdbcTemplate.query(sql, params, guestPassesRowMapper());
    }

    /**
     * Nutrition adherence: members with most consistent meal logging.
     */
    public List<NutritionAdherenceResponse> nutritionAdherence(LocalDate from, LocalDate to, int maxRows)
    {
        String sql = """
                WITH daily_calories AS (
                    SELECT m.member_id,
                           m.log_date,
                           SUM(mi.quantity / f.serving_size * f.calories) AS total_calories
                      FROM meal m
                      JOIN meal_item mi ON m.meal_id = mi.meal_id
                      JOIN food f ON mi.food_id = f.food_id
                     WHERE m.log_date >= :from
                       AND m.log_date <= :to
                     GROUP BY m.member_id, m.log_date
                ),
                member_stats AS (
                    SELECT m.member_id,
                           CONCAT(p.first_name, ' ', p.last_name) AS member_name,
                           COUNT(DISTINCT dc.log_date) AS days_logged,
                           ROUND(AVG(dc.total_calories), 0) AS average_daily_calories,
                           ng.calorie_goal,
                           ng.tolerance_percent,
                           COUNT(CASE WHEN dc.total_calories >= ng.calorie_goal * (1.0 - ng.tolerance_percent / 100.0)
                                   AND dc.total_calories <= ng.calorie_goal * (1.0 + ng.tolerance_percent / 100.0)
                                 THEN 1 END) AS days_within_goal
                      FROM daily_calories dc
                      JOIN member m ON dc.member_id = m.member_id
                      JOIN person p ON m.person_id = p.person_id
                      JOIN nutrition_goal ng ON m.member_id = ng.member_id
                     GROUP BY m.member_id, p.first_name, p.last_name, ng.calorie_goal, ng.tolerance_percent
                )
                SELECT member_name,
                       days_logged,
                       average_daily_calories,
                       calorie_goal,
                       days_within_goal,
                       ROUND(100.0 * days_within_goal / NULLIF(days_logged, 0), 2) AS adherence_rate
                  FROM member_stats
                 ORDER BY days_logged DESC
                 LIMIT :maxRows
                """;

        var params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("maxRows", maxRows);

        return jdbcTemplate.query(sql, params, nutritionAdherenceRowMapper());
    }

    /**
     * Check if a member exists.
     */
    public boolean memberExists(Long memberId)
    {
        String sql = "SELECT COUNT(*) FROM member WHERE member_id = :memberId";
        var params = new MapSqlParameterSource().addValue("memberId", memberId);
        var count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    // --- RowMappers ---

    private RowMapper<RevenueResponse> revenueRowMapper()
    {
        return (rs, rowNum) -> new RevenueResponse(
                rs.getObject("period", LocalDate.class),
                rs.getString("plan_name"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("net_amount")
        );
    }

    private RowMapper<MembershipExpiryResponse> membershipExpiryRowMapper()
    {
        return (rs, rowNum) -> new MembershipExpiryResponse(
                rs.getLong("membership_id"),
                rs.getString("member_name"),
                rs.getString("plan_name"),
                rs.getString("status"),
                rs.getObject("end_date", LocalDate.class),
                rs.getInt("days_to_expiry")
        );
    }

    private RowMapper<MemberDistributionResponse> memberDistributionRowMapper()
    {
        return (rs, rowNum) -> new MemberDistributionResponse(
                rs.getString("plan_name"),
                rs.getString("status"),
                rs.getLong("member_count")
        );
    }

    private RowMapper<ClassAttendanceResponse> classAttendanceRowMapper()
    {
        return (rs, rowNum) -> new ClassAttendanceResponse(
                rs.getLong("class_session_id"),
                rs.getString("class_name"),
                rs.getString("trainer_name"),
                rs.getObject("session_date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class),
                rs.getLong("attended_count"),
                rs.getLong("absent_count"),
                rs.getLong("cancelled_count"),
                rs.getDouble("attendance_rate")
        );
    }

    private RowMapper<ClassDemandResponse> classDemandRowMapper()
    {
        return (rs, rowNum) -> new ClassDemandResponse(
                rs.getLong("group_class_id"),
                rs.getString("class_name"),
                rs.getLong("total_sessions"),
                rs.getLong("total_enrollments"),
                rs.getDouble("average_occupancy_rate"),
                rs.getLong("waitlist_activations_count")
        );
    }

    private RowMapper<TrainerLoadResponse> trainerLoadRowMapper()
    {
        return (rs, rowNum) -> new TrainerLoadResponse(
                rs.getLong("trainer_id"),
                rs.getString("trainer_name"),
                rs.getLong("assigned_member_count"),
                rs.getShort("max_member_load"),
                rs.getLong("available_slots")
        );
    }

    private RowMapper<MemberProgressResponse> memberProgressRowMapper()
    {
        return (rs, rowNum) -> new MemberProgressResponse(
                rs.getLong("progress_measurement_id"),
                rs.getObject("measured_on", LocalDate.class),
                rs.getBigDecimal("weight_kg"),
                rs.getBigDecimal("weight_change_kg"),
                rs.getBigDecimal("body_fat_percent"),
                rs.getBigDecimal("muscle_mass_kg"),
                rs.getString("notes")
        );
    }

    private RowMapper<GuestPassUsageResponse> guestPassesRowMapper()
    {
        return (rs, rowNum) -> new GuestPassUsageResponse(
                rs.getLong("guest_pass_id"),
                rs.getString("guest_name"),
                rs.getString("pass_type"),
                rs.getObject("visit_date", LocalDate.class),
                rs.getBoolean("converted_to_member"),
                rs.getBigDecimal("amount_paid")
        );
    }

    private RowMapper<NutritionAdherenceResponse> nutritionAdherenceRowMapper()
    {
        return (rs, rowNum) -> new NutritionAdherenceResponse(
                rs.getString("member_name"),
                rs.getLong("days_logged"),
                rs.getBigDecimal("average_daily_calories"),
                rs.getShort("calorie_goal"),
                rs.getLong("days_within_goal"),
                rs.getDouble("adherence_rate")
        );
    }
}
