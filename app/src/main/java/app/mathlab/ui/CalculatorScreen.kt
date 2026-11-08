@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.mathlab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mathlab.data.AppState
import app.mathlab.math.*

class CalcState {
    var field by mutableStateOf(TextFieldValue(""))
    var result by mutableStateOf<String?>(null)
    var resultValue by mutableStateOf<Double?>(null)
    var error by mutableStateOf<String?>(null)
    var lastAns by mutableStateOf<Double?>(null)
    var lastNode by mutableStateOf<Node?>(null)
    var showSci by mutableStateOf(false)

    fun setText(s: String) {
        field = TextFieldValue(s, TextRange(s.length))
        result = null; resultValue = null; error = null
    }

    fun clear() = setText("")

    fun insert(s: String, isOp: Boolean = false) {
        if (result != null) {
            val rv = resultValue
            setText(if (isOp && rv != null && rv.isFinite()) result!! else "")
        }
        error = null
        val t = field.text
        val sel = field.selection
        field = TextFieldValue(t.replaceRange(sel.min, sel.max, s), TextRange(sel.min + s.length))
    }

    fun backspace() {
        if (result != null) { result = null; resultValue = null }
        error = null
        val t = field.text
        val sel = field.selection
        if (!sel.collapsed) {
            field = TextFieldValue(t.removeRange(sel.min, sel.max), TextRange(sel.min))
            return
        }
        if (sel.start == 0) return
        val before = t.substring(0, sel.start)
        val m = Regex("(ans|[a-z]+\\()$").find(before)
        val cut = m?.value?.length ?: 1
        field = TextFieldValue(t.removeRange(sel.start - cut, sel.start), TextRange(sel.start - cut))
    }

    fun evaluate(app: AppState) {
        val expr = field.text.trim()
        if (expr.isEmpty()) return
        try {
            val node = Calc.parse(expr, lastAns)
            val v = Ev.clean(Ev.eval(node, Env(app.angle)))
            lastNode = node
            result = fmt(v); resultValue = v; error = null
            if (v.isFinite()) { lastAns = v; app.addHistory(expr, result!!) }
        } catch (e: MathError) {
            error = e.message
        } catch (e: Exception) {
            error = "Invalid expression"
        }
    }
}

private data class Sci(val label: String, val text: String, val op: Boolean = false)

private val SCI = listOf(
    Sci("sin", "sin("), Sci("cos", "cos("), Sci("tan", "tan("), Sci("π", "π"), Sci("e", "e"),
    Sci("asin", "asin("), Sci("acos", "acos("), Sci("atan", "atan("), Sci("x", "x"), Sci("ans", "ans"),
    Sci("sinh", "sinh("), Sci("cosh", "cosh("), Sci("tanh", "tanh("), Sci("ln", "ln("), Sci("log", "log("),
    Sci("√", "sqrt("), Sci("x²", "^2", true), Sci("xʸ", "^", true), Sci("1/x", "1/("), Sci("n!", "!", true),
    Sci("|x|", "abs("), Sci("∛", "cbrt("), Sci("eˣ", "exp("), Sci("nCr", "ncr("), Sci("nPr", "npr("),
    Sci("%", "%", true), Sci("mod", "mod("), Sci("log₂", "log2("), Sci("ʸ√x", "root("), Sci(",", ","),
    Sci("∫", "integ("), Sci("d/dx", "deriv("), Sci("sec", "sec("), Sci("csc", "csc("), Sci("cot", "cot("),
    Sci("°", "°", true), Sci("rad()", "rad("), Sci("deg()", "deg("), Sci("gcd", "gcd("), Sci("lcm", "lcm(")
)

private class KeyDef(val label: String, val kind: Int, val icon: ImageVector? = null, val onClick: () -> Unit)

@Composable
fun CalculatorScreen(app: AppState, calc: CalcState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var showSteps by remember { mutableStateOf(false) }

    val preview: String? = remember(calc.field.text, calc.result, app.angle, calc.lastAns) {
        if (calc.result != null || calc.field.text.isBlank()) null
        else try {
            val v = Calc.evaluate(calc.field.text, Env(app.angle), calc.lastAns)
            if (v.isNaN()) null else fmt(v)
        } catch (e: Exception) { null }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = app.angle == AngleMode.DEG, onClick = { app.setAngle(AngleMode.DEG) }, label = { Text("DEG") })
            FilterChip(selected = app.angle == AngleMode.RAD, onClick = { app.setAngle(AngleMode.RAD) }, label = { Text("RAD") })
            Spacer(Modifier.weight(1f))
            FilledTonalIconToggleButton(checked = calc.showSci, onCheckedChange = { calc.showSci = it }) {
                Icon(Icons.Default.Functions, contentDescription = "Scientific functions")
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            val len = calc.field.text.length
            val fs = when { len <= 9 -> 56; len <= 14 -> 44; len <= 22 -> 34; else -> 26 }
            val onSurface = MaterialTheme.colorScheme.onSurface
            BasicTextField(
                value = calc.field,
                onValueChange = { calc.field = it },
                readOnly = true,
                textStyle = TextStyle(color = onSurface, fontSize = fs.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Light),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (it.isFocused) keyboard?.hide() },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        if (calc.field.text.isEmpty()) Text("0", fontSize = fs.sp, fontWeight = FontWeight.Light, color = onSurface.copy(alpha = 0.3f))
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            val err = calc.error
            val shown = calc.result ?: preview
            if (err != null) {
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
            } else if (shown != null) {
                Text(
                    if (calc.result != null) "= $shown" else shown,
                    style = if (calc.result != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                    color = if (calc.result != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            val rv = calc.resultValue
            if (calc.result != null && rv != null && rv.isFinite()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    asFraction(rv)?.let { (n, d) ->
                        Text("≈ $n/$d", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { showSteps = true }) { Text("Show steps") }
                }
            }
        }

        AnimatedVisibility(calc.showSci) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().height(172.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(SCI) { k ->
                    KeyButton(
                        k.label, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface,
                        Modifier.height(44.dp), null, 15.sp
                    ) { calc.insert(k.text, k.op) }
                }
            }
        }

        Keypad(calc, app, Modifier.weight(1.5f).fillMaxWidth().padding(bottom = 8.dp))
    }

    if (showSteps) {
        val node = calc.lastNode
        ModalBottomSheet(onDismissRequest = { showSteps = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                if (node != null) {
                    val env = Env(app.angle)
                    val sol = remember(node, app.angle) {
                        Solution(
                            "Step by step  (${app.angle.name})", calc.result ?: "",
                            listOf(
                                Method("Grouped by order of operations", Stepper.steps(node, env, false)),
                                Method("One operation at a time", Stepper.steps(node, env, true))
                            ),
                            if (node.toString().contains("integ") || node.toString().contains("deriv"))
                                listOf("Derivatives and integrals are computed numerically.") else emptyList()
                        )
                    }
                    SolutionView(sol)
                }
            }
        }
    }
}

@Composable
private fun Keypad(calc: CalcState, app: AppState, modifier: Modifier) {
    fun d(s: String) = KeyDef(s, 0) { calc.insert(s) }
    val rows = listOf(
        listOf(KeyDef("AC", 3) { calc.clear() }, KeyDef("(", 4) { calc.insert("(") }, KeyDef(")", 4) { calc.insert(")") }, KeyDef("÷", 1) { calc.insert("÷", true) }),
        listOf(d("7"), d("8"), d("9"), KeyDef("×", 1) { calc.insert("×", true) }),
        listOf(d("4"), d("5"), d("6"), KeyDef("−", 1) { calc.insert("−", true) }),
        listOf(d("1"), d("2"), d("3"), KeyDef("+", 1) { calc.insert("+", true) }),
        listOf(d("0"), d("."), KeyDef("⌫", 3, Icons.AutoMirrored.Filled.Backspace) { calc.backspace() }, KeyDef("=", 2) { calc.evaluate(app) })
    )
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val (bg, fg) = when (k.kind) {
                        1 -> cs.secondaryContainer to cs.onSecondaryContainer
                        2 -> cs.primary to cs.onPrimary
                        3 -> cs.tertiaryContainer to cs.onTertiaryContainer
                        4 -> cs.surfaceContainerHighest to cs.onSurface
                        else -> cs.surfaceContainerHigh to cs.onSurface
                    }
                    KeyButton(k.label, bg, fg, Modifier.weight(1f).fillMaxHeight(), k.icon, 28.sp, k.onClick)
                }
            }
        }
    }
}

/** Key whose corners "morph" from very round to squarer while pressed (Material 3 expressive feel). */
@Composable
fun KeyButton(
    label: String, container: Color, content: Color, modifier: Modifier,
    icon: ImageVector? = null, fontSize: TextUnit = 26.sp, onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val radius by animateDpAsState(
        if (pressed) 14.dp else 32.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "keyRadius"
    )
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(radius),
        color = container, contentColor = content, interactionSource = src
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = label)
            else Text(label, fontSize = fontSize, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}
