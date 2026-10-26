package com.farmcare.app.domain

import com.farmcare.app.data.Vaccination
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class VaccStatus(val label: String) {
    COMPLETED("Completed"), CANCELLED("Cancelled"), OVERDUE("Overdue"),
    DUE_TODAY("Due today"), DUE_SOON("Due soon"), SCHEDULED("Scheduled")
}

object HealthRules {
    /** Status is always derived from stored dates, never stored. A scheduled record never becomes completed by itself. */
    fun status(status: String, due: LocalDate?, today: LocalDate, soonDays: Int = 7): VaccStatus = when {
        status == "completed" -> VaccStatus.COMPLETED
        status == "cancelled" -> VaccStatus.CANCELLED
        due == null -> VaccStatus.SCHEDULED
        due.isBefore(today) -> VaccStatus.OVERDUE
        due == today -> VaccStatus.DUE_TODAY
        !due.isAfter(today.plusDays(soonDays.toLong())) -> VaccStatus.DUE_SOON
        else -> VaccStatus.SCHEDULED
    }

    data class Pending(val record: Vaccination, val due: LocalDate, val status: VaccStatus)

    /**
     * Open vaccination work: scheduled records, plus completed records whose next due date
     * has not been followed by a later record for the same animal and vaccine.
     */
    fun pending(all: List<Vaccination>, today: LocalDate, soonDays: Int = 7): List<Pending> = all.mapNotNull { v ->
        val due = when (v.status) {
            "scheduled" -> v.scheduledFor
            "completed" -> v.nextDue?.takeIf {
                all.none { o -> o.id != v.id && o.animalId == v.animalId && o.status != "cancelled" &&
                    o.vaccine.equals(v.vaccine, true) && o.createdAt > v.createdAt }
            }
            else -> null
        }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
        Pending(v, due, status("scheduled", due, today, soonDays))
    }.sortedBy { it.due }
}

object BillingCalculator {
    data class Range(val start: LocalDate, val end: LocalDate?)

    /** Intersection of the billing period with every range (inclusive dates). Null when empty. */
    fun window(periodStart: LocalDate, periodEnd: LocalDate, vararg ranges: Range): Pair<LocalDate, LocalDate>? {
        var s = periodStart; var e = periodEnd
        for (r in ranges) { if (r.start.isAfter(s)) s = r.start; if (r.end != null && r.end.isBefore(e)) e = r.end }
        return if (e.isBefore(s)) null else s to e
    }

    fun days(s: LocalDate, e: LocalDate): Long = ChronoUnit.DAYS.between(s, e) + 1

    /**
     * daily: rate x billable calendar days (both ends inclusive).
     * monthly: each calendar month touched is charged rate x (days in month covered / days in that month),
     * so a full month costs exactly the monthly rate and partial months are prorated by day.
     */
    fun charge(rate: BigDecimal, unit: String, s: LocalDate, e: LocalDate): BigDecimal {
        if (e.isBefore(s)) return BigDecimal.ZERO.setScale(2)
        if (unit != "monthly") return (rate * BigDecimal(days(s, e))).setScale(2, RoundingMode.HALF_UP)
        var total = BigDecimal.ZERO; var cur = s
        while (!cur.isAfter(e)) {
            val ym = YearMonth.from(cur); val segEnd = minOf(e, ym.atEndOfMonth())
            total += rate * BigDecimal(days(cur, segEnd)) .divide(BigDecimal(ym.lengthOfMonth()), 8, RoundingMode.HALF_UP)
            cur = segEnd.plusDays(1)
        }
        return total.setScale(2, RoundingMode.HALF_UP)
    }
}
