package com.farmcare.app.data

import android.content.Context
import com.farmcare.app.data.local.*
import com.farmcare.app.work.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FarmRepository @Inject constructor(private val sb: SupabaseClient, val dao: LocalDao, @ApplicationContext private val ctx: Context) {
    /** Thrown after a write was safely stored on the device because there is no connection. Not yet on the server. */
    class QueuedException : Exception("Saved on this device. It will upload when you are back online.")
    private val json = Json { ignoreUnknownKeys = true }
    fun isNetwork(e: Throwable) = generateSequence(e) { it.cause }.any { it is java.io.IOException }

    private suspend fun insertOrQueue(table: String, label: String, opId: String, payload: JsonObject) {
        try { sb.from(table).insert(payload) } catch (e: Exception) {
            if (!isNetwork(e)) throw e
            dao.insertOp(PendingOp(opId, table, label, payload.toString())); SyncWorker.schedule(ctx); throw QueuedException()
        }
    }
    /** Sends queued writes. Returns false if the network is still down. Duplicates (same op id) count as synced. */
    suspend fun syncPending(): Boolean {
        for (op in dao.openOps()) {
            try { sb.from(op.tableName).insert(json.parseToJsonElement(op.payload).jsonObject); dao.mark(op.opId, "synced", null) }
            catch (e: Exception) {
                val msg = e.message.orEmpty()
                when { isNetwork(e) -> return false
                    msg.contains("client_op_id") || msg.contains("duplicate key") -> dao.mark(op.opId, "synced", null)
                    else -> dao.mark(op.opId, "failed", msg.take(200)) }
            }
        }
        dao.clearSynced(); return true
    }
    suspend fun saveSnapshot(m: Membership, d: FarmData) { dao.saveSnapshot(SnapshotRow(json = json.encodeToString(Snapshot.serializer(), Snapshot(m, d)), savedAt = System.currentTimeMillis())) }
    suspend fun loadSnapshot(): Pair<Snapshot, Long>? = dao.snapshot()?.let { r -> runCatching { json.decodeFromString(Snapshot.serializer(), r.json) to r.savedAt }.getOrNull() }
    /** Removes cached farm data and unsent changes from this device (used on sign-out). */
    suspend fun wipeLocal() { dao.clearSnapshot(); dao.clearOps() }
    val sessionStatus get() = sb.auth.sessionStatus
    private var farmId: String = ""
    private val uid get() = sb.auth.currentUserOrNull()?.id
    private fun op() = UUID.randomUUID().toString()

    // ---- auth ----
    suspend fun signIn(email: String, password: String) { sb.auth.signInWith(Email) { this.email = email.trim(); this.password = password } }
    /** Returns true when a session was opened immediately (email confirmation disabled). */
    suspend fun signUp(email: String, password: String): Boolean { sb.auth.signUpWith(Email) { this.email = email.trim(); this.password = password }; return sb.auth.currentSessionOrNull() != null }
    suspend fun resetPassword(email: String) = sb.auth.resetPasswordForEmail(email.trim())
    suspend fun signOut() = sb.auth.signOut()

    suspend fun membership(): Membership? {
        sb.auth.awaitInitialization()
        val id = uid ?: return null
        return sb.from("farm_memberships").select { filter { eq("user_id", id) } }.decodeList<Membership>().firstOrNull()?.also { farmId = it.farmId }
    }
    suspend fun bootstrapFarm(name: String) { sb.postgrest.rpc("bootstrap_farm", buildJsonObject { put("p_name", name) }) }

    private suspend inline fun <reified T : Any> rows(table: String, orderBy: String? = null, desc: Boolean = true, limit: Long? = null): List<T> =
        sb.from(table).select {
            if (orderBy != null) order(orderBy, if (desc) Order.DESCENDING else Order.ASCENDING)
            if (limit != null) limit(limit)
        }.decodeList<T>()

    suspend fun loadAll(m: Membership): FarmData = coroutineScope {
        farmId = m.farmId
        val farm = async { rows<Farm>("farms").firstOrNull() }
        val settings = async { rows<FarmSettings>("farm_settings").firstOrNull() }
        val customers = async { rows<Customer>("customers", "customer_code", false) }
        val animals = async { rows<Animal>("animals", "animal_code", false) }
        val weights = async { rows<Weight>("weight_records", "measured_on") }
        val vacc = async { rows<Vaccination>("vaccination_records", "created_at") }
        val treat = async { rows<Treatment>("treatment_records", "observed_on") }
        val assign = async { rows<CareAssignment>("care_assignments") }
        val owner = async { rows<Ownership>("animal_ownership_history", "effective_from") }
        val invoices = async { rows<Invoice>("invoices", "created_at") }
        val items = async { rows<InvoiceItem>("invoice_items") }
        val payments = async { rows<Payment>("payments", "paid_on") }
        val logs = async { if (m.isAdmin) rows<ActivityLog>("activity_logs", "id", limit = 300) else emptyList() }
        val members = async { rows<Membership>("farm_memberships") }
        val profiles = async { rows<Profile>("profiles") }
        val tokens = async { rows<QrToken>("animal_qr_tokens", "created_at") }
        FarmData(farm.await(), settings.await(), customers.await(), animals.await(), weights.await(), vacc.await(),
            treat.await(), assign.await(), owner.await(), invoices.await(), items.await(), payments.await(),
            logs.await(), members.await(), profiles.await(), tokens.await())
    }

    suspend fun vaccinationsOnly(): List<Vaccination> = rows("vaccination_records", "created_at")

    // ---- customers ----
    suspend fun createCustomer(c: NewCustomer): Customer = sb.from("customers").insert(c) { select() }.decodeSingle<Customer>()
    suspend fun updateCustomer(id: String, name: String, phone: String, alt: String?, address: String?, notes: String?, status: String) {
        sb.from("customers").update(buildJsonObject {
            put("full_name", name); put("phone", phone); put("alt_phone", alt); put("address", address); put("notes", notes); put("status", status)
        }) { filter { eq("id", id) } }
    }
    fun customerDraft(name: String, phone: String, alt: String?, address: String?, notes: String?) =
        NewCustomer(farmId, name, phone, alt?.ifBlank { null }, address?.ifBlank { null }, notes?.ifBlank { null })

    // ---- animals ----
    class Registration(val species: String, val customerId: String, val name: String?, val breed: String?, val gender: String?,
        val dob: String?, val ageMonths: Int?, val arrival: String, val idNotes: String?, val healthNotes: String?, val remarks: String?,
        val weightKg: Double?, val weightDate: String?, val dailyRate: Double?)

    suspend fun registerAnimal(r: Registration): Animal {
        val animal = sb.postgrest.rpc("register_animal", buildJsonObject {
            put("p_farm", farmId); put("p_species", r.species); put("p_customer", r.customerId); put("p_op", op())
            put("p", buildJsonObject {
                put("name", r.name); put("breed", r.breed); put("gender", r.gender); put("date_of_birth", r.dob)
                put("estimated_age_months", r.ageMonths?.toString()); put("arrival_date", r.arrival)
                put("identification_notes", r.idNotes); put("health_notes", r.healthNotes); put("remarks", r.remarks)
            })
        }).decodeAs<Animal>()
        if (r.weightKg != null) try { addWeight(animal.id, r.weightKg, r.weightDate ?: r.arrival, "Initial weight") } catch (_: QueuedException) {}
        if (r.dailyRate != null) sb.from("care_assignments").insert(NewAssignment(farmId, animal.id, r.dailyRate, "daily", r.arrival))
        return animal
    }

    /** Optimistic update: fails with a conflict message if another user changed the record first. */
    suspend fun updateAnimal(a: Animal, fields: JsonObject) {
        val updated = sb.from("animals").update(fields) { filter { eq("id", a.id); eq("version", a.version) }; select() }.decodeList<Animal>()
        if (updated.isEmpty()) throw IllegalStateException("Conflict: this animal was changed by someone else. Refresh and try again.")
    }
    suspend fun returnAnimal(a: Animal, date: String) {
        updateAnimal(a, buildJsonObject { put("status", "returned"); put("return_date", date) })
        sb.from("care_assignments").update(buildJsonObject { put("care_end", date) }) { filter { eq("animal_id", a.id); exact("care_end", null) } }
    }
    suspend fun transferAnimal(animalId: String, newCustomer: String, date: String) {
        sb.postgrest.rpc("transfer_animal", buildJsonObject { put("p_animal", animalId); put("p_new_customer", newCustomer); put("p_effective", date) })
    }

    // ---- QR ----
    suspend fun resolveQr(token: String): String? =
        sb.from("animal_qr_tokens").select { filter { eq("token", token); exact("revoked_at", null) } }.decodeList<QrToken>().firstOrNull()?.animalId
    suspend fun reissueQr(animalId: String) {
        sb.from("animal_qr_tokens").update(buildJsonObject { put("revoked_at", java.time.Instant.now().toString()) }) {
            filter { eq("animal_id", animalId); exact("revoked_at", null) } }
        sb.from("animal_qr_tokens").insert(NewToken(farmId, animalId))
    }

    // ---- weights ----
    suspend fun addWeight(animalId: String, kg: Double, date: String, notes: String?) {
        val w = NewWeight(farmId, animalId, kg, date, notes?.ifBlank { null }, uid, op())
        insertOrQueue("weight_records", "Weight %.1f kg on $date".format(kg), w.opId, json.encodeToJsonElement(NewWeight.serializer(), w) as JsonObject)
    }

    // ---- health ----
    suspend fun addVaccination(animalId: String, vaccine: String, status: String, scheduledFor: String?, administeredOn: String?,
        nextDue: String?, by: String?, dose: String?, batch: String?, notes: String?) {
        val v = NewVaccination(farmId, animalId, vaccine, status, scheduledFor, administeredOn, nextDue?.ifBlank { null }, by?.ifBlank { null },
            dose?.ifBlank { null }, batch?.ifBlank { null }, notes?.ifBlank { null }, uid, op())
        insertOrQueue("vaccination_records", "Vaccination: $vaccine ($status)", v.opId, json.encodeToJsonElement(NewVaccination.serializer(), v) as JsonObject)
    }
    suspend fun completeVaccination(id: String, date: String, by: String?, nextDue: String?, batch: String?, notes: String?) {
        sb.from("vaccination_records").update(buildJsonObject {
            put("status", "completed"); put("administered_on", date); put("administered_by", by); put("next_due_date", nextDue)
            put("batch_no", batch); put("notes", notes) }) { filter { eq("id", id); eq("status", "scheduled") } }
    }
    suspend fun cancelVaccination(id: String) {
        sb.from("vaccination_records").update(buildJsonObject { put("status", "cancelled") }) { filter { eq("id", id); eq("status", "scheduled") } }
    }
    suspend fun addTreatment(t: NewTreatment) { val x = t.copy(farmId = farmId, createdBy = uid, opId = op())
        insertOrQueue("treatment_records", "Treatment ${x.observedOn}", x.opId, json.encodeToJsonElement(NewTreatment.serializer(), x) as JsonObject) }
    fun blankTreatment(animalId: String) = NewTreatment(farmId, animalId, java.time.LocalDate.now().toString(), createdBy = uid, opId = op())

    // ---- money ----
    suspend fun recordPayment(customerId: String, amount: Double, method: String, ref: String?, date: String) {
        sb.from("payments").insert(NewPayment(farmId, customerId, amount, method, ref?.ifBlank { null }, date, uid, op()))
    }

    /** Creates an invoice from care assignments, ownership periods and the billing period. */
    suspend fun generateInvoice(customerId: String, start: java.time.LocalDate, end: java.time.LocalDate, d: FarmData): String {
        val lines = com.farmcare.app.domain.InvoicePlanner.plan(customerId, start, end, d)
        if (lines.isEmpty()) throw IllegalStateException("No billable animals for this customer in the selected period.")
        val total = lines.fold(java.math.BigDecimal.ZERO) { s, l -> s + l.amount }.toDouble()
        val no = "INV-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + UUID.randomUUID().toString().take(4).uppercase()
        val inv = sb.from("invoices").insert(NewInvoice(farmId, no, customerId, start.toString(), end.toString(), total, total, uid)) { select() }.decodeSingle<Invoice>()
        try {
            sb.from("invoice_items").insert(lines.map { NewInvoiceItem(inv.id, farmId, it.animalId, it.description, it.days.toDouble(),
                it.rate.toDouble(), it.amount.toDouble(), it.start.toString(), it.end.toString()) })
        } catch (e: Exception) {
            sb.from("invoices").update(buildJsonObject { put("status", "void") }) { filter { eq("id", inv.id) } }
            throw IllegalStateException(if (e.message?.contains("invoice_items_no_dupe") == true || e.message?.contains("duplicate") == true)
                "Part of this period is already invoiced for one of the animals." else e.message ?: "Could not create invoice")
        }
        return no
    }

    // ---- settings & staff ----
    suspend fun saveWeightTracking(map: Map<String, Boolean>) {
        sb.from("farm_settings").update(buildJsonObject { put("weight_tracking", buildJsonObject { map.forEach { (k, v) -> put(k, v) } }) }) { filter { eq("farm_id", farmId) } }
    }
    suspend fun saveDefaultRates(current: JsonObject?, rates: Map<String, Double>) {
        val merged = buildJsonObject {
            current?.forEach { (k, v) -> if (k != "default_rates") put(k, v) }
            put("default_rates", buildJsonObject { rates.forEach { (k, v) -> put(k, v) } })
        }
        sb.from("farm_settings").update(buildJsonObject { put("billing_rules", merged) }) { filter { eq("farm_id", farmId) } }
    }
    suspend fun saveReminderDays(days: List<Int>) {
        sb.from("farm_settings").update(buildJsonObject { put("reminder_days_before", JsonArray(days.map { JsonPrimitive(it) })) }) { filter { eq("farm_id", farmId) } }
    }
    suspend fun saveFarmName(name: String) { sb.from("farms").update(buildJsonObject { put("name", name) }) { filter { eq("id", farmId) } } }
    suspend fun addMember(email: String, role: String, pay: Boolean, fin: Boolean) {
        sb.postgrest.rpc("add_member_by_email", buildJsonObject { put("p_farm", farmId); put("p_email", email.trim()); put("p_role", role); put("p_pay", pay); put("p_fin", fin) })
    }
    suspend fun removeMember(userId: String) { sb.from("farm_memberships").delete { filter { eq("farm_id", farmId); eq("user_id", userId) } } }
}
