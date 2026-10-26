package com.farmcare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farmcare.app.AuthViewModel
import com.farmcare.app.BuildConfig
import com.farmcare.app.ui.theme.*

@Composable
fun LoginScreen(vm: AuthViewModel) {
    var email by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("login") }   // login | reset | create
    val busy by vm.busy.collectAsState(); val msg by vm.message.collectAsState()
    val emailOk = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("FarmCare", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Forest)
            Text(when (mode) { "reset" -> "Reset your password"; "create" -> "Create the administrator account"; else -> "Sign in to manage your farm" },
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (BuildConfig.SUPABASE_URL.isBlank()) Panel { Text("Backend not configured. Add supabase.url and supabase.anonKey to local.properties and rebuild.", color = Danger) }
            Field("Email", email, { email = it }, keyboard = KeyboardType.Email, error = if (email.isNotEmpty() && !emailOk) "Enter a valid email" else null)
            if (mode != "reset") Field("Password", pw, { pw = it }, password = true, error = if (mode == "create" && pw.isNotEmpty() && pw.length < 8) "Use at least 8 characters" else null)
            msg?.let { Text(it, color = if (it.startsWith("Account created") || it.startsWith("If that")) Success else Danger) }
            Button({ when (mode) { "login" -> vm.signIn(email, pw); "reset" -> vm.reset(email); else -> vm.signUp(email, pw) } },
                enabled = !busy && emailOk && (mode == "reset" || (pw.length >= if (mode == "create") 8 else 1)),
                modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(when (mode) { "reset" -> "Send reset link"; "create" -> "Create account"; else -> "Sign in" })
            }
            if (mode == "login") { TextButton({ mode = "reset"; vm.message.value = null }) { Text("Forgot password?") }
                TextButton({ mode = "create"; vm.message.value = null }) { Text("First time setup: create administrator") } }
            else TextButton({ mode = "login"; vm.message.value = null }) { Text("Back to sign in") }
        }
    }
}

@Composable
fun SetupScreen(onCreate: (String, (String?) -> Unit) -> Unit, onSignOut: () -> Unit) {
    var name by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Set up your farm", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Forest)
            Text("You will become the Farm Administrator. If your farm already exists, ask the administrator to add your email as staff, then sign in again.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field("Farm name", name, { name = it })
            err?.let { Text(it, color = Danger) }
            Button({ busy = true; onCreate(name.trim()) { e -> busy = false; err = e } }, enabled = name.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Create farm") }
            TextButton(onSignOut) { Text("Sign out") }
        }
    }
}
