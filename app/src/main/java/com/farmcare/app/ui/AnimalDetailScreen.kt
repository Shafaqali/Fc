package com.farmcare.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farmcare.app.FarmViewModel
import com.farmcare.app.data.*
import com.farmcare.app.domain.HealthRules
import com.farmcare.app.domain.VaccStatus
import com.farmcare.app.ui.theme.*
import com.farmcare.app.util.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

@Composable
fun AnimalDetailScreen(vm: FarmViewModel, id: String, onBack: () -> Unit, onOwner: (String) -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val m = ui.membership!!; val ctx = LocalContext.current
    val a = d.animal(id)
    if (a == null) { AppScaffold("Animal", onBack) { p -> Box(Modifier.padding(p).padding(24.dp)) { if (ui.loading) Loading() else Text("This animal was not found or you do not have access to it.") } }; return }
    val tabs = buildList { add("Overview"); add("Weight"); add("Vaccines"); add("Treatments"); if (m.canFinance) add("Billing"); add("QR tag"); if (m.isAdmin) add("Activity") }
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }; var menu by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val owner = d.customer(a.customerId); val tracking = d.trackingEnabled(a)
    fun done(e: String?) { toast = e; if (e == null) dialog = null }

    AppScaffold("${a.code}  ${cap(a.species)}", onBack, actions = {
        IconButton({ shareText(ctx, "FarmCare animal report\nID: ${a.code}\nSpecies: ${cap(a.species)}\nOwner: ${owner?.name ?: "-"}\nStatus: ${cap(a.status)}\n" +
            (weightLine(d, a)?.let { "Weight: $it\n" } ?: "")) }) { Icon(Icons.Default.Share, "Share report") }
        if (m.canWrite) Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Edit") }, { menu = false; dialog = "edit" })
                if (m.isAdmin && a.status == "active") { DropdownMenuItem({ Text("Return to owner") }, { menu = false; dialog = "return" })
                    DropdownMenuItem({ Text("Transfer ownership") }, { menu = false; dialog = "transfer" }) }
                if (m.isAdmin && a.status != "archived") DropdownMenuItem({ Text("Archive") }, { menu = false; dialog = "archive" })
            } }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(tab, edgePadding = 8.dp, containerColor = androidx.compose.ui.graphics.Color.White, contentColor = Forest) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
            toast?.let { Text(it, color = Danger, modifier = Modifier.padding(12.dp)) }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 900.dp)) {
                when (tabs[tab]) {
                    "Overview" -> {
                        Panel {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(a.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Forest)
                                Chip(cap(a.status), if (a.status == "active") Tone.Good else Tone.Warn) }
                            KV("Species", cap(a.species)); a.name?.let { KV("Name", it) }; a.breed?.let { KV("Breed", it) }; a.gender?.let { KV("Gender", cap(it)) }
                            a.dob?.let { KV("Date of birth", it) } ?: a.ageMonths?.let { KV("Estimated age", "$it months") }
                            KV("Owner", owner?.name ?: "-"); a.arrivalDate?.let { KV("Arrival", it) }; a.returnDate?.let { KV("Returned", it) }
                            val care = d.assignments.filter { it.animalId == a.id && it.end == null }.firstOrNull()
                            if (m.canFinance && care?.rate != null) KV("Care rate", money(care.rate) + if (care.unit == "monthly") "/month" else "/day")
                            WeightSummary(d, a)
                            a.idNotes?.let { KV("Identification", it) }; a.healthNotes?.let { KV("Health notes", it) }; a.remarks?.let { KV("Remarks", it) }
                        }
                        SectionTitle("Quick actions")
                        if (m.canWrite) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tracking) OutlinedButton({ dialog = "weight" }) { Text("Add weight") }
                            OutlinedButton({ dialog = "vacc" }) { Text("Vaccination") }
                            OutlinedButton({ dialog = "treat" }) { Text("Treatment") } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (m.canPay) OutlinedButton({ dialog = "pay" }) { Text("Record payment") }
                            OutlinedButton({ onOwner(a.customerId) }) { Text("View owner") } }
                    }
                    "Weight" -> WeightTab(d, a, tracking, m.canWrite) { dialog = "weight" }
                    "Vaccines" -> VaccTab(d, a, m.canWrite, { dialog = "vacc" }) { v, act -> if (act == "complete") dialog = "complete:${v.id}" else dialog = "cancel:${v.id}" }
                    "Treatments" -> {
                        if (m.canWrite) Button({ dialog = "treat" }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Add treatment record") }
                        val list = d.treatments.filter { it.animalId == a.id }
                        if (list.isEmpty()) EmptyNote("No treatment records.")
                        list.forEach { t -> Panel(Modifier.padding(top = 8.dp)) {
                            Text(t.observedOn, fontWeight = FontWeight.Bold)
                            t.symptoms?.let { KV("Symptoms", it) }; t.diagnosis?.let { KV("Diagnosis", it) }; t.details?.let { KV("Treatment", it) }
                            t.medicine?.let { KV("Medicine", it) }; t.instructions?.let { KV("Vet instructions", it) }
                            t.followUp?.let { KV("Follow-up", it) }; t.recovery?.let { KV("Recovery", it) }; t.by?.let { KV("Recorded by", it) } } }
                    }
                    "Billing" -> {
                        val items = d.items.filter { it.animalId == a.id }
                        if (items.isEmpty()) EmptyNote("This animal has not been invoiced yet.")
                        items.forEach { Panel(Modifier.padding(bottom = 8.dp)) { Text(it.description); Text(money(it.amount), fontWeight = FontWeight.Bold) } }
                        if (m.canPay) Button({ dialog = "pay" }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Record payment for owner") }
                    }
                    "QR tag" -> QrTab(vm, d, a, m.isAdmin)
                    "Activity" -> { val l = d.logs.filter { it.rowId == a.id }
                        if (l.isEmpty()) EmptyNote("No recorded changes among the latest 300 log entries.")
                        l.forEach { Text("${it.at.take(16).replace('T', ' ')}  ${it.action.lowercase()} by ${d.memberName(it.actor)}", Modifier.padding(vertical = 4.dp)) } }
                }
            }
        }
    }
    // ---- dialogs ----
    var err by remember(dialog) { mutableStateOf<String?>(null) }; var busy by remember(dialog) { mutableStateOf(false) }
    fun submit(block: suspend FarmRepository.() -> Unit) { busy = true; vm.run({ e -> busy = false; err = e; if (e == null) dialog = null }, block) }
    when (val dlg = dialog) {
        "weight" -> { var kg by remember { mutableStateOf("") }; var dt by remember { mutableStateOf(LocalDate.now().toString()) }; var n by remember { mutableStateOf("") }
            FormDialog("Add weight", "Save", { dialog = null }, err, busy, {
                val v = kg.toDoubleOrNull(); if (v == null || v <= 0) err = "Enter a weight above 0 kg." else submit { addWeight(a.id, v, dt, n) } }) {
                Field("Weight (kg)", kg, { kg = it }, keyboard = KeyboardType.Decimal); DateField("Measured on", dt, { dt = it }); Field("Notes", n, { n = it }) } }
        "vacc" -> { var mode by remember { mutableStateOf("scheduled") }; var name by remember { mutableStateOf("") }; var dt by remember { mutableStateOf(LocalDate.now().toString()) }
            var next by remember { mutableStateOf("") }; var by by remember { mutableStateOf("") }; var dose by remember { mutableStateOf("") }; var batch by remember { mutableStateOf("") }; var n by remember { mutableStateOf("") }
            FormDialog("Vaccination", "Save", { dialog = null }, err, busy, {
                if (name.isBlank()) err = "Enter the vaccine name." else submit { addVaccination(a.id, name.trim(), mode, if (mode == "scheduled") dt else null, if (mode == "completed") dt else null, next, by, dose, batch, n) } }) {
                DropdownField("Type", listOf("scheduled" to "Schedule a vaccination", "completed" to "Record an administered vaccination"), mode, { mode = it })
                Field("Vaccine name", name, { name = it }); DateField(if (mode == "scheduled") "Scheduled for" else "Administered on", dt, { dt = it })
                DateField("Next due date (optional)", next, { next = it }, clearable = true); Field("Administered by", by, { by = it })
                Field("Dose / administration details", dose, { dose = it }); Field("Batch or lot (optional)", batch, { batch = it }); Field("Notes", n, { n = it }) } }
        "treat" -> { var obs by remember { mutableStateOf(LocalDate.now().toString()) }; var sym by remember { mutableStateOf("") }; var dx by remember { mutableStateOf("") }
            var vet by remember { mutableStateOf(false) }; var det by remember { mutableStateOf("") }; var med by remember { mutableStateOf("") }; var ins by remember { mutableStateOf("") }
            var fu by remember { mutableStateOf("") }; var rec by remember { mutableStateOf("") }; var by by remember { mutableStateOf("") }
            FormDialog("Add treatment record", "Save", { dialog = null }, err, busy, {
                if (sym.isBlank() && det.isBlank()) err = "Enter symptoms or treatment details." else
                submit { addTreatment(blankTreatment(a.id).copy(observedOn = obs, symptoms = sym.ifBlank { null }, diagnosis = dx.ifBlank { null }, vet = vet,
                    details = det.ifBlank { null }, medicine = med.ifBlank { null }, instructions = ins.ifBlank { null }, followUp = fu.ifBlank { null }, recovery = rec.ifBlank { null }, by = by.ifBlank { null })) } }) {
                DateField("Observation date", obs, { obs = it }); Field("Symptoms or condition", sym, { sym = it }, singleLine = false); Field("Confirmed diagnosis (if any)", dx, { dx = it })
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(vet, { vet = it }); Text("Veterinarian consulted") }
                Field("Treatment details", det, { det = it }, singleLine = false); Field("Medicine (as prescribed)", med, { med = it }); Field("Veterinarian instructions", ins, { ins = it }, singleLine = false)
                DateField("Follow-up date (optional)", fu, { fu = it }, clearable = true); Field("Recovery notes", rec, { rec = it }); Field("Veterinarian or responsible person", by, { by = it }) } }
        "pay" -> PaymentDialog(vm, owner, a.customerId, { dialog = null })
        "edit" -> { var nm by remember { mutableStateOf(a.name.orEmpty()) }; var br by remember { mutableStateOf(a.breed.orEmpty()) }; var hn by remember { mutableStateOf(a.healthNotes.orEmpty()) }
            var rm by remember { mutableStateOf(a.remarks.orEmpty()) }; var tr by remember { mutableStateOf(a.trackingOverride?.toString() ?: "default") }
            FormDialog("Edit ${a.code}", "Save", { dialog = null }, err, busy, {
                submit { updateAnimal(a, buildJsonObject { put("name", nm.ifBlank { null }); put("breed", br.ifBlank { null }); put("health_notes", hn.ifBlank { null }); put("remarks", rm.ifBlank { null })
                    if (m.isAdmin) { if (tr == "default") put("weight_tracking_override", null as String?) else put("weight_tracking_override", tr == "true") } }) } }) {
                Field("Name", nm, { nm = it }); Field("Breed", br, { br = it }); Field("Health notes", hn, { hn = it }, singleLine = false); Field("Remarks", rm, { rm = it }, singleLine = false)
                if (m.isAdmin) DropdownField("Weight tracking for this animal", listOf("default" to "Follow species setting", "true" to "Enabled", "false" to "Disabled"), tr, { tr = it }) } }
        "return" -> { var dt by remember { mutableStateOf(LocalDate.now().toString()) }
            FormDialog("Return ${a.code} to owner", "Return", { dialog = null }, err, busy, { submit { returnAnimal(a, dt) } }) {
                Text("Care charges stop on this date. All records are kept."); DateField("Return date", dt, { dt = it }) } }
        "transfer" -> { var to by remember { mutableStateOf<String?>(null) }; var dt by remember { mutableStateOf(LocalDate.now().toString()) }
            FormDialog("Transfer ${a.code}", "Transfer", { dialog = null }, err, busy, { if (to == null) err = "Choose the new owner." else submit { transferAnimal(a.id, to!!, dt) } }) {
                DropdownField("New owner", d.customers.filter { it.id != a.customerId }.map { it.id to it.name }, to, { to = it }); DateField("Effective date", dt, { dt = it })
                Text("The previous owner is billed up to the day before this date.") } }
        "archive" -> ConfirmDialog("Archive ${a.code}?", "The animal is hidden from active lists. Its health, weight, billing and ownership history are kept.", "Archive",
            { vm.run({ e -> toast = e; dialog = null }) { updateAnimal(a, buildJsonObject { put("status", "archived") }) } }, { dialog = null })
        else -> if (dlg != null && dlg.startsWith("complete:")) { val vid = dlg.removePrefix("complete:"); var dt by remember { mutableStateOf(LocalDate.now().toString()) }
            var by by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; var batch by remember { mutableStateOf("") }; var n by remember { mutableStateOf("") }
            FormDialog("Mark vaccination completed", "Complete", { dialog = null }, err, busy, { submit { completeVaccination(vid, dt, by.ifBlank { null }, next.ifBlank { null }, batch.ifBlank { null }, n.ifBlank { null }) } }) {
                DateField("Administered on", dt, { dt = it }); Field("Administered by", by, { by = it }); DateField("Next due date (optional)", next, { next = it }, clearable = true)
                Field("Batch or lot", batch, { batch = it }); Field("Notes", n, { n = it }) } }
        else if (dlg != null && dlg.startsWith("cancel:")) ConfirmDialog("Cancel this scheduled vaccination?", "The record stays in history as cancelled.", "Cancel vaccination",
            { vm.run({ e -> toast = e; dialog = null }) { cancelVaccination(dlg.removePrefix("cancel:")) } }, { dialog = null })
    }
}

@Composable private fun KV(k: String, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.SpaceBetween) {
    Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f)); Text(v, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f)) }

@Composable private fun WeightSummary(d: FarmData, a: Animal) {
    if (!d.trackingEnabled(a)) { KV("Weight", "Weight tracking not enabled"); return }
    val w = d.weightsOf(a.id)
    if (w.isEmpty()) { KV("Weight", "Weight not recorded"); return }
    KV("Current weight", "%.1f kg".format(w[0].kg))
    if (w.size < 2) KV("Previous", "No previous measurement available")
    else { val diff = w[0].kg - w[1].kg; KV("Previous", "%.1f kg".format(w[1].kg)); KV("Change", "%+.1f kg".format(diff)) }
}

@Composable
private fun WeightTab(d: FarmData, a: Animal, tracking: Boolean, canWrite: Boolean, onAdd: () -> Unit) {
    if (!tracking) { Text("Weight tracking not enabled"); Text("An administrator can enable it for this species or this animal.", style = MaterialTheme.typography.bodySmall); return }
    val w = d.weightsOf(a.id)
    if (canWrite) Button(onAdd, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Add weight") }
    if (w.isEmpty()) { EmptyNote("Weight not recorded"); return }
    Panel(Modifier.padding(top = 10.dp)) { WeightSummary(d, a) }
    if (w.size >= 2) { Spacer(Modifier.height(10.dp)); Panel { Text("Trend (kg)", fontWeight = FontWeight.SemiBold); LineChart(w.reversed().map { it.kg }) } }
    SectionTitle("History")
    w.forEachIndexed { i, r -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween) {
        Column { Text(r.measuredOn); r.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) { Text("%.1f kg".format(r.kg), fontWeight = FontWeight.Bold)
            if (i + 1 < w.size) Text("%+.1f".format(r.kg - w[i + 1].kg), style = MaterialTheme.typography.bodySmall) } }; HorizontalDivider() }
}

@Composable
private fun VaccTab(d: FarmData, a: Animal, canWrite: Boolean, onAdd: () -> Unit, onAct: (Vaccination, String) -> Unit) {
    val today = LocalDate.now(); val soon = d.settings?.reminderDays?.maxOrNull() ?: 7
    if (canWrite) Button(onAdd, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Schedule or record vaccination") }
    val list = d.vaccinations.filter { it.animalId == a.id }
    if (list.isEmpty()) EmptyNote("No vaccination records.")
    list.forEach { v ->
        val st = HealthRules.status(v.status, v.scheduledFor?.let { LocalDate.parse(it) }, today, soon)
        Panel(Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(v.vaccine, fontWeight = FontWeight.Bold)
                Chip(st.label, when (st) { VaccStatus.COMPLETED -> Tone.Good; VaccStatus.OVERDUE -> Tone.Bad; VaccStatus.DUE_TODAY, VaccStatus.DUE_SOON -> Tone.Warn; else -> Tone.Neutral }) }
            v.scheduledFor?.let { KV("Scheduled", it) }; v.administeredOn?.let { KV("Administered", it) }; v.nextDue?.let { KV("Next due", it) }
            v.by?.let { KV("By", it) }; v.dose?.let { KV("Dose", it) }; v.batch?.let { KV("Batch", it) }; v.notes?.let { KV("Notes", it) }
            if (canWrite && v.status == "scheduled") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onAct(v, "complete") }) { Text("Mark completed") }; TextButton({ onAct(v, "cancel") }) { Text("Cancel", color = Danger) } }
        }
    }
}

@Composable
private fun QrTab(vm: FarmViewModel, d: FarmData, a: Animal, admin: Boolean) {
    val ctx = LocalContext.current
    val token = d.tokens.filter { it.animalId == a.id && it.revokedAt == null }.firstOrNull()?.token
    var confirm by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf<String?>(null) }
    if (token == null) { EmptyNote("No active QR tag for this animal."); if (admin) Button({ vm.run({ msg = it }) { reissueQr(a.id) } }) { Text("Generate tag") }; msg?.let { Text(it, color = Danger) }; return }
    val bmp = remember(token) { qrBitmap(QR_PREFIX + token, 640) }
    Panel {
        Text(d.farm?.name ?: "FarmCare", fontWeight = FontWeight.Bold, color = Forest)
        Image(bmp.asImageBitmap(), "QR code for ${a.code}", Modifier.size(240.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
        Text(a.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
        Text(cap(a.species), modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
    }
    Text("The tag holds only a random token. Scanning it still requires you to be signed in with permission.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    Button({ shareFile(ctx, writePng(ctx, "tag-${a.code}", tagBitmap(d.farm?.name ?: "FarmCare", a.code, a.species, token)), "image/png") }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Share or print tag") }
    if (admin) OutlinedButton({ confirm = true }) { Text("Revoke and reissue tag") }
    msg?.let { Text(it, color = Danger) }
    if (confirm) ConfirmDialog("Revoke this tag?", "The old tag stops working immediately and a new one is created. Reprint the new tag.", "Revoke",
        { confirm = false; vm.run({ msg = it }) { reissueQr(a.id) } }, { confirm = false })
}

@Composable
fun PaymentDialog(vm: FarmViewModel, owner: Customer?, customerId: String, onDismiss: () -> Unit) {
    val d = vm.s.collectAsState().value.d
    var amt by remember { mutableStateOf("") }; var method by remember { mutableStateOf("cash") }; var ref by remember { mutableStateOf("") }
    var dt by remember { mutableStateOf(LocalDate.now().toString()) }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val methods = (d.settings?.paymentMethods ?: listOf("cash", "upi", "bank_transfer")).map { it to cap(it.replace('_', ' ')) }
    FormDialog("Record payment${owner?.let { " - ${it.name}" } ?: ""}", "Save payment", onDismiss, err, busy, {
        val v = amt.toDoubleOrNull()
        if (v == null || v <= 0) err = "Enter an amount above 0." else { busy = true; vm.run({ e -> busy = false; err = e; if (e == null) onDismiss() }) { recordPayment(customerId, v, method, ref, dt) } } }) {
        Text("Outstanding: " + money(d.balance(customerId)), style = MaterialTheme.typography.bodySmall)
        Field("Amount", amt, { amt = it }, keyboard = KeyboardType.Decimal); DropdownField("Method", methods, method, { method = it })
        Field("Reference number (optional)", ref, { ref = it }); DateField("Payment date", dt, { dt = it }) }
}
