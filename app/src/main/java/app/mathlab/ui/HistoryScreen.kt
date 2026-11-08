@file:OptIn(ExperimentalMaterial3Api::class)

package app.mathlab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mathlab.data.AppState
import app.mathlab.data.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val headerFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private fun stamp(t: Long): String {
    val z = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
    return z.format(dateFmt) + " · " + z.format(timeFmt)
}

private fun dayOf(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun HistoryScreen(app: AppState, onReuse: (String) -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (app.history.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all") }
            }
        }
        if (app.history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing calculated yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val grouped = app.history.groupBy { dayOf(it.time) }
            val today = LocalDate.now()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (day, list) ->
                    item(key = "h$day") {
                        Text(
                            when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> day.format(headerFmt) },
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }
                    items(list, key = { it.id }) { e -> HistoryCard(e, onReuse, { app.removeHistory(e.id) }) }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All saved calculations will be removed.") },
            confirmButton = { TextButton(onClick = { app.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HistoryCard(e: HistoryEntry, onReuse: (String) -> Unit, onDelete: () -> Unit) {
    ElevatedCard(onClick = { onReuse(e.expr) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.expr, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                Text("= ${e.result}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e.kind == "scan") {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Calculated " + stamp(e.time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}
