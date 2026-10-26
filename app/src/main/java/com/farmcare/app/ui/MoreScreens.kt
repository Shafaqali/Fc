package com.farmcare.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farmcare.app.AuthViewModel
import com.farmcare.app.FarmViewModel
import com.farmcare.app.data.*
import com.farmcare.app.domain.HealthRules
import com.farmcare.app.domain.VaccStatus
import com.farmcare.app.ui.theme.*
import com.farmcare.app.util.*
import java.time.LocalDate

@Composable
fun MoreScreen(vm: FarmViewModel, auth: AuthViewModel, onNav: (String) -> Unit) {
    val m = vm.s.collectAsState().value.membership!!
    val pend = vm.pending.collectAsState().value.count { it.status != "synced" }
    var confirmOut by remember { mutableStateOf(false) }
    @Composable fun Item(icon: ImageVector, title: String, sub: String, route: String) = Row(Modifier.fillMaxWidth().clickable { onNav(route) }.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(icon, null, tint = Forest); Spacer(Modifier.width(16.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    AppScaffold("More", tab = true) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).widthIn(max = 700.dp)) {
            Text("Signed in as ${cap(m.role.replace("admin", "farm administrator").replace("staff", "farm staff"))}", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            Item(Icons.Default.MedicalServices, "Health tasks", "Vaccinations and follow-ups", "health")
            if (m.canFinance) Item(Icons.Default.Receipt, "Billing and payments", "Invoices, payments, balances", "billing")
            Item(Icons.Default.Assessment, "Reports", "Export CSV and PDF", "reports")
            if (m.isAdmin) { Item(Icons.Default.Badge, "Staff and permissions", "Add staff, set roles", "staff"); Item(Icons.Default.History, "Audit history", "Who changed what", "audit") }
            Item(Icons.Default.Sync, "Synchronisation", "Offline changes and data freshness", "sync")
            Item(Icons.Default.Settings, "Settings", "Farm, weight tracking, rates, reminders", "settings")
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().clickable { if (pend > 0) confirmOut = true else auth.signOut() }.padding(16.dp)) { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Danger); Spacer(Modifier.width(16.dp)); Text("Sign out", color = Danger, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (confirmOut) ConfirmDialog("Sign out with unsent changes?", "$pend change(s) have not reached the server and will be deleted from this device if you sign out. Sync first to keep them.", "Sign out anyway",
        { confirmOut = false; auth.signOut() }, { confirmOut = false })
}

@Composable
fun SyncScreen(vm: FarmViewModel, onBack: () -> Unit) {
    val ui by vm.s.collectAsState(); val ops by vm.pending.collectAsState(); var msg by remember { mutableStateOf<String?>(null) }
    AppScaffold("Synchronisation", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 800.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel {
                if (ui.offline) { Text("Offline", fontWeight = FontWeight.Bold, color = Warning)
                    Text("Showing data saved on ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(ui.savedAt))}. It may be out of date.") }
                else Text("Connected. Data was refreshed from the server when this screen last loaded.")
            }
            Text("Offline entry is supported for weights, vaccinations and treatments. Everything else needs a connection. A queued entry is not on the server until it shows as uploaded.", style = MaterialTheme.typography.bodySmall)
            Button({ vm.syncNow { msg = it } }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Sync now") }
            msg?.let { Text(it) }
            SectionTitle("Queued changes (${ops.count { it.status != "synced" }})")
            if (ops.none { it.status != "synced" }) EmptyNote("Nothing waiting.")
            ops.filter { it.status != "synced" }.forEach { op -> Panel {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(op.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Chip(if (op.status == "failed") "Failed" else "Waiting", if (op.status == "failed") Tone.Bad else Tone.Warn) }
                op.error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ vm.retryOp(op.opId) }) { Text("Retry") }; TextButton({ vm.discardOp(op.opId) }) { Text("Discard", color = Danger) } } } }
        }
    }
}

@Composable
fun HealthTasksScreen(vm: FarmViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val d = vm.s.collectAsState().value.d; val today = LocalDate.now(); val soonDays = d.settings?.reminderDays?.maxOrNull() ?: 7
    val pending = HealthRules.pending(d.vaccinations, today, soonDays)
    val fups = d.treatments.mapNotNull { t -> t.followUp?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { t to it } }.sortedBy { it.second }
        .filter { (_, dt) -> !dt.isAfter(today.plusDays(soonDays.toLong())) }
    AppScaffold("Health tasks", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 800.dp)) {
            Text("Reminders are computed from stored dates. Dismissing a notification never completes a task; open the animal and record it.", style = MaterialTheme.typography.bodySmall)
            listOf(VaccStatus.OVERDUE to "Overdue vaccinations", VaccStatus.DUE_TODAY to "Due today", VaccStatus.DUE_SOON to "Due soon").forEach { (st, title) ->
                val l = pending.filter { it.status == st }; SectionTitle("$title (${l.size})")
                if (l.isEmpty()) EmptyNote("None.")
                l.forEach { p -> val a = d.animal(p.record.animalId); Panel(Modifier.padding(bottom = 6.dp).clickable { a?.let { onOpen(it.id) } }) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("${a?.code ?: "-"}  ${p.record.vaccine}", fontWeight = FontWeight.SemiBold)
                        Chip(p.due.toString(), if (st == VaccStatus.OVERDUE) Tone.Bad else Tone.Warn) } } }
            }
            SectionTitle("Treatment follow-ups (${fups.size})")
            if (fups.isEmpty()) EmptyNote("None.")
            fups.forEach { (t, dt) -> val a = d.animal(t.animalId); Panel(Modifier.padding(bottom = 6.dp).clickable { a?.let { onOpen(it.id) } }) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("${a?.code ?: "-"}  ${t.symptoms ?: t.details ?: "Follow-up"}".take(50), fontWeight = FontWeight.SemiBold)
                    Chip(if (dt.isBefore(today)) "Overdue $dt" else dt.toString(), if (dt.isBefore(today)) Tone.Bad else Tone.Warn) } } }
        }
    }
}

@Composable
fun BillingScreen(vm: FarmViewModel, onBack: () -> Unit, onCustomer: (String) -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val m = ui.membership!!
    var create by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf<String?>(null) }
    AppScaffold("Billing and payments", onBack, fab = { if (m.isAdmin) ExtendedFloatingActionButton(onClick = { create = true }, containerColor = Forest, contentColor = androidx.compose.ui.graphics.Color.White,
        icon = { Icon(Icons.Default.Add, null) }, text = { Text("Create invoice") }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 900.dp)) {
            msg?.let { Text(it, color = Success) }
            SectionTitle("Outstanding balances")
            val owing = d.customers.map { it to d.balance(it.id) }.filter { it.second.signum() != 0 }.sortedByDescending { it.second }
            if (owing.isEmpty()) EmptyNote("No outstanding balances.")
            owing.forEach { (c, b) -> Row(Modifier.fillMaxWidth().clickable { onCustomer(c.id) }.padding(vertical = 8.dp), Arrangement.SpaceBetween) {
                Text(c.name, fontWeight = FontWeight.SemiBold); Text(money(b), color = if (b.signum() > 0) Danger else Success, fontWeight = FontWeight.Bold) }; HorizontalDivider() }
            SectionTitle("Invoices")
            if (d.invoices.isEmpty()) EmptyNote("No invoices yet.")
            d.invoices.forEach { i -> Row(Modifier.fillMaxWidth().clickable { onCustomer(i.customerId) }.padding(vertical = 6.dp), Arrangement.SpaceBetween) {
                Column { Text(i.no, fontWeight = FontWeight.SemiBold); Text("${d.customer(i.customerId)?.name ?: "-"}  ${i.start} to ${i.end}", style = MaterialTheme.typography.bodySmall) }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) { Text(money(i.total)); if (i.status != "issued") Chip(cap(i.status)) } }; HorizontalDivider() }
            SectionTitle("Payment history")
            if (d.payments.isEmpty()) EmptyNote("No payments yet.")
            d.payments.take(50).forEach { p -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween) {
                Text("${p.paidOn}  ${d.customer(p.customerId)?.name ?: "-"}  (${p.method})"); Text(money(p.amount), color = Success, fontWeight = FontWeight.SemiBold) }; HorizontalDivider() }
            Spacer(Modifier.height(80.dp))
        }
    }
    if (create) {
        var cid by remember { mutableStateOf<String?>(null) }; var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
        var to by remember { mutableStateOf(LocalDate.now().toString()) }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
        var preview by remember(cid, from, to) { mutableStateOf<List<com.farmcare.app.domain.InvoicePlanner.Line>?>(null) }
        FormDialog("Create invoice", "Generate invoice", { create = false }, err, busy, {
            val s = runCatching { LocalDate.parse(from) }.getOrNull(); val e = runCatching { LocalDate.parse(to) }.getOrNull()
            when { cid == null -> err = "Choose a customer."; s == null || e == null || e.isBefore(s) -> err = "End date must not be before the start date."
                else -> { busy = true; vm.run({ er -> busy = false; err = er; if (er == null) { create = false; msg = "Invoice created." } }) { generateInvoice(cid!!, s, e, vm.s.value.d) } } }
        }) {
            DropdownField("Customer", d.customers.map { it.id to it.name }, cid, { cid = it })
            DateField("Period start", from, { from = it }); DateField("Period end", to, { to = it })
            val s = runCatching { LocalDate.parse(from) }.getOrNull(); val e = runCatching { LocalDate.parse(to) }.getOrNull()
            if (cid != null && s != null && e != null && !e.isBefore(s)) {
                val lines = com.farmcare.app.domain.InvoicePlanner.plan(cid!!, s, e, d)
                if (lines.isEmpty()) Text("No billable animals in this period.", style = MaterialTheme.typography.bodySmall)
                else { lines.forEach { Text(it.description + "  " + money(it.amount), style = MaterialTheme.typography.bodySmall) }
                    Text("Total " + money(lines.fold(java.math.BigDecimal.ZERO) { a, l -> a + l.amount }), fontWeight = FontWeight.Bold) }
            }
            Text("Daily rates bill every calendar day from care start to care end, both included. Monthly rates are prorated by day for partial months.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ReportsScreen(vm: FarmViewModel, onBack: () -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val m = ui.membership!!; val ctx = LocalContext.current
    var sp by remember { mutableStateOf("") }; var st by remember { mutableStateOf("") }; var cust by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }
    fun inRange(date: String) = (from.isBlank() || date >= from) && (to.isBlank() || date <= to)
    val animals = d.animals.filter { (sp.isEmpty() || it.species == sp) && (st.isEmpty() || it.status == st) && (cust.isEmpty() || it.customerId == cust) }
    val ids = animals.map { it.id }.toSet()
    data class Rep(val name: String, val rows: List<List<String>>)
    val reports = buildList {
        add(Rep("Animal inventory", listOf(listOf("ID", "Species", "Name", "Owner", "Status", "Arrival", "Latest weight kg")) + animals.map { a -> listOf(a.code, a.species, a.name.orEmpty(), d.customer(a.customerId)?.name.orEmpty(), a.status, a.arrivalDate.orEmpty(),
            if (d.trackingEnabled(a)) d.weightsOf(a.id).firstOrNull()?.kg?.toString() ?: "Weight not recorded" else "Weight tracking not enabled") }))
        add(Rep("Species-wise counts", listOf(listOf("Species", "Count")) + listOf("goat", "buffalo", "sheep").map { s -> listOf(s, animals.count { it.species == s }.toString()) }))
        add(Rep("Weight history", listOf(listOf("ID", "Date", "Weight kg", "Notes")) + d.weights.filter { it.animalId in ids && inRange(it.measuredOn) }.map { w -> listOf(d.animal(w.animalId)?.code.orEmpty(), w.measuredOn, w.kg.toString(), w.notes.orEmpty()) }))
        add(Rep("Vaccination history", listOf(listOf("ID", "Vaccine", "Status", "Scheduled", "Administered", "Next due", "By")) + d.vaccinations.filter { it.animalId in ids }.map { v -> listOf(d.animal(v.animalId)?.code.orEmpty(), v.vaccine, v.status, v.scheduledFor.orEmpty(), v.administeredOn.orEmpty(), v.nextDue.orEmpty(), v.by.orEmpty()) }))
        add(Rep("Treatment history", listOf(listOf("ID", "Observed", "Symptoms", "Diagnosis", "Treatment", "Follow-up")) + d.treatments.filter { it.animalId in ids && inRange(it.observedOn) }.map { t -> listOf(d.animal(t.animalId)?.code.orEmpty(), t.observedOn, t.symptoms.orEmpty(), t.diagnosis.orEmpty(), t.details.orEmpty(), t.followUp.orEmpty()) }))
        add(Rep("Ownership transfers", listOf(listOf("ID", "Owner", "From", "To")) + d.ownership.filter { it.animalId in ids }.map { o -> listOf(d.animal(o.animalId)?.code.orEmpty(), d.customer(o.customerId)?.name.orEmpty(), o.from, o.to.orEmpty()) }))
        add(Rep("Arrivals and returns", listOf(listOf("ID", "Owner", "Arrival", "Return")) + animals.map { a -> listOf(a.code, d.customer(a.customerId)?.name.orEmpty(), a.arrivalDate.orEmpty(), a.returnDate.orEmpty()) }))
        if (m.canFinance) {
            add(Rep("Care charges (invoices)", listOf(listOf("Invoice", "Customer", "From", "To", "Total", "Status")) + d.invoices.filter { inRange(it.start) && (cust.isEmpty() || it.customerId == cust) }.map { i -> listOf(i.no, d.customer(i.customerId)?.name.orEmpty(), i.start, i.end, i.total.toString(), i.status) }))
            add(Rep("Payment collections", listOf(listOf("Date", "Customer", "Method", "Reference", "Amount")) + d.payments.filter { inRange(it.paidOn) && (cust.isEmpty() || it.customerId == cust) }.map { p -> listOf(p.paidOn, d.customer(p.customerId)?.name.orEmpty(), p.method, p.reference.orEmpty(), p.amount.toString()) }))
            add(Rep("Outstanding balances", listOf(listOf("Customer", "Charges", "Paid", "Balance")) + d.customers.filter { cust.isEmpty() || it.id == cust }.map { c -> listOf(c.name, d.charged(c.id).toString(), d.paid(c.id).toString(), d.balance(c.id).toString()) }))
        }
    }
    AppScaffold("Reports", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reports include only the data your role is allowed to see.", style = MaterialTheme.typography.bodySmall)
            DropdownField("Species", listOf("" to "All species", "goat" to "Goat", "buffalo" to "Buffalo", "sheep" to "Sheep"), sp, { sp = it })
            DropdownField("Status", listOf("" to "Any status", "active" to "Active", "returned" to "Returned", "archived" to "Archived"), st, { st = it })
            DropdownField("Customer", listOf("" to "All customers") + d.customers.map { it.id to it.name }, cust, { cust = it })
            DateField("From date", from, { from = it }, clearable = true); DateField("To date", to, { to = it }, clearable = true)
            reports.forEach { r -> Panel { Text(r.name, fontWeight = FontWeight.Bold); Text("${r.rows.size - 1} rows", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ shareFile(ctx, writeCsv(ctx, r.name.replace(' ', '-').lowercase(), r.rows), "text/csv") }) { Text("CSV") }
                    OutlinedButton({ shareFile(ctx, writePdf(ctx, r.name.replace(' ', '-').lowercase(), r.name + "  " + LocalDate.now(), r.rows.map { it.joinToString(" | ") }), "application/pdf") }) { Text("PDF") } } } }
        }
    }
}

@Composable
fun SettingsScreen(vm: FarmViewModel, onBack: () -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val m = ui.membership!!; val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    val track = remember(d.settings) { mutableStateMapOf<String, Boolean>().apply { listOf("goat", "sheep", "buffalo").forEach { put(it, d.settings?.weightTracking?.get(it) ?: (it != "buffalo")) } } }
    val rates = remember(d.settings) { mutableStateMapOf<String, String>().apply { listOf("goat", "sheep", "buffalo").forEach { put(it, d.defaultRate(it)?.toString() ?: "") } } }
    var farm by remember(d.farm) { mutableStateOf(d.farm?.name.orEmpty()) }
    var days by remember(d.settings) { mutableStateOf(d.settings?.reminderDays?.joinToString(",") ?: "7,3,1") }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { msg = if (it) "Notifications allowed." else "Notifications were not allowed." }
    fun saved(e: String?) { msg = e ?: "Saved." }
    AppScaffold("Settings", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 700.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            msg?.let { Text(it, color = if (it.startsWith("Saved") || it.endsWith("allowed.")) Success else Danger) }
            SectionTitle("Farm")
            Field("Farm name", farm, { farm = it }); if (m.isAdmin) Button({ if (farm.isBlank()) msg = "Farm name cannot be empty." else vm.run(::saved) { saveFarmName(farm.trim()) } }) { Text("Save name") }
            SectionTitle("Weight tracking by species")
            Text("Buffalo tracking is off by default. An individual animal can override this from its Edit screen.", style = MaterialTheme.typography.bodySmall)
            track.keys.sorted().forEach { k -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, androidx.compose.ui.Alignment.CenterVertically) { Text(cap(k)); Switch(track[k] == true, { track[k] = it }, enabled = m.isAdmin) } }
            if (m.isAdmin) Button({ vm.run(::saved) { saveWeightTracking(track.toMap()) } }) { Text("Save weight settings") }
            if (m.isAdmin) { SectionTitle("Default daily care rates")
                rates.keys.sorted().forEach { k -> Field("${cap(k)} (per day)", rates[k].orEmpty(), { rates[k] = it }, keyboard = KeyboardType.Decimal) }
                Button({ val parsed = rates.filterValues { it.isNotBlank() }.mapValues { it.value.toDoubleOrNull() }
                    if (parsed.values.any { it == null || it < 0 }) msg = "Rates must be numbers of 0 or more." else vm.run(::saved) { saveDefaultRates(d.settings?.billingRules, parsed.mapValues { it.value!! }) } }) { Text("Save rates") }
                SectionTitle("Health reminders")
                Field("Reminder days before due (comma separated)", days, { days = it })
                Button({ val p = days.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..90 }
                    if (p.isEmpty()) msg = "Enter numbers between 0 and 90." else vm.run(::saved) { saveReminderDays(p.distinct().sortedDescending()) } }) { Text("Save reminders") } }
            SectionTitle("Notifications")
            Text("Reminders run once a day in the background. Android battery optimisation, Doze mode, denied permission or device settings can delay or block them, so also check Health tasks regularly.", style = MaterialTheme.typography.bodySmall)
            if (Build.VERSION.SDK_INT >= 33) OutlinedButton({ notif.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            SectionTitle("Data")
            Text("Offline queue and synchronisation are not enabled in this build. Data is read from the server, so an internet connection is required.", style = MaterialTheme.typography.bodySmall)
            Text("Backups: use Supabase scheduled backups or point-in-time recovery (see README).", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StaffScreen(vm: FarmViewModel, onBack: () -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val me = ui.membership!!
    var add by remember { mutableStateOf(false) }; var edit by remember { mutableStateOf<Membership?>(null) }; var msg by remember { mutableStateOf<String?>(null) }
    AppScaffold("Staff and permissions", onBack, fab = { FloatingActionButton({ add = true }, containerColor = Forest, contentColor = androidx.compose.ui.graphics.Color.White) { Icon(Icons.Default.Add, "Add staff") } }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 700.dp)) {
            Text("Permissions are enforced by the database. Create the person's login in Supabase Auth first, then add their email here.", style = MaterialTheme.typography.bodySmall)
            msg?.let { Text(it, color = Danger) }
            d.members.forEach { mb -> Panel(Modifier.padding(top = 8.dp).clickable(enabled = mb.userId != me.userId) { edit = mb }) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(d.memberName(mb.userId) + if (mb.userId == me.userId) " (you)" else "", fontWeight = FontWeight.SemiBold); Chip(cap(mb.role)) }
                if (mb.role != "admin") Text("Payments: ${if (mb.canRecordPayments) "yes" else "no"}   Finance view: ${if (mb.canViewFinance) "yes" else "no"}", style = MaterialTheme.typography.bodySmall) } }
        }
    }
    if (add || edit != null) {
        val e = edit; var email by remember(e) { mutableStateOf(e?.let { d.memberName(it.userId) }.orEmpty()) }; var role by remember(e) { mutableStateOf(e?.role ?: "staff") }
        var pay by remember(e) { mutableStateOf(e?.canRecordPayments ?: false) }; var fin by remember(e) { mutableStateOf(e?.canViewFinance ?: false) }
        var err by remember(e) { mutableStateOf<String?>(null) }; var busy by remember(e) { mutableStateOf(false) }; var rm by remember(e) { mutableStateOf(false) }
        FormDialog(if (e == null) "Add staff" else "Edit access", "Save", { add = false; edit = null }, err, busy, {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) err = "Enter the person's login email." else { busy = true
                vm.run({ er -> busy = false; err = er; if (er == null) { add = false; edit = null } }) { addMember(email, role, pay, fin) } } }) {
            Field("Email", email, { email = it }, keyboard = KeyboardType.Email)
            DropdownField("Role", listOf("admin" to "Farm administrator", "staff" to "Farm staff", "viewer" to "Read-only viewer"), role, { role = it })
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(pay, { pay = it }); Text("Can record payments (staff)") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(fin, { fin = it }); Text("Can view finance") }
            if (e != null) TextButton({ rm = true }) { Text("Remove from farm", color = Danger) }
        }
        if (rm && e != null) ConfirmDialog("Remove access?", "They will no longer be able to open FarmCare data. Records they created stay.", "Remove",
            { rm = false; vm.run({ er -> msg = er; edit = null }) { removeMember(e.userId) } }, { rm = false })
    }
}

@Composable
fun AuditScreen(vm: FarmViewModel, onBack: () -> Unit) {
    val d = vm.s.collectAsState().value.d
    AppScaffold("Audit history", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 800.dp)) {
            Text("Latest ${d.logs.size} changes. Entries cannot be edited or deleted.", style = MaterialTheme.typography.bodySmall)
            if (d.logs.isEmpty()) EmptyNote("No activity yet.")
            d.logs.forEach { l -> Column(Modifier.padding(vertical = 6.dp)) { Text("${l.action} ${l.table.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                Text("${l.at.take(19).replace('T', ' ')}  by ${d.memberName(l.actor)}", style = MaterialTheme.typography.bodySmall) }; HorizontalDivider() }
        }
    }
}
