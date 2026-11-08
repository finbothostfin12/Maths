@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.mathlab.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.mathlab.data.AppState
import app.mathlab.math.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Cleans OCR output and guesses what kind of problem it is. */
object Ocr {
    fun clean(raw: String): String {
        var t = raw.replace("\r", "").trim()
        t = t.replace('—', '-').replace('–', '-').replace('−', '-')
        t = t.replace(Regex("(?<=\\d)[Oo](?=\\d)"), "0")
        t = t.replace(Regex("(?<=\\d)\\s*[xX]\\s*(?=\\d)"), "×")
        t = t.replace(Regex("(?<=\\d)[lI|](?=\\d)"), "1")
        return t
    }

    fun stripEq(t: String) = t.replace("\n", " ").trim().trimEnd('=', '?', ' ')

    fun guess(t: String): Int {
        val low = t.lowercase()
        val hasFn = Regex("sin|cos|tan").containsMatchIn(low)
        if (Regex("[αβγ]|triangle|hypotenuse|dreieck").containsMatchIn(low) || (low.contains("°") && !hasFn)) {
            return if (Regex("(^|[^0-9.])90\\s*°?").containsMatchIn(low)) 2 else 3
        }
        if (low.contains("=")) {
            try {
                val (l, r) = Calc.parseEq(low.replace("\n", " "))
                val vs = mutableSetOf<String>()
                collectVars(l, vs); collectVars(r, vs)
                if (vs.isNotEmpty()) return 1
            } catch (e: Exception) { /* fall through */ }
        }
        return 0
    }

    /** Returns values for a, b, c, α, β, γ (empty string = unknown). */
    fun fillTriangle(text: String, right: Boolean): List<String> {
        val out = Array(6) { "" }
        var rest = text.replace(',', '.')
        val labeled = Regex("([a-cA-CαβγΑΒΓ])\\s*=\\s*(\\d+(?:\\.\\d+)?)\\s*(°)?")
        rest = labeled.replace(rest) { m ->
            val lab = m.groupValues[1]
            val num = m.groupValues[2]
            val isDeg = m.groupValues[3].isNotEmpty()
            val idx = when (lab) {
                "a" -> if (isDeg) 3 else 0
                "b" -> if (isDeg) 4 else 1
                "c" -> if (isDeg) 5 else 2
                "A", "α", "Α" -> 3
                "B", "β", "Β" -> 4
                "C", "γ", "Γ" -> 5
                else -> -1
            }
            if (idx >= 0 && out[idx].isEmpty()) out[idx] = num
            " "
        }
        val angRe = Regex("(\\d+(?:\\.\\d+)?)\\s*°")
        val angles = angRe.findAll(rest).map { it.groupValues[1] }.toList()
        rest = angRe.replace(rest, " ")
        val sides = Regex("\\d+(?:\\.\\d+)?").findAll(rest).map { it.value }.toList()
        var ai = 3
        for (a in angles) {
            if (right && a.toDoubleOrNull() == 90.0) continue
            while (ai < 6 && out[ai].isNotEmpty()) ai++
            if (ai < 6) { out[ai] = a; ai++ }
        }
        var si = 0
        for (s in sides) {
            while (si < 3 && out[si].isNotEmpty()) si++
            if (si < 3) { out[si] = s; si++ }
        }
        if (right) out[5] = ""
        return out.toList()
    }
}

class ScanState {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var a by mutableStateOf<Offset?>(null)
    var b by mutableStateOf<Offset?>(null)
    var busy by mutableStateOf(false)
    var text by mutableStateOf<String?>(null)
    var mode by mutableIntStateOf(0)          // 0 expression, 1 equation, 2 right triangle, 3 any triangle
    val tri = mutableStateListOf("", "", "", "", "", "")
    var solution by mutableStateOf<Solution?>(null)
    var error by mutableStateOf<String?>(null)

    fun load(ctx: Context, uri: Uri) {
        try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: run { error = "Could not open the image"; return }
            val orient = cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val deg = when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg != 0f) {
                val m = Matrix().apply { postRotate(deg) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            bitmap = bmp
            a = null; b = null; text = null; solution = null; error = null
        } catch (e: Exception) {
            error = "Could not load the image"
        }
    }

    private fun crop(): Bitmap {
        val bmp = bitmap!!
        val p = a; val q = b
        if (p == null || q == null || abs(p.x - q.x) < 0.02f || abs(p.y - q.y) < 0.02f) return bmp
        val padX = 0.01f; val padY = 0.01f
        val l = ((min(p.x, q.x) - padX).coerceAtLeast(0f) * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val t = ((min(p.y, q.y) - padY).coerceAtLeast(0f) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val r = ((max(p.x, q.x) + padX).coerceAtMost(1f) * bmp.width).toInt().coerceIn(l + 1, bmp.width)
        val btm = ((max(p.y, q.y) + padY).coerceAtMost(1f) * bmp.height).toInt().coerceIn(t + 1, bmp.height)
        return Bitmap.createBitmap(bmp, l, t, r - l, btm - t)
    }

    fun setMode(m: Int) {
        mode = m
        if (m >= 2 && tri.all { it.isEmpty() }) fillFromText()
    }

    fun fillFromText() {
        val f = Ocr.fillTriangle(text.orEmpty(), mode == 2)
        for (i in 0 until 6) tri[i] = f[i]
    }

    fun read() {
        if (bitmap == null) return
        busy = true; error = null; solution = null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(InputImage.fromBitmap(crop(), 0))
                .addOnSuccessListener { res ->
                    val t = Ocr.clean(res.text)
                    text = t
                    mode = Ocr.guess(t)
                    for (i in 0 until 6) tri[i] = ""
                    if (mode >= 2) fillFromText()
                    if (t.isBlank()) error = "No text found in the selection. Try a tighter box, better light, or type it below."
                    busy = false
                }
                .addOnFailureListener { busy = false; error = "Text recognition failed: ${it.message}" }
        } catch (e: Exception) {
            busy = false; error = "Text recognition failed"
        }
    }

    private fun num(i: Int) = tri[i].replace(',', '.').trim().toDoubleOrNull()

    fun solve(app: AppState) {
        error = null; solution = null
        try {
            val t = text.orEmpty().trim()
            val summary: String
            val sol = when (mode) {
                0 -> {
                    val s = Ocr.stripEq(t)
                    val node = Calc.parse(s, null)
                    val env = Env(app.angle)
                    val v = Ev.clean(Ev.eval(node, env))
                    summary = s
                    val frac = asFraction(v)?.let { "   (≈ ${it.first}/${it.second})" } ?: ""
                    Solution("Expression", fmt(v) + frac, listOf(
                        Method("By order of operations", Stepper.steps(node, env, false)),
                        Method("One operation at a time", Stepper.steps(node, env, true))
                    ))
                }
                1 -> { summary = t.replace("\n", " "); EqSolver.solve(summary, app.angle) }
                2 -> {
                    summary = "Right triangle " + describeTri()
                    TriangleSolver.right(num(0), num(1), num(2), num(3), num(4))
                }
                else -> {
                    summary = "Triangle " + describeTri()
                    TriangleSolver.general(listOf(num(0), num(1), num(2)), listOf(num(3), num(4), num(5)))
                }
            }
            solution = sol
            app.addHistory(summary, sol.answer.replace("\n", " | "), "scan")
        } catch (e: MathError) {
            error = e.message
        } catch (e: Exception) {
            error = "Could not solve this. Please check the values."
        }
    }

    private fun describeTri(): String {
        val n = listOf("a", "b", "c", "α", "β", "γ")
        return n.indices.filter { tri[it].isNotBlank() }.joinToString(", ") { n[it] + "=" + tri[it] + (if (it >= 3) "°" else "") }
    }
}

@Composable
fun ScanScreen(app: AppState, scan: ScanState) {
    val ctx = LocalContext.current
    val photoUri = remember {
        val dir = File(ctx.cacheDir, "images").apply { mkdirs() }
        val f = File(dir, "capture.jpg")
        if (!f.exists()) f.createNewFile()
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) scan.load(ctx, photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scan.load(ctx, uri) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Scan & solve", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Take a photo of a calculation, an equation or a triangle. Drag a box around the part you want, " +
                    "and MathLab reads it on your device and shows every step.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { camera.launch(photoUri) }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Take photo") }
            FilledTonalButton(onClick = { gallery.launch("image/*") }) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Gallery") }
        }
        if (scan.bitmap == null && scan.text == null) {
            TextButton(onClick = { scan.text = "" }) { Text("or type the problem manually") }
        }

        scan.bitmap?.let { bmp ->
            Box(Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height).clip(RoundedCornerShape(24.dp))) {
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Canvas(
                    Modifier.fillMaxSize().pointerInput(bmp) {
                        detectDragGestures(
                            onDragStart = { o ->
                                val p = Offset((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
                                scan.a = p; scan.b = p
                            },
                            onDrag = { change, _ ->
                                scan.b = Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                            }
                        )
                    }
                ) {
                    val p = scan.a; val q = scan.b
                    if (p != null && q != null) {
                        val l = min(p.x, q.x) * size.width; val r = max(p.x, q.x) * size.width
                        val t = min(p.y, q.y) * size.height; val bt = max(p.y, q.y) * size.height
                        val scrim = Color.Black.copy(alpha = 0.55f)
                        drawRect(scrim, Offset(0f, 0f), Size(size.width, t))
                        drawRect(scrim, Offset(0f, bt), Size(size.width, size.height - bt))
                        drawRect(scrim, Offset(0f, t), Size(l, bt - t))
                        drawRect(scrim, Offset(r, t), Size(size.width - r, bt - t))
                        drawRect(Color.White, Offset(l, t), Size(r - l, bt - t), style = Stroke(3.dp.toPx()))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { scan.read() }, enabled = !scan.busy) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp))
                    Text(if (scan.a != null) "Read selection" else "Read whole photo")
                }
                if (scan.a != null) TextButton(onClick = { scan.a = null; scan.b = null }) { Text("Clear box") }
                if (scan.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            }
        }

        scan.text?.let { txt ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("What was found – fix anything that is wrong", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = txt, onValueChange = { scan.text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Detected text") })
                    Text("Problem type", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Expression", "Equation (find x)", "Right triangle", "Any triangle").forEachIndexed { i, l ->
                            FilterChip(selected = scan.mode == i, onClick = { scan.setMode(i) }, label = { Text(l) })
                        }
                    }
                    if (scan.mode >= 2) {
                        Text(
                            if (scan.mode == 2) "a and b are the legs, c is the hypotenuse; α is opposite a, β is opposite b. Fill in what you know and leave the unknown ones empty."
                            else "a, b, c are sides; α, β, γ are the angles opposite them. Enter exactly three known values (at least one side).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (i in 0..2) TriField(scan, i, listOf("a", "b", "c")[i], Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TriField(scan, 3, "α (°)", Modifier.weight(1f))
                            TriField(scan, 4, "β (°)", Modifier.weight(1f))
                            if (scan.mode == 3) TriField(scan, 5, "γ (°)", Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                        }
                        TextButton(onClick = { for (i in 0 until 6) scan.tri[i] = ""; scan.fillFromText() }) { Text("Re-detect values from text") }
                    }
                    Button(onClick = { scan.solve(app) }, modifier = Modifier.fillMaxWidth()) { Text("Solve with steps") }
                }
            }
        }

        scan.error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        scan.solution?.let { SolutionView(it) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TriField(scan: ScanState, i: Int, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = scan.tri[i], onValueChange = { scan.tri[i] = it }, singleLine = true,
        label = { Text(label) }, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
