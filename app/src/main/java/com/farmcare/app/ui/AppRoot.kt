package com.farmcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.farmcare.app.AuthViewModel
import com.farmcare.app.FarmViewModel
import io.github.jan.supabase.auth.status.SessionStatus

private data class NavTab(val route: String, val label: String, val icon: ImageVector, val key: String)
private val tabs = listOf(
    NavTab("home", "Home", Icons.Default.Home, "home"),
    NavTab("animals?species=&status=active", "Animals", Icons.Default.Pets, "animals"),
    NavTab("scan", "Scan QR", Icons.Default.QrCodeScanner, "scan"),
    NavTab("customers", "Customers", Icons.Default.People, "customers"),
    NavTab("more", "More", Icons.Default.MoreHoriz, "more"))

@Composable
fun FarmCareRoot() {
    val auth: AuthViewModel = hiltViewModel(); val vm: FarmViewModel = hiltViewModel()
    val status by auth.status.collectAsState(); val ui by vm.s.collectAsState()
    // A failed token refresh while offline must not log the user out; saved data stays viewable.
    val signedIn = status is SessionStatus.Authenticated || status.javaClass.simpleName == "RefreshFailure"
    LaunchedEffect(status) { if (signedIn) vm.load() else if (status is SessionStatus.NotAuthenticated) vm.clear() }
    when {
        status is SessionStatus.Initializing -> Loading()
        !signedIn -> LoginScreen(auth)
        ui.needsSetup -> SetupScreen({ n, cb -> vm.setup(n, cb) }, { auth.signOut() })
        ui.loading && ui.membership == null -> Loading()
        ui.membership == null -> ErrorState(ui.error ?: "Could not load your farm.") { vm.load() }
        else -> MainNav(vm, auth)
    }
}

@Composable
private fun MainNav(vm: FarmViewModel, auth: AuthViewModel) {
    val nav = rememberNavController()
    val ui by vm.s.collectAsState(); val pending by vm.pending.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val activeTab = tabs.find { route.startsWith(it.key) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    fun goTab(t: NavTab) = nav.navigate(t.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    val open: (String) -> Unit = { nav.navigate("animal/$it") }
    val back: () -> Unit = { nav.popBackStack() }

    Row(Modifier.fillMaxSize()) {
        if (wide && activeTab != null) NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            Spacer(Modifier.weight(1f))
            tabs.forEach { t -> NavigationRailItem(activeTab == t, { goTab(t) }, { Icon(t.icon, t.label) }, label = { Text(t.label) }) }
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            NavHost(nav, "home", Modifier.weight(1f)) {
                composable("home") { DashboardScreen(vm, onNav = { nav.navigate(it) }, onOpenAnimal = open) }
                composable("animals?species={species}&status={status}",
                    listOf(navArgument("species") { defaultValue = ""; type = NavType.StringType }, navArgument("status") { defaultValue = "active"; type = NavType.StringType })) {
                    AnimalListScreen(vm, it.arguments?.getString("species").orEmpty(), it.arguments?.getString("status").orEmpty(), open) { nav.navigate("animal/new") }
                }
                composable("scan") { ScanScreen(vm, open) }
                composable("customers") { CustomerListScreen(vm) { nav.navigate("customer/$it") } }
                composable("more") { MoreScreen(vm, auth) { nav.navigate(it) } }
                composable("animal/new") { RegisterAnimalScreen(vm, back) { id -> nav.popBackStack(); nav.navigate("animal/$id") } }
                composable("animal/{id}") { AnimalDetailScreen(vm, it.arguments?.getString("id").orEmpty(), back, { c -> nav.navigate("customer/$c") }) }
                composable("customer/{id}") { CustomerDetailScreen(vm, it.arguments?.getString("id").orEmpty(), back, open) }
                composable("health") { HealthTasksScreen(vm, back, open) }
                composable("billing") { BillingScreen(vm, back) { c -> nav.navigate("customer/$c") } }
                composable("reports") { ReportsScreen(vm, back) }
                composable("settings") { SettingsScreen(vm, back) }
                composable("staff") { StaffScreen(vm, back) }
                composable("sync") { SyncScreen(vm, back) }
                composable("audit") { AuditScreen(vm, back) }
            }
            val unsent = pending.count { it.status != "synced" }
            if (activeTab != null && (ui.offline || unsent > 0)) Surface(color = androidx.compose.ui.graphics.Color(0xFFFFF1D6), modifier = Modifier.fillMaxWidth().clickable { nav.navigate("sync") }) {
                Text((if (ui.offline) "Offline: showing saved data. " else "") + (if (unsent > 0) "$unsent change(s) not uploaded yet. " else "") + "Tap for details.",
                    style = MaterialTheme.typography.labelMedium, color = com.farmcare.app.ui.theme.Warning, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            if (!wide && activeTab != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { t -> NavigationBarItem(activeTab == t, { goTab(t) }, { Icon(t.icon, t.label) }, label = { Text(t.label) }) }
            }
        }
    }
}
