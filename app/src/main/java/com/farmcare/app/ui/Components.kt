package com.farmcare.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farmcare.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(title: String, onBack: (() -> Unit)? = null, tab: Boolean = false,
                actions: @Composable RowScope.() -> Unit = {}, fab: @Composable () -> Unit = {},
                content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Forest, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White))
        },
        floatingActionButton = fab,
        contentWindowInsets = if (tab) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        content = content)
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Forest, onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(m, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = Color.White)) {
    Column(Modifier.padding(14.dp), content = content)
}

@Composable fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
    color = Forest, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))

@Composable fun EmptyNote(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))

@Composable fun Loading() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Forest) }

@Composable fun ErrorState(msg: String, onRetry: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
    Text(msg, color = Danger); Spacer(Modifier.height(12.dp)); Button(onRetry) { Text("Retry") }
}

enum class Tone { Good, Warn, Bad, Neutral }
@Composable fun Chip(text: String, tone: Tone = Tone.Neutral) {
    val c = when (tone) { Tone.Good -> Success; Tone.Warn -> Warning; Tone.Bad -> Danger; Tone.Neutral -> MaterialTheme.colorScheme.secondary }
    Surface(color = c.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(text, color = c, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
          keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, error: String? = null, password: Boolean = false) {
    OutlinedTextField(value, onChange, modifier = modifier, label = { Text(label) }, singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        isError = error != null, supportingText = error?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(options.find { it.first == selected }?.second ?: "", {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem({ Text(l) }, { onSelect(v); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), clearable: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(value, {}, readOnly = true, label = { Text(label) }, modifier = modifier,
        trailingIcon = { Row { if (clearable && value.isNotEmpty()) TextButton({ onChange("") }) { Text("Clear") }
            IconButton({ show = true }) { Icon(Icons.Default.DateRange, "Pick date") } } })
    if (show) {
        val st = rememberDatePickerState(initialSelectedDateMillis = runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull())
        DatePickerDialog(onDismissRequest = { show = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; show = false }) { Text("OK") } },
            dismissButton = { TextButton({ show = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Confirm", onYes: () -> Unit, onNo: () -> Unit) = AlertDialog(
    onDismissRequest = onNo, title = { Text(title) }, text = { Text(text) },
    confirmButton = { TextButton(onYes) { Text(confirm, color = Forest, fontWeight = FontWeight.Bold) } },
    dismissButton = { TextButton(onNo) { Text("Cancel") } })

/** Form dialog with a scrollable body, an inline error line, and a busy state. */
@Composable
fun FormDialog(title: String, confirm: String, onDismiss: () -> Unit, error: String?, busy: Boolean, onSubmit: () -> Unit, body: @Composable ColumnScope.() -> Unit) =
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { body(); if (error != null) Text(error, color = Danger, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { Button(onSubmit, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text(confirm) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancel") } })


@Composable
fun BarChart(items: List<Pair<String, Double>>, format: (Double) -> String = { "%.0f".format(it) }) {
    val max = (items.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(170.dp), Arrangement.spacedBy(10.dp), Alignment.Bottom) {
        items.forEach { (l, v) ->
            Column(Modifier.weight(1f).fillMaxHeight(), Arrangement.Bottom, Alignment.CenterHorizontally) {
                Text(format(v), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().fillMaxHeight(((v / max) * 0.68).toFloat().coerceAtLeast(0.01f)).background(Forest, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                Spacer(Modifier.height(4.dp))
                Text(l, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** Needs at least two real points; the caller must not render it otherwise. */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier) {
    val color = Forest
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val mn = values.min(); val mx = values.max(); val range = (mx - mn).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * stepX, size.height - (((v - mn) / range).toFloat() * size.height * 0.8f + size.height * 0.1f)) }
        for (i in 0 until pts.size - 1) drawLine(color, pts[i], pts[i + 1], strokeWidth = 5f)
        pts.forEach { drawCircle(color, 8f, it) }
    }
}
