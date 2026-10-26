package com.farmcare.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal

@Serializable data class Membership(
    @SerialName("farm_id") val farmId: String, @SerialName("user_id") val userId: String, val role: String,
    @SerialName("can_record_payments") val canRecordPayments: Boolean = false,
    @SerialName("can_view_finance") val canViewFinance: Boolean = false) {
    val isAdmin get() = role == "admin"
    val canWrite get() = role == "admin" || role == "staff"
    val canPay get() = isAdmin || (role == "staff" && canRecordPayments)
    val canFinance get() = isAdmin || canViewFinance
}
@Serializable data class Profile(val id: String, @SerialName("full_name") val fullName: String? = null)
@Serializable data class Farm(val id: String, val name: String)
@Serializable data class FarmSettings(
    @SerialName("farm_id") val farmId: String, val currency: String = "INR",
    @SerialName("weight_tracking") val weightTracking: Map<String, Boolean> = emptyMap(),
    @SerialName("reminder_days_before") val reminderDays: List<Int> = listOf(7, 3, 1),
    @SerialName("payment_methods") val paymentMethods: List<String> = listOf("cash", "upi", "bank_transfer"),
    @SerialName("billing_rules") val billingRules: JsonObject? = null)
@Serializable data class Customer(
    val id: String, @SerialName("customer_code") val code: String, @SerialName("full_name") val name: String,
    val phone: String, @SerialName("alt_phone") val altPhone: String? = null, val address: String? = null,
    val notes: String? = null, val status: String = "active", @SerialName("registered_on") val registeredOn: String = "")
@Serializable data class Animal(
    val id: String, @SerialName("farm_id") val farmId: String, @SerialName("animal_code") val code: String,
    val species: String, val name: String? = null, val breed: String? = null, val gender: String? = null,
    @SerialName("date_of_birth") val dob: String? = null, @SerialName("estimated_age_months") val ageMonths: Int? = null,
    @SerialName("arrival_date") val arrivalDate: String? = null, @SerialName("return_date") val returnDate: String? = null,
    @SerialName("current_customer_id") val customerId: String,
    @SerialName("weight_tracking_override") val trackingOverride: Boolean? = null,
    @SerialName("identification_notes") val idNotes: String? = null,
    @SerialName("health_notes") val healthNotes: String? = null, val remarks: String? = null,
    val status: String = "active", val version: Int = 1, @SerialName("created_at") val createdAt: String = "")
@Serializable data class Weight(
    val id: String, @SerialName("animal_id") val animalId: String, @SerialName("weight_kg") val kg: Double,
    @SerialName("measured_on") val measuredOn: String, val notes: String? = null,
    @SerialName("recorded_by") val recordedBy: String? = null, @SerialName("created_at") val createdAt: String = "")
@Serializable data class Vaccination(
    val id: String, @SerialName("animal_id") val animalId: String, @SerialName("vaccine_name") val vaccine: String,
    val status: String, @SerialName("scheduled_for") val scheduledFor: String? = null,
    @SerialName("administered_on") val administeredOn: String? = null, @SerialName("next_due_date") val nextDue: String? = null,
    @SerialName("dose_details") val dose: String? = null, @SerialName("administered_by") val by: String? = null,
    @SerialName("batch_no") val batch: String? = null, val notes: String? = null, @SerialName("created_at") val createdAt: String = "")
@Serializable data class Treatment(
    val id: String, @SerialName("animal_id") val animalId: String, @SerialName("observed_on") val observedOn: String,
    val symptoms: String? = null, val diagnosis: String? = null, @SerialName("vet_consulted") val vet: Boolean = false,
    @SerialName("treatment_details") val details: String? = null, @SerialName("medicine_name") val medicine: String? = null,
    @SerialName("vet_instructions") val instructions: String? = null, @SerialName("follow_up_date") val followUp: String? = null,
    @SerialName("recovery_notes") val recovery: String? = null, @SerialName("responsible_person") val by: String? = null,
    @SerialName("created_at") val createdAt: String = "")
@Serializable data class CareAssignment(
    val id: String, @SerialName("animal_id") val animalId: String, @SerialName("rate_override") val rate: Double? = null,
    val unit: String = "daily", @SerialName("care_start") val start: String, @SerialName("care_end") val end: String? = null)
@Serializable data class Ownership(
    @SerialName("animal_id") val animalId: String, @SerialName("customer_id") val customerId: String,
    @SerialName("effective_from") val from: String, @SerialName("effective_to") val to: String? = null)
@Serializable data class Invoice(
    val id: String, @SerialName("invoice_no") val no: String, @SerialName("customer_id") val customerId: String,
    @SerialName("period_start") val start: String, @SerialName("period_end") val end: String,
    val total: Double, val status: String = "issued", @SerialName("created_at") val createdAt: String = "")
@Serializable data class InvoiceItem(
    @SerialName("invoice_id") val invoiceId: String, @SerialName("animal_id") val animalId: String? = null,
    val description: String, val quantity: Double, @SerialName("unit_rate") val rate: Double, val amount: Double)
@Serializable data class Payment(
    val id: String, @SerialName("customer_id") val customerId: String, val amount: Double, val method: String,
    @SerialName("reference_no") val reference: String? = null, @SerialName("paid_on") val paidOn: String,
    @SerialName("created_at") val createdAt: String = "")
@Serializable data class ActivityLog(
    val id: Long, @SerialName("table_name") val table: String, @SerialName("row_id") val rowId: String? = null,
    val action: String, val at: String, val actor: String? = null)
@Serializable data class QrToken(
    val id: String, @SerialName("animal_id") val animalId: String, val token: String, @SerialName("revoked_at") val revokedAt: String? = null)

// ---- insert payloads (nulls are omitted so database defaults apply) ----
@Serializable data class NewCustomer(@SerialName("farm_id") val farmId: String, @SerialName("full_name") val name: String,
    val phone: String, @SerialName("alt_phone") val altPhone: String? = null, val address: String? = null, val notes: String? = null)
@Serializable data class NewWeight(@SerialName("farm_id") val farmId: String, @SerialName("animal_id") val animalId: String,
    @SerialName("weight_kg") val kg: Double, @SerialName("measured_on") val measuredOn: String, val notes: String? = null,
    @SerialName("recorded_by") val recordedBy: String?, @SerialName("client_op_id") val opId: String)
@Serializable data class NewVaccination(@SerialName("farm_id") val farmId: String, @SerialName("animal_id") val animalId: String,
    @SerialName("vaccine_name") val vaccine: String, val status: String, @SerialName("scheduled_for") val scheduledFor: String? = null,
    @SerialName("administered_on") val administeredOn: String? = null, @SerialName("next_due_date") val nextDue: String? = null,
    @SerialName("dose_details") val dose: String? = null, @SerialName("administered_by") val by: String? = null,
    @SerialName("batch_no") val batch: String? = null, val notes: String? = null,
    @SerialName("created_by") val createdBy: String?, @SerialName("client_op_id") val opId: String)
@Serializable data class NewTreatment(@SerialName("farm_id") val farmId: String, @SerialName("animal_id") val animalId: String,
    @SerialName("observed_on") val observedOn: String, val symptoms: String? = null, val diagnosis: String? = null,
    @SerialName("vet_consulted") val vet: Boolean = false, @SerialName("treatment_details") val details: String? = null,
    @SerialName("medicine_name") val medicine: String? = null, @SerialName("vet_instructions") val instructions: String? = null,
    @SerialName("follow_up_date") val followUp: String? = null, @SerialName("recovery_notes") val recovery: String? = null,
    @SerialName("responsible_person") val by: String? = null, @SerialName("created_by") val createdBy: String?,
    @SerialName("client_op_id") val opId: String)
@Serializable data class NewPayment(@SerialName("farm_id") val farmId: String, @SerialName("customer_id") val customerId: String,
    val amount: Double, val method: String, @SerialName("reference_no") val reference: String? = null,
    @SerialName("paid_on") val paidOn: String, @SerialName("recorded_by") val recordedBy: String?, @SerialName("client_op_id") val opId: String)
@Serializable data class NewAssignment(@SerialName("farm_id") val farmId: String, @SerialName("animal_id") val animalId: String,
    @SerialName("rate_override") val rate: Double, val unit: String = "daily", @SerialName("care_start") val start: String)
@Serializable data class NewInvoice(@SerialName("farm_id") val farmId: String, @SerialName("invoice_no") val no: String,
    @SerialName("customer_id") val customerId: String, @SerialName("period_start") val start: String,
    @SerialName("period_end") val end: String, val subtotal: Double, val total: Double, @SerialName("created_by") val createdBy: String?)
@Serializable data class NewInvoiceItem(@SerialName("invoice_id") val invoiceId: String, @SerialName("farm_id") val farmId: String,
    @SerialName("animal_id") val animalId: String, val description: String, val quantity: Double,
    @SerialName("unit_rate") val rate: Double, val amount: Double,
    @SerialName("item_start") val start: String, @SerialName("item_end") val end: String)
@Serializable data class NewToken(@SerialName("farm_id") val farmId: String, @SerialName("animal_id") val animalId: String)

@Serializable data class FarmData(
    val farm: Farm? = null, val settings: FarmSettings? = null,
    val customers: List<Customer> = emptyList(), val animals: List<Animal> = emptyList(),
    val weights: List<Weight> = emptyList(), val vaccinations: List<Vaccination> = emptyList(),
    val treatments: List<Treatment> = emptyList(), val assignments: List<CareAssignment> = emptyList(),
    val ownership: List<Ownership> = emptyList(), val invoices: List<Invoice> = emptyList(),
    val items: List<InvoiceItem> = emptyList(), val payments: List<Payment> = emptyList(),
    val logs: List<ActivityLog> = emptyList(), val members: List<Membership> = emptyList(),
    val profiles: List<Profile> = emptyList(), val tokens: List<QrToken> = emptyList()) {

    fun customer(id: String) = customers.find { it.id == id }
    fun animal(id: String) = animals.find { it.id == id }
    /** Tracking: per-animal override, else species setting, else on for goat/sheep and off for buffalo. */
    fun trackingEnabled(a: Animal): Boolean = a.trackingOverride ?: settings?.weightTracking?.get(a.species) ?: (a.species != "buffalo")
    fun weightsOf(animalId: String) = weights.filter { it.animalId == animalId }
        .sortedWith(compareByDescending<Weight> { it.measuredOn }.thenByDescending { it.createdAt })
    fun charged(customerId: String): BigDecimal = invoices.filter { it.customerId == customerId && it.status == "issued" }
        .fold(BigDecimal.ZERO) { s, i -> s + BigDecimal(i.total.toString()) }
    fun paid(customerId: String): BigDecimal = payments.filter { it.customerId == customerId }
        .fold(BigDecimal.ZERO) { s, p -> s + BigDecimal(p.amount.toString()) }
    fun balance(customerId: String) = charged(customerId) - paid(customerId)
    fun defaultRate(species: String): Double? =
        (billingRulesRates()?.get(species) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
    private fun billingRulesRates(): JsonObject? = settings?.billingRules?.get("default_rates") as? JsonObject
    fun memberName(userId: String?) = profiles.find { it.id == userId }?.fullName ?: userId?.take(8) ?: "-"
}

@Serializable data class Snapshot(val membership: Membership, val data: FarmData)
