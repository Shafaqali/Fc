package com.farmcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmcare.app.FarmViewModel
import com.farmcare.app.domain.HealthRules
import com.farmcare.app.domain.VaccStatus
import com.farmcare.app.ui.theme.*
import com.farmcare.app.util.money
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(vm: FarmViewModel, onNav: (String) -> Unit, onOpenAnimal: (String) -> Unit) {
    val ui by vm.s.collectAsState(); val d = ui.d; val m = ui.membership!!
    val today = LocalDate.now()
    var range by remember { mutableStateOf("month") }
    val from: LocalDate? = when (range) { "month" -> today.withDayOfMonth(1); "30" -> today.minusDays(30); else -> null }
    val soonDays = d.settings?.reminderDays?.maxOrNull() ?: 7
    val pending = HealthRules.pending(d.vaccinations, today, soonDays)
    val overdue = pending.count { it.status == VaccStatus.OVERDUE }
    val soon = pending.count { it.status == VaccStatus.DUE_SOON || it.status == VaccStatus.DUE_TODAY }
    val followUps = d.treatments.count { t -> t.followUp?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(soonDays.toLong())) } == true }
    fun inRange(date: String) = from == null || runCatching { !LocalDate.parse(date).isBefore(from) }.getOrDefault(false)
    val charges = d.invoices.filter { it.status == "issued" && inRange(it.start) }.fold(BigDecimal.ZERO) { s, i -> s + BigDecimal(i.total.toString()) }
    val collected = d.payments.filter { inRange(it.paidOn) }.fold(BigDecimal.ZERO) { s, p -> s + BigDecimal(p.amount.toString()) }
    val outstanding = d.customers.fold(BigDecimal.ZERO) { s, c -> s + d.balance(c.id).max(BigDecimal.ZERO) }
    val active = d.animals.filter { it.status == "active" }
    val thisMonth = d.animals.count { it.registeredOnMonth() == YearMonth.from(today).toString() }
    val species = listOf("goat" to "Goats", "buffalo" to "Buffaloes", "sheep" to "Sheep")

    AppScaffold("FarmCare", tab = true, actions = {
        IconButton({ onNav("animals?species=&status=active") }) { Icon(Icons.Default.Search, "Search animals") }
        IconButton({ onNav("health") }) { BadgedBox({ if (overdue + soon > 0) Badge { Text("${overdue + soon}") } }) { Icon(Icons.Default.Notifications, "Health tasks") } }
    }) { pad ->
        PullBox(ui.loading, { vm.load() }, Modifier.padding(pad)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 1100.dp)) {
                ui.error?.let { Text(it, color = Danger, modifier = Modifier.padding(bottom = 8.dp)) }
                Text(d.farm?.name ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SectionTitle("Animals")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                    val w = Modifier.weight(1f).widthIn(min = 150.dp)
                    StatCard("Total animals", "${d.animals.size}", w) { onNav("animals?species=&status=") }
                    StatCard("Active in care", "${active.size}", w) { onNav("animals?species=&status=active") }
                    StatCard("Registered this month", "$thisMonth", w) { onNav("animals?species=&status=") }
                    species.forEach { (k, l) -> StatCard(l, "${d.animals.count { it.species == k }}", w) { onNav("animals?species=$k&status=") } }
                    StatCard("Customers", "${d.customers.size}", w) { onNav("customers") }
                }
                SectionTitle("Health")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                    val w = Modifier.weight(1f).widthIn(min = 150.dp)
                    StatCard("Vaccinations due soon", "$soon", w, Warning) { onNav("health") }
                    StatCard("Overdue vaccinations", "$overdue", w, if (overdue > 0) Danger else Forest) { onNav("health") }
                    StatCard("Follow-ups upcoming", "$followUps", w) { onNav("health") }
                }
                if (m.canFinance) {
                    SectionTitle("Finance")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("month" to "This month", "30" to "Last 30 days", "all" to "All time").forEach { (k, l) ->
                            FilterChip(range == k, { range = k }, { Text(l) }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                        val w = Modifier.weight(1f).widthIn(min = 150.dp)
                        StatCard("Care charges", money(charges), w) { onNav("billing") }
                        StatCard("Payments collected", money(collected), w, Success) { onNav("billing") }
                        StatCard("Outstanding (all time)", money(outstanding), w, if (outstanding.signum() > 0) Danger else Forest) { onNav("billing") }
                    }
                }
                SectionTitle("Charts")
                if (d.animals.isEmpty()) EmptyNote("Charts appear once animals are registered.") else Panel {
                    Text("Animal population by species", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
                    BarChart(species.map { (k, l) -> l to d.animals.count { it.species == k }.toDouble() })
                }
                if (m.canFinance && d.invoices.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Panel {
                        Text("Monthly care charges", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
                        val months = (5 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
                        BarChart(months.map { ym -> ym.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } to
                            d.invoices.filter { it.status == "issued" && it.start.startsWith(ym.toString()) }.sumOf { it.total } }) { "%.0f".format(it) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Panel {
                        Text("Payments received vs outstanding", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
                        BarChart(listOf("Received" to collected.toDouble(), "Outstanding" to outstanding.toDouble())) { "%.0f".format(it) }
                    }
                }
                val gs = d.animals.filter { it.species != "buffalo" && d.weightsOf(it.id).isNotEmpty() }
                if (gs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Panel {
                        Text("Recent weight measurements", fontWeight = FontWeight.SemiBold)
                        d.weights.take(5).forEach { w -> val a = d.animal(w.animalId)
                            Row(Modifier.fillMaxWidth().clickable { a?.let { onOpenAnimal(it.id) } }.padding(vertical = 6.dp), Arrangement.SpaceBetween) {
                                Text("${a?.code ?: "-"}  ${w.measuredOn}"); Text("%.1f kg".format(w.kg), fontWeight = FontWeight.SemiBold) } }
                    }
                }
                SectionTitle("Recent registrations")
                if (d.animals.isEmpty()) EmptyNote("No animals registered yet.") else Panel {
                    d.animals.sortedByDescending { it.createdAt }.take(5).forEach { a ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenAnimal(a.id) }.padding(vertical = 8.dp), Arrangement.SpaceBetween) {
                            Text("${a.code}  ${a.name ?: ""}", fontWeight = FontWeight.SemiBold); Text(d.customer(a.customerId)?.name ?: "") } }
                }
                if (m.canFinance) {
                    SectionTitle("Recent payments")
                    if (d.payments.isEmpty()) EmptyNote("No payments recorded yet.") else Panel {
                        d.payments.take(5).forEach { p -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween) {
                            Text("${d.customer(p.customerId)?.name ?: "-"}  ${p.paidOn}"); Text(money(p.amount), fontWeight = FontWeight.SemiBold, color = Success) } }
                    }
                }
                if (m.isAdmin) {
                    SectionTitle("Recent farm activity")
                    if (d.logs.isEmpty()) EmptyNote("No activity yet.") else Panel {
                        d.logs.take(6).forEach { l -> Text("${l.at.take(16).replace('T', ' ')}  ${l.action.lowercase()} ${l.table.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun com.farmcare.app.data.Animal.registeredOnMonth() = createdAt.take(7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullBox(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(refreshing, onRefresh, modifier) { content() }
