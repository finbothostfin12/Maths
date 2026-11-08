package app.mathlab.math

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

class MathError(message: String) : Exception(message)

enum class AngleMode { DEG, RAD }

sealed class Node
data class Num(val v: Double) : Node()
data class Const(val name: String) : Node()
data class Var(val name: String) : Node()
data class Neg(val e: Node) : Node()
data class Bin(val op: Char, val l: Node, val r: Node) : Node()
data class Fn(val name: String, val args: List<Node>) : Node()
data class Post(val op: Char, val e: Node) : Node()

data class Step(val text: String, val math: String = "")

fun Node.children(): List<Node> = when (this) {
    is Neg -> listOf(e)
    is Bin -> listOf(l, r)
    is Fn -> args
    is Post -> listOf(e)
    else -> emptyList()
}

// ───────────────────────── Tokenizer ─────────────────────────

private val FUNCS = listOf(
    "asinh", "acosh", "atanh", "sinh", "cosh", "tanh", "asin", "acos", "atan2", "atan",
    "sin", "cos", "tan", "sec", "csc", "cot", "log2", "log", "ln", "sqrt", "cbrt", "abs",
    "exp", "floor", "ceil", "round", "sign", "fact", "root", "min", "max", "mod", "gcd",
    "lcm", "ncr", "npr", "deriv", "integ", "rad", "deg"
)
private val NAMES = (FUNCS + listOf("pi", "ans", "e")).sortedByDescending { it.length }

internal class Tok(val kind: Char, val text: String, val num: Double = 0.0)

fun normalize(s: String): String {
    var t = s.lowercase()
    val rep = listOf(
        "**" to "^", "×" to "*", "÷" to "/", "−" to "-", "–" to "-", "—" to "-", "·" to "*", "∙" to "*",
        "π" to "pi", "√" to "sqrt", "∛" to "cbrt", "²" to "^2", "³" to "^3",
        "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "theta" to "θ",
        "{" to "(", "}" to ")", "[" to "(", "]" to ")"
    )
    for ((a, b) in rep) t = t.replace(a, b)
    return t
}

private fun tokenize(src: String): List<Tok> {
    val t = normalize(src)
    val out = ArrayList<Tok>()
    var i = 0
    while (i < t.length) {
        val ch = t[i]
        when {
            ch.isWhitespace() -> i++
            ch.isDigit() || (ch == '.' && i + 1 < t.length && t[i + 1].isDigit()) -> {
                val st = i
                var dot = false
                while (i < t.length && (t[i].isDigit() || (t[i] == '.' && !dot))) {
                    if (t[i] == '.') dot = true
                    i++
                }
                val s = t.substring(st, i)
                out.add(Tok('n', s, s.toDouble()))
            }
            ch.isLetter() -> {
                val st = i
                while (i < t.length && t[i].isLetter()) i++
                val run = t.substring(st, i)
                var p = 0
                while (p < run.length) {
                    val name = NAMES.firstOrNull { run.startsWith(it, p) }
                    if (name != null) {
                        val kind = when (name) {
                            "pi", "e" -> 'c'
                            "ans" -> 'a'
                            else -> 'f'
                        }
                        out.add(Tok(kind, name))
                        p += name.length
                    } else {
                        out.add(Tok('v', run[p].toString()))
                        p++
                    }
                }
            }
            ch in "+-*/^()!%°,=" -> { out.add(Tok('o', ch.toString())); i++ }
            else -> throw MathError("Unexpected '$ch'")
        }
    }
    return out
}

// ───────────────────────── Parser ─────────────────────────

internal class Parser(private val toks: List<Tok>, private val ans: Double?) {
    private var p = 0

    private fun peek(): Tok? = toks.getOrNull(p)
    private fun isOp(t: Tok?, s: String) = t != null && t.kind == 'o' && t.text == s

    fun parseAll(): Node {
        val n = expr()
        if (p < toks.size) throw MathError("Unexpected '${toks[p].text}'")
        return n
    }

    fun parseEquation(): Pair<Node, Node> {
        val l = expr()
        val r = if (isOp(peek(), "=")) { p++; expr() } else Num(0.0)
        if (p < toks.size) throw MathError("Unexpected '${toks[p].text}'")
        return l to r
    }

    private fun expr(): Node {
        var l = term()
        while (true) {
            val t = peek()
            if (t != null && t.kind == 'o' && (t.text == "+" || t.text == "-")) {
                p++
                var r = term()
                if (r is Post && r.op == '%') r = Bin('*', l, r)   // 200 + 10% = 200 + 200×10%
                l = Bin(t.text[0], l, r)
            } else break
        }
        return l
    }

    private fun startsPrimary(t: Tok) = t.kind in "nfcva" || (t.kind == 'o' && t.text == "(")

    private fun term(): Node {
        var l = unary()
        while (true) {
            val t = peek() ?: break
            if (t.kind == 'o' && (t.text == "*" || t.text == "/")) {
                p++
                l = Bin(t.text[0], l, unary())
            } else if (startsPrimary(t)) {
                l = Bin('*', l, unary())
            } else break
        }
        return l
    }

    private fun unary(): Node {
        val t = peek()
        if (isOp(t, "-")) { p++; return Neg(unary()) }
        if (isOp(t, "+")) { p++; return unary() }
        return power()
    }

    private fun power(): Node {
        val base = postfix()
        if (isOp(peek(), "^")) {
            p++
            return Bin('^', base, unary())
        }
        return base
    }

    private fun postfix(): Node {
        var n = primary()
        while (true) {
            val t = peek()
            if (t != null && t.kind == 'o' && (t.text == "!" || t.text == "%" || t.text == "°")) {
                p++
                n = Post(t.text[0], n)
            } else break
        }
        return n
    }

    private fun primary(): Node {
        val t = peek() ?: throw MathError("Expression ended unexpectedly")
        p++
        return when (t.kind) {
            'n' -> Num(t.num)
            'c' -> Const(t.text)
            'a' -> Num(ans ?: 0.0)
            'v' -> Var(t.text)
            'f' -> {
                if (isOp(peek(), "(")) {
                    p++
                    val args = ArrayList<Node>()
                    if (!isOp(peek(), ")")) {
                        while (true) {
                            args.add(expr())
                            if (isOp(peek(), ",")) p++ else break
                        }
                    }
                    if (isOp(peek(), ")")) p++
                    Fn(t.text, args)
                } else {
                    Fn(t.text, listOf(unary()))
                }
            }
            'o' -> {
                if (t.text == "(") {
                    val e = expr()
                    if (isOp(peek(), ")")) p++
                    e
                } else throw MathError("Unexpected '${t.text}'")
            }
            else -> throw MathError("Unexpected '${t.text}'")
        }
    }
}

object Calc {
    fun parse(s: String, ans: Double? = null): Node {
        val ts = tokenize(s)
        if (ts.isEmpty()) throw MathError("Empty expression")
        return Parser(ts, ans).parseAll()
    }

    fun parseEq(s: String): Pair<Node, Node> {
        val ts = tokenize(s)
        if (ts.isEmpty()) throw MathError("Empty equation")
        return Parser(ts, null).parseEquation()
    }

    fun evaluate(s: String, env: Env, ans: Double? = null): Double = Ev.clean(Ev.eval(parse(s, ans), env))
}

// ───────────────────────── Evaluator ─────────────────────────

class Env(val angle: AngleMode = AngleMode.DEG, val vars: MutableMap<String, Double> = HashMap())

fun roundSig(v: Double, n: Int): Double =
    if (v.isNaN() || v.isInfinite() || v == 0.0) v else BigDecimal(v).round(MathContext(n)).toDouble()

fun fmt(v: Double, sig: Int = 12): String {
    if (v.isNaN()) return "Undefined"
    if (v.isInfinite()) return if (v > 0) "∞" else "-∞"
    if (v == 0.0) return "0"
    val bd = BigDecimal(v).round(MathContext(sig)).stripTrailingZeros()
    val a = abs(v)
    return if (a >= 1e15 || a < 1e-6) bd.toString().replace("E+", "×10^").replace("E", "×10^")
    else bd.toPlainString()
}

fun asFraction(v: Double, maxDen: Long = 10000): Pair<Long, Long>? {
    if (!v.isFinite() || v == floor(v) || abs(v) > 1e9) return null
    val x = abs(v)
    var h0 = 0L; var h1 = 1L; var k0 = 1L; var k1 = 0L
    var b = x
    for (i in 0 until 40) {
        val a = floor(b).toLong()
        val h2 = a * h1 + h0
        val k2 = a * k1 + k0
        if (k2 > maxDen) break
        h0 = h1; h1 = h2; k0 = k1; k1 = k2
        if (abs(x - h1.toDouble() / k1) < 1e-11 * max(1.0, x)) break
        val fr = b - a
        if (fr < 1e-12) break
        b = 1.0 / fr
    }
    if (k1 == 0L) return null
    return if (abs(x - h1.toDouble() / k1) < 1e-9 * max(1.0, x)) Pair(if (v < 0) -h1 else h1, k1) else null
}

object Ev {
    fun clean(v: Double): Double {
        val r = roundSig(v, 13)
        return if (abs(r) < 1e-15) 0.0 else r
    }

    fun eval(n: Node, env: Env): Double = when (n) {
        is Num -> n.v
        is Const -> if (n.name == "pi") PI else E
        is Var -> env.vars[n.name] ?: throw MathError("Unknown variable '${n.name}'")
        is Neg -> -eval(n.e, env)
        is Bin -> {
            val a = eval(n.l, env)
            val b = eval(n.r, env)
            when (n.op) {
                '+' -> a + b
                '-' -> a - b
                '*' -> a * b
                '/' -> if (b == 0.0) Double.NaN else a / b
                '^' -> power(a, b)
                else -> throw MathError("Unknown operator")
            }
        }
        is Post -> {
            val a = eval(n.e, env)
            when (n.op) {
                '!' -> factorial(a)
                '%' -> a / 100.0
                else -> if (env.angle == AngleMode.DEG) a else Math.toRadians(a)   // degree sign
            }
        }
        is Fn -> evalFn(n, env)
    }

    private fun power(a: Double, b: Double): Double {
        if (a < 0 && b != floor(b)) {
            val inv = 1.0 / b
            if (abs(inv - Math.rint(inv)) < 1e-9 && Math.rint(inv).toLong() % 2L != 0L) return -((-a).pow(b))
            return Double.NaN
        }
        return a.pow(b)
    }

    private fun gamma(x: Double): Double {
        if (x < 0.5) return PI / (sin(PI * x) * gamma(1 - x))
        val c = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012,
            9.9843695780195716e-6, 1.5056327351493116e-7
        )
        val xx = x - 1
        var a = c[0]
        val t = xx + 7.5
        for (i in 1 until 9) a += c[i] / (xx + i)
        return sqrt(2 * PI) * t.pow(xx + 0.5) * exp(-t) * a
    }

    fun factorial(x: Double): Double {
        if (x < 0 && x == floor(x)) return Double.NaN
        if (x == floor(x)) {
            if (x > 170) return Double.POSITIVE_INFINITY
            var r = 1.0
            for (i in 2..x.toInt()) r *= i
            return r
        }
        if (x > 170) return Double.POSITIVE_INFINITY
        return gamma(x + 1)
    }

    private fun snap(v: Double) = if (abs(v) < 1e-15) 0.0 else v

    private fun sinA(x: Double, env: Env): Double {
        if (env.angle == AngleMode.DEG) {
            val m = ((x % 360.0) + 360.0) % 360.0
            return when (m) {
                0.0, 180.0 -> 0.0
                90.0 -> 1.0
                270.0 -> -1.0
                else -> sin(Math.toRadians(x))
            }
        }
        return snap(sin(x))
    }

    private fun cosA(x: Double, env: Env): Double {
        if (env.angle == AngleMode.DEG) {
            val m = ((x % 360.0) + 360.0) % 360.0
            return when (m) {
                90.0, 270.0 -> 0.0
                0.0 -> 1.0
                180.0 -> -1.0
                else -> cos(Math.toRadians(x))
            }
        }
        return snap(cos(x))
    }

    private fun tanA(x: Double, env: Env): Double {
        if (env.angle == AngleMode.DEG) {
            val m = ((x % 180.0) + 180.0) % 180.0
            if (m == 90.0) return Double.NaN
            if (m == 0.0) return 0.0
            return tan(Math.toRadians(x))
        }
        val c = cos(x)
        return if (abs(c) < 1e-15) Double.NaN else snap(tan(x))
    }

    private fun back(r: Double, env: Env) = if (env.angle == AngleMode.DEG) Math.toDegrees(r) else r

    private fun evalFn(n: Fn, env: Env): Double {
        val name = n.name
        if (name == "deriv" || name == "integ") return calculus(n, env)
        val a = n.args.map { eval(it, env) }
        fun need(k: Int) {
            if (a.size != k) throw MathError("$name needs $k argument${if (k > 1) "s" else ""}")
        }
        fun one(): Double { need(1); return a[0] }
        return when (name) {
            "sin" -> sinA(one(), env)
            "cos" -> cosA(one(), env)
            "tan" -> tanA(one(), env)
            "sec" -> { val c = cosA(one(), env); if (c == 0.0) Double.NaN else 1 / c }
            "csc" -> { val s = sinA(one(), env); if (s == 0.0) Double.NaN else 1 / s }
            "cot" -> { val x = one(); val s = sinA(x, env); if (s == 0.0) Double.NaN else cosA(x, env) / s }
            "asin" -> back(asin(one()), env)
            "acos" -> back(acos(one()), env)
            "atan" -> back(atan(one()), env)
            "atan2" -> { need(2); back(atan2(a[0], a[1]), env) }
            "sinh" -> sinh(one())
            "cosh" -> cosh(one())
            "tanh" -> tanh(one())
            "asinh" -> { val x = one(); ln(x + sqrt(x * x + 1)) }
            "acosh" -> { val x = one(); if (x < 1) Double.NaN else ln(x + sqrt(x * x - 1)) }
            "atanh" -> { val x = one(); if (abs(x) >= 1) Double.NaN else 0.5 * ln((1 + x) / (1 - x)) }
            "ln" -> { val x = one(); if (x <= 0) Double.NaN else ln(x) }
            "log" -> if (a.size == 2) { if (a[0] <= 0 || a[1] <= 0 || a[1] == 1.0) Double.NaN else ln(a[0]) / ln(a[1]) }
                     else { val x = one(); if (x <= 0) Double.NaN else log10(x) }
            "log2" -> { val x = one(); if (x <= 0) Double.NaN else ln(x) / ln(2.0) }
            "sqrt" -> { val x = one(); if (x < 0) Double.NaN else sqrt(x) }
            "cbrt" -> Math.cbrt(one())
            "abs" -> abs(one())
            "exp" -> exp(one())
            "floor" -> floor(one())
            "ceil" -> ceil(one())
            "round" -> floor(one() + 0.5)
            "sign" -> sign(one())
            "fact" -> factorial(one())
            "root" -> {
                need(2)
                val k = a[0]; val x = a[1]
                if (k == 0.0) Double.NaN
                else if (x < 0) { if (k == floor(k) && k.toLong() % 2L != 0L) -((-x).pow(1 / k)) else Double.NaN }
                else x.pow(1 / k)
            }
            "min" -> { if (a.isEmpty()) throw MathError("min needs arguments"); a.min() }
            "max" -> { if (a.isEmpty()) throw MathError("max needs arguments"); a.max() }
            "mod" -> { need(2); if (a[1] == 0.0) Double.NaN else a[0] - a[1] * floor(a[0] / a[1]) }
            "gcd" -> { need(2); gcd(a[0].toLong(), a[1].toLong()).toDouble() }
            "lcm" -> { need(2); val g = gcd(a[0].toLong(), a[1].toLong()); if (g == 0L) 0.0 else abs(a[0].toLong() / g * a[1].toLong()).toDouble() }
            "ncr" -> { need(2); ncr(a[0], a[1]) }
            "npr" -> { need(2); npr(a[0], a[1]) }
            "rad" -> Math.toRadians(one())
            "deg" -> Math.toDegrees(one())
            else -> throw MathError("Unknown function '$name'")
        }
    }

    private fun gcd(x: Long, y: Long): Long {
        var p = abs(x); var q = abs(y)
        while (q != 0L) { val t = p % q; p = q; q = t }
        return p
    }

    private fun ncr(n: Double, k: Double): Double {
        if (n != floor(n) || k != floor(k) || k < 0 || n < k) return Double.NaN
        val kk = min(k, n - k).toLong()
        var r = 1.0
        for (i in 1..kk) r = r * (n - kk + i) / i
        return Math.rint(r)
    }

    private fun npr(n: Double, k: Double): Double {
        if (n != floor(n) || k != floor(k) || k < 0 || n < k) return Double.NaN
        var r = 1.0
        for (i in 0 until k.toLong()) r *= (n - i)
        return r
    }

    private fun calculus(n: Fn, env: Env): Double {
        val f = n.args.getOrNull(0) ?: throw MathError("Missing function")
        val e2 = Env(env.angle, HashMap(env.vars))
        val g: (Double) -> Double = { x -> e2.vars["x"] = x; eval(f, e2) }
        if (n.name == "deriv") {
            if (n.args.size != 2) throw MathError("Use deriv(f(x), x0)")
            val x0 = eval(n.args[1], env)
            val h = 1e-3 * max(1.0, abs(x0))
            val d = (-g(x0 + 2 * h) + 8 * g(x0 + h) - 8 * g(x0 - h) + g(x0 - 2 * h)) / (12 * h)
            return roundSig(d, 9)
        }
        if (n.args.size != 3) throw MathError("Use integ(f(x), a, b)")
        val a = eval(n.args[1], env)
        val b = eval(n.args[2], env)
        return roundSig(simpson(g, a, b), 10)
    }

    private fun simpson(f: (Double) -> Double, a: Double, b: Double): Double {
        if (a == b) return 0.0
        fun rec(a: Double, b: Double, fa: Double, fm: Double, fb: Double, whole: Double, eps: Double, depth: Int): Double {
            val m = (a + b) / 2
            val flm = f((a + m) / 2)
            val frm = f((m + b) / 2)
            val left = (m - a) / 6 * (fa + 4 * flm + fm)
            val right = (b - m) / 6 * (fm + 4 * frm + fb)
            if (left.isNaN() || right.isNaN()) return Double.NaN
            if (depth <= 0 || abs(left + right - whole) <= 15 * eps) return left + right + (left + right - whole) / 15
            return rec(a, m, fa, flm, fm, left, eps / 2, depth - 1) + rec(m, b, fm, frm, fb, right, eps / 2, depth - 1)
        }
        val fa = f(a); val fb = f(b); val fm = f((a + b) / 2)
        val whole = (b - a) / 6 * (fa + 4 * fm + fb)
        if (!whole.isFinite()) return Double.NaN
        return rec(a, b, fa, fm, fb, whole, 1e-10, 18)
    }
}

// ───────────────────────── Pretty printing ─────────────────────────

private fun prec(n: Node): Int = when (n) {
    is Bin -> when (n.op) { '+', '-' -> 1; '*', '/' -> 2; else -> 4 }
    is Neg -> 3
    is Num -> if (n.v < 0) 0 else 6
    else -> 6
}

private fun fnLabel(name: String) = when (name) {
    "sqrt" -> "√"; "cbrt" -> "∛"; "ncr" -> "nCr"; "npr" -> "nPr"; else -> name
}

private fun str(n: Node, parent: Int): String {
    val s = when (n) {
        is Num -> fmt(n.v, 10)
        is Const -> if (n.name == "pi") "π" else n.name
        is Var -> n.name
        is Neg -> "−" + str(n.e, 3)
        is Bin -> {
            val p = prec(n)
            when (n.op) {
                '^' -> str(n.l, p + 1) + "^" + str(n.r, p)
                '-', '/' -> str(n.l, p) + (if (n.op == '-') " − " else " ÷ ") + str(n.r, p + 1)
                '+' -> str(n.l, p) + " + " + str(n.r, p)
                else -> str(n.l, p) + " × " + str(n.r, p)
            }
        }
        is Post -> str(n.e, 6) + n.op
        is Fn -> fnLabel(n.name) + "(" + n.args.joinToString(", ") { str(it, 0) } + ")"
    }
    return if (prec(n) < parent) "($s)" else s
}

fun Node.show(): String = str(this, 0)

// ───────────────────────── Step-by-step reducer ─────────────────────────

object Stepper {
    private fun isLeaf(n: Node) = n is Num || n is Const

    private fun kids(n: Node): List<Node> =
        if (n is Fn && (n.name == "deriv" || n.name == "integ")) n.args.drop(1) else n.children()

    private fun rebuild(n: Node, f: (Node) -> Node): Node = when (n) {
        is Neg -> Neg(f(n.e))
        is Bin -> { val l = f(n.l); val r = f(n.r); Bin(n.op, l, r) }
        is Post -> Post(n.op, f(n.e))
        is Fn ->
            if (n.name == "deriv" || n.name == "integ") Fn(n.name, listOf(n.args[0]) + n.args.drop(1).map(f))
            else Fn(n.name, n.args.map(f))
        else -> n
    }

    private fun maxDepth(n: Node, d: Int): Int {
        if (isLeaf(n) || n is Var) return -1
        val ks = kids(n)
        if (ks.all { isLeaf(it) }) return d
        return ks.maxOf { maxDepth(it, d + 1) }
    }

    private fun reduce(n: Node, d: Int, target: Int, env: Env, notes: MutableList<String>, onlyFirst: Boolean): Node {
        if (isLeaf(n) || n is Var) return n
        val ks = kids(n)
        if (ks.all { isLeaf(it) } && d == target && !(onlyFirst && notes.isNotEmpty())) {
            val v = Ev.clean(Ev.eval(n, env))
            notes.add("${n.show()} = ${fmt(v, 10)}")
            return Num(v)
        }
        return rebuild(n) { reduce(it, d + 1, target, env, notes, onlyFirst) }
    }

    fun steps(root: Node, env: Env, onlyFirst: Boolean = false): List<Step> {
        val out = ArrayList<Step>()
        var cur = root
        out.add(Step("Start with the expression", cur.show()))
        var guard = 0
        while (guard++ < 120) {
            val d = maxDepth(cur, 0)
            if (d < 0) break
            val notes = ArrayList<String>()
            cur = reduce(cur, 0, d, env, notes, onlyFirst)
            out.add(Step("Calculate " + notes.joinToString("; "), cur.show()))
        }
        return out
    }
}
