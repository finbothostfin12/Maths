@file:OptIn(ExperimentalMaterial3Api::class)

package app.mathlab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mathlab.data.AppState
import app.mathlab.math.*
import kotlin.math.*

class GraphState {
    val fns = mutableStateListOf("sin(x)", "x^2/4")
    var cx by mutableDoubleStateOf(0.0)
    var cy by mutableDoubleStateOf(0.0)
    var scale by mutableDoubleStateOf(50.0)
    var trace by mutableStateOf<Double?>(null)
}

private fun compile(s: String): Node? {
    val t = s.replace(" ", "").lowercase().removePrefix("y=").removePrefix("f(x)=")
    if (t.isBlank()) return null
    return try { Calc.parse(t) } catch (e: Exception) { null }
}

private fun niceStep(raw: Double): Double {
    val base = 10.0.pow(floor(log10(raw)))
    val m = raw / base
    return base * when { m < 1.5 -> 1.0; m < 3.5 -> 2.0; m < 7.5 -> 5.0; else -> 10.0 }
}

private fun evalAt(node: Node, env: Env, x: Double): Double {
    env.vars["x"] = x
    return try { Ev.eval(node, env) } catch (e: Exception) { Double.NaN }
}

private fun analyze(nodes: List<Node?>, env: Env, x0: Double, x1: Double): List<String> {
    val out = mutableListOf<String>()
    val n = 3000
    nodes.forEachIndexed { idx, node ->
        if (node == null) return@forEachIndexed
        val label = "f${idx + 1}"
        val y0 = evalAt(node, env, 0.0)
        if (y0.isFinite()) out.add("$label: y-intercept (0, ${fmt(Ev.clean(y0), 6)})")
        var found = 0
        var px = x0; var py = evalAt(node, env, px)
        var pdy = Double.NaN; var ppx = px
        for (i in 1..n) {
            val x = x0 + (x1 - x0) * i / n
            val y = evalAt(node, env, x)
            if (found < 14 && py.isFinite() && y.isFinite()) {
                if (py * y < 0) {
                    var a = px; var b = x; var fa = py
                    repeat(50) { val m = (a + b) / 2; val fm = evalAt(node, env, m); if (fa * fm <= 0) b = m else { a = m; fa = fm } }
                    val r = (a + b) / 2
                    if (abs(evalAt(node, env, r)) < 1e-6 * max(1.0, abs(py) + abs(y))) { out.add("$label: root x = ${fmt(Ev.clean(r), 6)}"); found++ }
                }
                val dy = y - py
                if (pdy.isFinite() && pdy * dy < 0) {
                    val isMin = pdy < 0
                    var lo = ppx; var hi = x
                    repeat(60) {
                        val m1 = lo + (hi - lo) / 3; val m2 = hi - (hi - lo) / 3
                        val f1 = evalAt(node, env, m1) * (if (isMin) 1 else -1)
                        val f2 = evalAt(node, env, m2) * (if (isMin) 1 else -1)
                        if (f1 < f2) hi = m2 else lo = m1
                    }
                    val xm = (lo + hi) / 2
                    val ym = evalAt(node, env, xm)
                    if (ym.isFinite()) { out.add("$label: ${if (isMin) "minimum" else "maximum"} (${fmt(Ev.clean(xm), 6)}, ${fmt(Ev.clean(ym), 6)})"); found++ }
                }
                pdy = dy
            } else pdy = Double.NaN
            ppx = px; px = x; py = y
        }
    }
    for (i in nodes.indices) for (j in i + 1 until nodes.size) {
        val a = nodes[i] ?: continue
        val b = nodes[j] ?: continue
        var px = x0
        var pd = evalAt(a, env, px) - evalAt(b, env, px)
        var cnt = 0
        for (k in 1..n) {
            val x = x0 + (x1 - x0) * k / n
            val d = evalAt(a, env, x) - evalAt(b, env, x)
            if (cnt < 8 && pd.isFinite() && d.isFinite() && pd * d < 0) {
                var lo = px; var hi = x; var fl = pd
                repeat(50) { val m = (lo + hi) / 2; val fm = evalAt(a, env, m) - evalAt(b, env, m); if (fl * fm <= 0) hi = m else { lo = m; fl = fm } }
                val r = (lo + hi) / 2
                val yy = evalAt(a, env, r)
                if (abs(evalAt(a, env, r) - evalAt(b, env, r)) < 1e-6 * max(1.0, abs(yy))) {
                    out.add("f${i + 1} ∩ f${j + 1}: (${fmt(Ev.clean(r), 6)}, ${fmt(Ev.clean(yy), 6)})"); cnt++
                }
            }
            px = x; pd = d
        }
    }
    return out
}

@Composable
fun GraphScreen(app: AppState, g: GraphState) {
    val cs = MaterialTheme.colorScheme
    val colors = listOf(cs.primary, cs.tertiary, cs.secondary, cs.error, Color(0xFF2E9E5B), Color(0xFFE59A12))
    val nodes = remember(g.fns.toList()) { g.fns.map { compile(it) } }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = cs.onSurfaceVariant)
    val gridColor = cs.outlineVariant.copy(alpha = 0.5f)
    val axisColor = cs.onSurfaceVariant
    var analysis by remember { mutableStateOf<List<String>?>(null) }
    var viewSize by remember { mutableStateOf(Pair(1f, 1f)) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val w = size.width; val h = size.height
                            val wx = g.cx + (centroid.x - w / 2) / g.scale
                            val wy = g.cy - (centroid.y - h / 2) / g.scale
                            val ns = (g.scale * zoom).coerceIn(2.0, 200000.0)
                            g.cx = wx - (centroid.x + pan.x - w / 2) / ns
                            g.cy = wy + (centroid.y + pan.y - h / 2) / ns
                            g.scale = ns
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { off -> g.trace = g.cx + (off.x - size.width / 2) / g.scale })
                    }
            ) {
                val w = size.width; val h = size.height
                viewSize = Pair(w, h)
                val sc = g.scale
                fun sx(x: Double) = (w / 2 + (x - g.cx) * sc).toFloat()
                fun sy(y: Double) = (h / 2 - (y - g.cy) * sc).toFloat()
                val step = niceStep(80.0 / sc)
                val xa = g.cx - w / 2 / sc; val xb = g.cx + w / 2 / sc
                val ya = g.cy - h / 2 / sc; val yb = g.cy + h / 2 / sc
                val axX = sx(0.0).coerceIn(0f, w)
                val axY = sy(0.0).coerceIn(0f, h)
                for (k in ceil(xa / step).toInt()..floor(xb / step).toInt()) {
                    val gx = k * step
                    drawLine(gridColor, Offset(sx(gx), 0f), Offset(sx(gx), h), 1f)
                    if (k != 0) drawText(measurer, fmt(gx, 6), Offset(sx(gx) + 3f, (axY + 4f).coerceIn(2f, h - 18f)), labelStyle)
                }
                for (k in ceil(ya / step).toInt()..floor(yb / step).toInt()) {
                    val gy = k * step
                    drawLine(gridColor, Offset(0f, sy(gy)), Offset(w, sy(gy)), 1f)
                    if (k != 0) drawText(measurer, fmt(gy, 6), Offset((axX + 5f).coerceIn(2f, w - 44f), sy(gy) + 2f), labelStyle)
                }
                drawLine(axisColor, Offset(0f, sy(0.0)), Offset(w, sy(0.0)), 2f)
                drawLine(axisColor, Offset(sx(0.0), 0f), Offset(sx(0.0), h), 2f)

                val env = Env(app.angle)
                nodes.forEachIndexed { i, node ->
                    if (node == null) return@forEachIndexed
                    val path = Path()
                    var pen = false
                    var prev = 0f
                    var px = 0f
                    while (px <= w) {
                        val x = g.cx + (px - w / 2) / sc
                        val y = evalAt(node, env, x)
                        if (y.isNaN() || y.isInfinite()) {
                            pen = false
                        } else {
                            val yy = (h / 2.0 - (y - g.cy) * sc).toFloat().coerceIn(-1e5f, 1e5f)
                            val jump = pen && ((prev < 0f && yy > h) || (prev > h && yy < 0f))
                            if (!pen || jump) path.moveTo(px, yy) else path.lineTo(px, yy)
                            pen = true; prev = yy
                        }
                        px += 1.5f
                    }
                    drawPath(path, colors[i % colors.size], style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                g.trace?.let { tx ->
                    drawLine(cs.outline, Offset(sx(tx), 0f), Offset(sx(tx), h), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    nodes.forEachIndexed { i, node ->
                        if (node == null) return@forEachIndexed
                        val y = evalAt(node, env, tx)
                        if (y.isFinite()) drawCircle(colors[i % colors.size], 5.dp.toPx(), Offset(sx(tx), sy(y)))
                    }
                }
            }

            g.trace?.let { tx ->
                val env = Env(app.angle)
                Card(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("x = ${fmt(Ev.clean(tx), 6)}", style = MaterialTheme.typography.labelLarge)
                        nodes.forEachIndexed { i, node ->
                            if (node != null) Text("f${i + 1} = ${fmt(Ev.clean(evalAt(node, env, tx)), 6)}", style = MaterialTheme.typography.bodySmall, color = colors[i % colors.size])
                        }
                    }
                }
            }

            Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(onClick = { g.scale = (g.scale * 1.4).coerceAtMost(200000.0) }) { Icon(Icons.Default.ZoomIn, "Zoom in") }
                FilledTonalIconButton(onClick = { g.scale = (g.scale / 1.4).coerceAtLeast(2.0) }) { Icon(Icons.Default.ZoomOut, "Zoom out") }
                FilledTonalIconButton(onClick = { g.cx = 0.0; g.cy = 0.0; g.scale = 50.0; g.trace = null }) { Icon(Icons.Default.FilterCenterFocus, "Reset view") }
                FilledIconButton(onClick = {
                    val (w, _) = viewSize
                    analysis = analyze(nodes, Env(app.angle), g.cx - w / 2 / g.scale, g.cx + w / 2 / g.scale)
                }) { Icon(Icons.Default.Search, "Roots, extrema, intersections") }
            }
        }

        Column(
            Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = app.angle == AngleMode.DEG, onClick = { app.setAngle(AngleMode.DEG) }, label = { Text("DEG") })
                FilterChip(selected = app.angle == AngleMode.RAD, onClick = { app.setAngle(AngleMode.RAD) }, label = { Text("RAD") })
                Spacer(Modifier.weight(1f))
                if (g.fns.size < 6) TextButton(onClick = { g.fns.add("") }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Function") }
            }
            g.fns.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(colors[i % colors.size]))
                    OutlinedTextField(
                        value = s, onValueChange = { g.fns[i] = it }, singleLine = true,
                        label = { Text("f${i + 1}(x)") }, isError = s.isNotBlank() && nodes.getOrNull(i) == null,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { if (g.fns.size > 1) g.fns.removeAt(i) else g.fns[0] = "" }) { Icon(Icons.Default.Close, "Remove") }
                }
            }
            Text("Drag to pan, pinch to zoom, tap to trace. Search icon: roots, extrema & intersections in view.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }

    analysis?.let { list ->
        AlertDialog(
            onDismissRequest = { analysis = null },
            title = { Text("In the visible area") },
            text = {
                if (list.isEmpty()) Text("Nothing found. Zoom or pan to a different area.")
                else LazyColumn { items(list) { Text(it, Modifier.padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium) } }
            },
            confirmButton = { TextButton(onClick = { analysis = null }) { Text("Close") } }
        )
    }
}
