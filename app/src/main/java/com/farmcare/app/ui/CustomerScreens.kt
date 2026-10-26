package com.farmcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farmcare.app.FarmViewModel
import com.farmcare.app.data.*
import com.farmcare.app.ui.theme.*
import com.farmcare.app.util.*

@Composable
fun CustomerListScreen(vm: FarmViewModel, onOpen: (String) -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val m = ui.membership!!
    var q by remember { mutableStateOf("") }; var add by remember { mutableStateOf(false) }
    val list = d.customers.filter { q.isBlank() || it.name.contains(q, true) || it.phone.contains(q) || it.code.contains(q, true) }
    AppScaffold("Customers", tab = true, fab = { if (m.canWrite) ExtendedFloatingActionButton(onClick = { add = true }, containerColor = Forest, contentColor = Color.White,
        icon = { Icon(Icons.Default.Add, null) }, text = { Text("New customer") }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 10.dp), singleLine = true, placeholder = { Text("Search name, phone or code") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            if (ui.loading && d.customers.isEmpty()) Loading()
            else if (list.isEmpty()) EmptyNote(if (d.customers.isEmpty()) "No customers yet. Add the first one." else "No matching customers.")
            else LazyVerticalGrid(GridCells.Adaptive(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(list, key = { it.id }) { c ->
                    val mine = d.animals.filter { it.customerId == c.id && it.status == "active" }
                    Panel(Modifier.clickable { onOpen(c.id) }) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(c.name, fontWeight = FontWeight.Bold); Text(c.code, color = Forest) }
                        Text(c.phone, style = MaterialTheme.typography.bodySmall)
                        Text("${mine.size} active animals", style = MaterialTheme.typography.bodySmall)
                        if (m.canFinance) { val b = d.balance(c.id); if (b.signum() > 0) Text("Outstanding " + money(b), color = Danger, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
    if (add) CustomerForm(vm, null) { add = false }
}

@Composable
fun CustomerForm(vm: FarmViewModel, c: Customer?, onDismiss: () -> Unit) {
    val d = vm.s.collectAsState().value.d
    var name by remember { mutableStateOf(c?.name.orEmpty()) }; var phone by remember { mutableStateOf(c?.phone.orEmpty()) }
    var alt by remember { mutableStateOf(c?.altPhone.orEmpty()) }; var addr by remember { mutableStateOf(c?.address.orEmpty()) }; var notes by remember { mutableStateOf(c?.notes.orEmpty()) }
    var status by remember { mutableStateOf(c?.status ?: "active") }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    FormDialog(if (c == null) "New customer" else "Edit customer", "Save", onDismiss, err, busy, {
        val digits = phone.filter(Char::isDigit)
        when {
            name.isBlank() -> err = "Enter the customer's name."
            digits.length < 7 -> err = "Enter a valid phone number."
            d.customers.any { it.id != c?.id && it.phone.filter(Char::isDigit) == digits } -> err = "Another customer already uses this phone number."
            else -> { busy = true; vm.run({ e -> busy = false; err = e; if (e == null) onDismiss() }) {
                if (c == null) createCustomer(customerDraft(name.trim(), phone.trim(), alt, addr, notes)) else updateCustomer(c.id, name.trim(), phone.trim(), alt.ifBlank { null }, addr.ifBlank { null }, notes.ifBlank { null }, status) } }
        }
    }) {
        Field("Full name", name, { name = it }); Field("Phone", phone, { phone = it }, keyboard = KeyboardType.Phone); Field("Alternate number (optional)", alt, { alt = it }, keyboard = KeyboardType.Phone)
        Field("Village or address", addr, { addr = it }); Field("Notes", notes, { notes = it }, singleLine = false)
        if (c != null) DropdownField("Status", listOf("active" to "Active", "inactive" to "Inactive"), status, { status = it })
    }
}

@Composable
fun CustomerDetailScreen(vm: FarmViewModel, id: String, onBack: () -> Unit, onAnimal: (String) -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val m = ui.membership!!; val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = d.customer(id)
    if (c == null) { AppScaffold("Customer", onBack) { p -> Box(Modifier.padding(p).padding(24.dp)) { Text("Customer not found.") } }; return }
    var edit by remember { mutableStateOf(false) }; var pay by remember { mutableStateOf(false) }
    val owned = d.animals.filter { it.customerId == c.id }
    val historical = d.ownership.filter { it.customerId == c.id }.mapNotNull { d.animal(it.animalId)?.let { a -> a to it } }
    AppScaffold(c.name, onBack, actions = { if (m.canWrite) IconButton({ edit = true }) { Icon(Icons.Default.Edit, "Edit") } }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 900.dp)) {
            Panel { Text("${c.code}  |  ${cap(c.status)}", color = Forest, fontWeight = FontWeight.Bold); Text("Phone: ${c.phone}"); c.altPhone?.let { Text("Alternate: $it") }
                c.address?.let { Text("Address: $it") }; c.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
            SectionTitle("Animals")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("goat" to "Goats", "buffalo" to "Buffaloes", "sheep" to "Sheep").forEach { (k, l) -> StatCard(l, "${owned.count { it.species == k }}", Modifier.weight(1f)) }
                StatCard("Active", "${owned.count { it.status == "active" }}", Modifier.weight(1f)) }
            Spacer(Modifier.height(8.dp))
            if (owned.isEmpty()) EmptyNote("No animals currently registered to this customer.")
            owned.forEach { a -> Panel(Modifier.padding(bottom = 6.dp).clickable { onAnimal(a.id) }) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${a.code}  ${cap(a.species)}", fontWeight = FontWeight.SemiBold); Chip(cap(a.status), if (a.status == "active") Tone.Good else Tone.Warn) } } }
            val past = historical.filter { (a, _) -> a.customerId != c.id }
            if (past.isNotEmpty()) { SectionTitle("Previously owned (transferred away)"); past.forEach { (a, o) -> Text("${a.code}: ${o.from} to ${o.to ?: "-"}", Modifier.clickable { onAnimal(a.id) }.padding(vertical = 3.dp)) } }
            if (m.canFinance) {
                SectionTitle("Account")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Charges", money(d.charged(c.id)), Modifier.weight(1f)); StatCard("Paid", money(d.paid(c.id)), Modifier.weight(1f), Success)
                    StatCard("Balance", money(d.balance(c.id)), Modifier.weight(1f), if (d.balance(c.id).signum() > 0) Danger else Forest) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.canPay) Button({ pay = true }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Record payment") }
                    OutlinedButton({ val f = writePdf(ctx, "statement-${c.code}", "Statement - ${c.name}", statementLines(d, c)); shareFile(ctx, f, "application/pdf") }) { Text("Statement PDF") } }
                SectionTitle("Invoices")
                val inv = d.invoices.filter { it.customerId == c.id }
                if (inv.isEmpty()) EmptyNote("No invoices.")
                inv.forEach { i -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween) { Column { Text(i.no, fontWeight = FontWeight.SemiBold); Text("${i.start} to ${i.end}", style = MaterialTheme.typography.bodySmall) }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) { Text(money(i.total)); if (i.status != "issued") Chip(cap(i.status), Tone.Neutral) } }; HorizontalDivider() }
                SectionTitle("Payments")
                val pays = d.payments.filter { it.customerId == c.id }
                if (pays.isEmpty()) EmptyNote("No payments.")
                pays.forEach { p -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween) { Text("${p.paidOn}  ${cap(p.method.replace('_', ' '))}${p.reference?.let { " ($it)" } ?: ""}"); Text(money(p.amount), color = Success, fontWeight = FontWeight.SemiBold) }; HorizontalDivider() }
            }
        }
    }
    if (edit) CustomerForm(vm, c) { edit = false }
    if (pay) PaymentDialog(vm, c, c.id) { pay = false }
}

fun statementLines(d: FarmData, c: Customer): List<String> = buildList {
    add(d.farm?.name ?: "FarmCare"); add("Customer: ${c.name} (${c.code})  Phone: ${c.phone}"); add("Generated: ${java.time.LocalDate.now()}"); add("")
    add("INVOICES")
    d.invoices.filter { it.customerId == c.id && it.status == "issued" }.sortedBy { it.start }.forEach { i ->
        add("${i.no}  ${i.start} to ${i.end}  ${money(i.total, "Rs ")}")
        d.items.filter { it.invoiceId == i.id }.forEach { add("    ${it.description}  =  ${money(it.amount, "Rs ")}") } }
    add(""); add("PAYMENTS")
    d.payments.filter { it.customerId == c.id }.sortedBy { it.paidOn }.forEach { add("${it.paidOn}  ${it.method}  ${it.reference ?: ""}  ${money(it.amount, "Rs ")}") }
    add(""); add("Total charges : ${money(d.charged(c.id), "Rs ")}"); add("Total paid    : ${money(d.paid(c.id), "Rs ")}"); add("Balance due   : ${money(d.balance(c.id), "Rs ")}")
}
