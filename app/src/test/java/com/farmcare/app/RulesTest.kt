package com.farmcare.app

import com.farmcare.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RulesTest {
    private val d = LocalDate::parse
    @Test fun dailyChargeCountsBothEndDates() = assertEquals(BigDecimal("200.00"), BillingCalculator.charge(BigDecimal("20"), "daily", d("2026-10-01"), d("2026-10-10")))
    @Test fun fullMonthEqualsMonthlyRate() = assertEquals(BigDecimal("3000.00"), BillingCalculator.charge(BigDecimal("3000"), "monthly", d("2026-02-01"), d("2026-02-28")))
    @Test fun partialMonthIsProratedByDay() = assertEquals(BigDecimal("1500.00"), BillingCalculator.charge(BigDecimal("3000"), "monthly", d("2026-02-01"), d("2026-02-14")))
    @Test fun windowIntersectsOwnershipAndCare() {
        val w = BillingCalculator.window(d("2026-10-01"), d("2026-10-31"), BillingCalculator.Range(d("2026-10-10"), d("2026-10-20")), BillingCalculator.Range(d("2026-09-01"), null))
        assertEquals(d("2026-10-10") to d("2026-10-20"), w)
    }
    @Test fun noOverlapGivesNull() = assertNull(BillingCalculator.window(d("2026-10-01"), d("2026-10-31"), BillingCalculator.Range(d("2026-11-05"), null)))
    @Test fun vaccinationStatusFromDates() {
        val t = d("2026-10-26")
        assertEquals(VaccStatus.OVERDUE, HealthRules.status("scheduled", d("2026-10-25"), t))
        assertEquals(VaccStatus.DUE_TODAY, HealthRules.status("scheduled", t, t))
        assertEquals(VaccStatus.DUE_SOON, HealthRules.status("scheduled", d("2026-10-30"), t))
        assertEquals(VaccStatus.SCHEDULED, HealthRules.status("scheduled", d("2026-12-01"), t))
        assertEquals(VaccStatus.COMPLETED, HealthRules.status("completed", d("2026-10-01"), t))
    }
}
