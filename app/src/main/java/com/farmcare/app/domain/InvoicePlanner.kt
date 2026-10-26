package com.farmcare.app.domain

import com.farmcare.app.data.FarmData
import java.math.BigDecimal
import java.time.LocalDate

object InvoicePlanner {
    data class Line(val animalId: String, val description: String, val start: LocalDate, val end: LocalDate,
        val days: Long, val rate: BigDecimal, val unit: String, val amount: BigDecimal)

    /** Charges are allocated by ownership period, so a transferred animal bills each owner only for their own days. */
    fun plan(customerId: String, start: LocalDate, end: LocalDate, d: FarmData): List<Line> {
        val out = mutableListOf<Line>()
        for (o in d.ownership.filter { it.customerId == customerId }) {
            val animal = d.animal(o.animalId) ?: continue
            for (a in d.assignments.filter { it.animalId == o.animalId }) {
                val rate = a.rate ?: d.defaultRate(animal.species) ?: continue
                val ranges = listOfNotNull(
                    BillingCalculator.Range(LocalDate.parse(o.from), o.to?.let(LocalDate::parse)),
                    BillingCalculator.Range(LocalDate.parse(a.start), a.end?.let(LocalDate::parse)),
                    animal.returnDate?.let { BillingCalculator.Range(LocalDate.MIN, LocalDate.parse(it)) })
                val w = BillingCalculator.window(start, end, *ranges.toTypedArray()) ?: continue
                val r = BigDecimal(rate.toString())
                val amount = BillingCalculator.charge(r, a.unit, w.first, w.second)
                val days = BillingCalculator.days(w.first, w.second)
                out += Line(animal.id, "${animal.code} ${animal.species.replaceFirstChar { it.uppercase() }}: $days day(s) at " +
                    "${"%.2f".format(rate)}/${if (a.unit == "monthly") "month" else "day"}", w.first, w.second, days, r, a.unit, amount)
            }
        }
        return out
    }
}
