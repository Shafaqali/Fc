package com.farmcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

private val speciesOptions = listOf("goat" to "Goat", "buffalo" to "Buffalo", "sheep" to "Sheep")
fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/** One-line weight text that never shows 0 kg and never invents a value. */
fun weightLine(d: FarmData, a: Animal): String? {
    if (!d.trackingEnabled(a)) return null
    return d.weightsOf(a.id).firstOrNull()?.let { "%.1f kg".format(it.kg) } ?: "Weight not recorded"
}

@Composable
fun AnimalListScreen(vm: FarmViewModel, species0: String, status0: String, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val m = ui.membership!!
    var q by remember { mutableStateOf("") }; var sp by remember(species0) { mutableStateOf(species0) }; var st by remember(status0) { mutableStateOf(status0) }
    val list = d.animals.filter { a ->
        (sp.isEmpty() || a.species == sp) && (st.isEmpty() || a.status == st) &&
        (q.isBlank() || a.code.contains(q, true) || a.name?.contains(q, true) == true || d.customer(a.customerId)?.name?.contains(q, true) == true)
    }
    AppScaffold("Animals", tab = true, fab = { if (m.canWrite) ExtendedFloatingActionButton(onClick = { onNew() }, containerColor = Forest, contentColor = androidx.compose.ui.graphics.Color.White,
        icon = { Icon(Icons.Default.Add, null) }, text = { Text("Register") }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, placeholder = { Text("Search ID, name or owner") },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sp.isEmpty(), { sp = "" }, { Text("All") })
                speciesOptions.forEach { (k, l) -> FilterChip(sp == k, { sp = k }, { Text(l) }) }
                Spacer(Modifier.width(8.dp))
                listOf("active" to "Active", "returned" to "Returned", "archived" to "Archived", "" to "Any status").forEach { (k, l) -> FilterChip(st == k, { st = k }, { Text(l) }) }
            }
            Text("${list.size} animals", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
            when {
                ui.loading && d.animals.isEmpty() -> Loading()
                list.isEmpty() -> EmptyNote(if (d.animals.isEmpty()) "No animals registered yet." else "No animals match these filters.")
                else -> LazyVerticalGrid(GridCells.Adaptive(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(list, key = { it.id }) { a -> AnimalRow(d, a) { onOpen(a.id) } }
                }
            }
        }
    }
}

private fun Modifier.horizontalScroll(rememberScrollState()) = this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))
private fun Modifier.horizontalScroll(s: androidx.compose.foundation.ScrollState): Modifier = androidx.compose.foundation.horizontalScroll(this, s)

@Composable
private fun AnimalRow(d: FarmData, a: Animal, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(a.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Forest)
            Chip(cap(a.status), when (a.status) { "active" -> Tone.Good; "returned" -> Tone.Warn; else -> Tone.Neutral })
        }
        Text("${cap(a.species)}${a.name?.let { " - $it" } ?: ""}")
        Text("Owner: ${d.customer(a.customerId)?.name ?: "-"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        weightLine(d, a)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

// ---------------------------------------------------------------- register
@Composable
fun RegisterAnimalScreen(vm: FarmViewModel, onBack: () -> Unit, onDone: (String) -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d
    var species by remember { mutableStateOf("goat") }
    var customerId by remember { mutableStateOf<String?>(null) }; var newCustomer by remember { mutableStateOf(false) }
    var cName by remember { mutableStateOf("") }; var cPhone by remember { mutableStateOf("") }; var cAddr by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }; var breed by remember { mutableStateOf("") }; var gender by remember { mutableStateOf<String?>(null) }
    var dob by remember { mutableStateOf("") }; var age by remember { mutableStateOf("") }
    var arrival by remember { mutableStateOf(LocalDate.now().toString()) }
    var weight by remember { mutableStateOf("") }; var wDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var rate by remember { mutableStateOf("") }; var rateTouched by remember { mutableStateOf(false) }
    var idNotes by remember { mutableStateOf("") }; var healthNotes by remember { mutableStateOf("") }; var remarks by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var newId by remember { mutableStateOf<String?>(null) }
    val tracking = d.settings?.weightTracking?.get(species) ?: (species != "buffalo")
    LaunchedEffect(species) { if (!rateTouched) rate = d.defaultRate(species)?.let { "%.2f".format(it) } ?: "" }
    val dupe = newCustomer && cPhone.isNotBlank() && d.customers.any { it.phone.filter(Char::isDigit) == cPhone.filter(Char::isDigit) }

    AppScaffold("Register animal", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("The animal ID (for example G001) is assigned by the server when you save.", style = MaterialTheme.typography.bodySmall)
            DropdownField("Species", speciesOptions, species, { species = it })
            SectionTitle("Owner")
            if (!newCustomer) {
                DropdownField("Customer", d.customers.filter { it.status == "active" }.map { it.id to "${it.name} (${it.code})" }, customerId, { customerId = it })
                TextButton({ newCustomer = true; customerId = null }) { Text("Add a new customer instead") }
            } else {
                Field("Customer name", cName, { cName = it }); Field("Phone", cPhone, { cPhone = it }, keyboard = KeyboardType.Phone,
                    error = if (dupe) "A customer with this phone number already exists. Select them instead." else null)
                Field("Village or address", cAddr, { cAddr = it })
                TextButton({ newCustomer = false }) { Text("Choose an existing customer") }
            }
            SectionTitle("Animal")
            Field("Name (optional)", name, { name = it }); Field("Breed (optional)", breed, { breed = it })
            DropdownField("Gender (optional)", listOf("male" to "Male", "female" to "Female"), gender, { gender = it })
            DateField("Date of birth (optional)", dob, { dob = it }, clearable = true)
            Field("Or estimated age in months (optional)", age, { age = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number)
            DateField("Arrival / care start date", arrival, { arrival = it })
            if (tracking) { Field(if (species == "buffalo") "Initial weight kg (optional)" else "Initial weight kg", weight, { weight = it }, keyboard = KeyboardType.Decimal)
                DateField("Weight measurement date", wDate, { wDate = it }) }
            else if (species == "buffalo") Text("Weight tracking is not enabled for buffaloes. An administrator can enable it in Settings.", style = MaterialTheme.typography.bodySmall)
            SectionTitle("Care rate")
            Field("Daily rate (optional)", rate, { rate = it; rateTouched = true }, keyboard = KeyboardType.Decimal)
            Field("Identification notes", idNotes, { idNotes = it }, singleLine = false); Field("Health notes", healthNotes, { healthNotes = it }, singleLine = false)
            Field("Remarks", remarks, { remarks = it }, singleLine = false)
            error?.let { Text(it, color = Danger) }
            Button({
                error = null
                val w = weight.toDoubleOrNull(); val r = rate.toDoubleOrNull()
                when {
                    newCustomer && (cName.isBlank() || cPhone.isBlank()) -> error = "Enter the new customer's name and phone."
                    !newCustomer && customerId == null -> error = "Select the owner."
                    dupe -> error = "Duplicate customer phone number."
                    weight.isNotBlank() && (w == null || w <= 0) -> error = "Weight must be a number above 0, or leave it empty."
                    rate.isNotBlank() && (r == null || r < 0) -> error = "Enter a valid rate."
                    else -> {
                        busy = true
                        vm.run({ e -> busy = false; error = e }) {
                            val cid = customerId ?: createCustomer(customerDraft(cName.trim(), cPhone.trim(), null, cAddr, null)).id
                            val a = registerAnimal(FarmRepository.Registration(species, cid, name.ifBlank { null }, breed.ifBlank { null }, gender,
                                dob.ifBlank { null }, age.toIntOrNull(), arrival, idNotes.ifBlank { null }, healthNotes.ifBlank { null }, remarks.ifBlank { null },
                                if (tracking) w else null, wDate, r))
                            newId = a.id
                        }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp) else Text("Register animal") }
        }
    }
    LaunchedEffect(busy, error) { if (!busy && error == null && newId != null) { val id = newId!!; newId = null; onDone(id) } }
}
