package app.mathlab.math

import kotlin.math.*

data class Method(val name: String, val steps: List<Step>)
data class Solution(
    val title: String,
    val answer: String,
    val methods: List<Method>,
    val notes: List<String> = emptyList()
)

fun collectVars(n: Node, out: MutableSet<String>) {
    if (n is Var) out.add(n.name) else n.children().forEach { collectVars(it, out) }
}

fun polyStr(a: Double, b: Double, c: Double, v: String): String {
    val sb = StringBuilder()
    fun add(coef: Double, term: String) {
        if (abs(coef) < 1e-12) return
        val m = abs(coef)
        val num = if (term.isNotEmpty() && abs(m - 1) < 1e-12) "" else fmt(m, 8)
        if (sb.isEmpty()) {
            if (coef < 0) sb.append("−")
            sb.append(num).append(term)
        } else {
            sb.append(if (coef < 0) " − " else " + ").append(num).append(term)
        }
    }
    add(a, "${v}²"); add(b, v); add(c, "")
    return if (sb.isEmpty()) "0" else sb.toString()
}

object EqSolver {

    fun solve(input: String, angle: AngleMode): Solution {
        val (l, r) = Calc.parseEq(input)
        val vs = sortedSetOf<String>()
        collectVars(l, vs); collectVars(r, vs)
        if (vs.isEmpty()) throw MathError("There is no unknown to solve for")
        if (vs.size > 1) throw MathError("Only one unknown is supported (found: ${vs.joinToString(", ")})")
        val v = vs.first()
        val env = Env(angle)
        val f: (Double) -> Double = { x ->
            env.vars[v] = x
            try { Ev.eval(l, env) - Ev.eval(r, env) } catch (e: MathError) { Double.NaN }
        }
        val eq = "${l.show()} = ${r.show()}"
        val co = coeffs(f)
        if (co != null) {
            val (a, b, c) = co
            return when {
                abs(a) > 1e-12 -> quadratic(a, b, c, v, eq, f)
                abs(b) > 1e-12 -> linear(b, c, v, eq, f, l, env)
                abs(c) < 1e-12 -> Solution("Equation", "Every real number is a solution",
                    listOf(Method("Simplify", listOf(Step("Both sides are identical after simplifying", eq)))))
                else -> Solution("Equation", "No solution",
                    listOf(Method("Simplify", listOf(Step("Moving everything to one side leaves a false statement", "${fmt(c)} = 0")))))
            }
        }
        val lim = if (angle == AngleMode.DEG) 360.0 else 100.0
        trigSimple(l, r, v, angle, eq, f, lim)?.let { return it }

        val (roots, num) = numeric(f, v, eq, lim)
        val answer = if (roots.isEmpty()) "No real solution found in [−${fmt(lim)}, ${fmt(lim)}]"
        else "$v ≈ " + roots.take(8).joinToString(",  ") { fmt(it, 8) } +
                (if (roots.size > 8) "  … (${roots.size} solutions in range)" else "")
        val graph = Method("Graphical", listOf(
            Step("Draw y = (left side) and y = (right side) as two curves", eq),
            Step("Every crossing point is a solution; its x-coordinate is the value of $v",
                if (roots.isEmpty()) "no crossings between −${fmt(lim)} and ${fmt(lim)}"
                else roots.take(8).joinToString(",  ") { "$v = ${fmt(it, 6)}" })
        ))
        return Solution("Equation", answer, listOf(num, graph))
    }

    // Fit a·x² + b·x + c to f and verify; null when f is not a polynomial of degree ≤ 2
    private fun coeffs(f: (Double) -> Double): Triple<Double, Double, Double>? {
        val f0 = f(0.0); val f1 = f(1.0); val fm = f(-1.0)
        if (f0.isNaN() || f1.isNaN() || fm.isNaN()) return null
        val c = f0
        val a = (f1 + fm) / 2 - c
        val b = (f1 - fm) / 2
        for (t in doubleArrayOf(2.0, -3.0, 0.5, 7.0)) {
            val e = a * t * t + b * t + c
            val g = f(t)
            if (g.isNaN() || abs(e - g) > 1e-7 * max(1.0, abs(g))) return null
        }
        return Triple(Ev.clean(a), Ev.clean(b), Ev.clean(c))
    }

    private fun linear(b: Double, c: Double, v: String, eq: String, f: (Double) -> Double, lhs: Node, env: Env): Solution {
        val sol = Ev.clean(-c / b)
        val alg = mutableListOf(
            Step("Write the equation", eq),
            Step("Move all terms to the left so the right side is 0", "${polyStr(0.0, b, c, v)} = 0")
        )
        if (abs(c) > 1e-12) alg.add(Step("Move the constant to the other side", "${polyStr(0.0, b, 0.0, v)} = ${fmt(-c)}"))
        alg.add(Step("Divide both sides by ${fmt(b)}", "$v = ${fmt(-c)} ÷ ${fmt(b)} = ${fmt(sol)}"))
        env.vars[v] = sol
        val yv = try { Ev.eval(lhs, env) } catch (e: MathError) { Double.NaN }
        alg.add(Step("Check: put $v = ${fmt(sol)} back into the equation", "left side = ${fmt(Ev.clean(yv))}, right side = ${fmt(Ev.clean(yv))} ✓"))
        val num = numeric(f, v, eq, 100.0).second
        val graph = Method("Graphical", listOf(
            Step("Draw y = (left side) and y = (right side); both are straight lines", eq),
            Step("They cross at one point – its x-coordinate is the solution", "($v, y) = (${fmt(sol)}, ${fmt(Ev.clean(yv))})")
        ))
        return Solution("Linear equation", "$v = ${fmt(sol)}", listOf(Method("Algebraic (balance method)", alg), graph, num))
    }

    private fun lin(v: String, r: Double) = if (r >= 0) "($v − ${fmt(r)})" else "($v + ${fmt(-r)})"

    private fun quadratic(a: Double, b: Double, c: Double, v: String, eq: String, f: (Double) -> Double): Solution {
        val d = Ev.clean(b * b - 4 * a * c)
        val std = polyStr(a, b, c, v) + " = 0"
        val head = listOf(
            Step("Write the equation", eq),
            Step("Bring it to standard form  a${v}² + b$v + c = 0  with a = ${fmt(a)}, b = ${fmt(b)}, c = ${fmt(c)}", std)
        )
        val roots = if (d >= 0) listOf((-b + sqrt(d)) / (2 * a), (-b - sqrt(d)) / (2 * a)).map { Ev.clean(it) }.distinct().sorted()
        else emptyList()
        val re = -b / (2 * a)
        val im = if (d < 0) sqrt(-d) / (2 * abs(a)) else 0.0
        val answer = when {
            d > 0 -> "$v = ${fmt(roots[0])}  or  $v = ${fmt(roots[1])}"
            d == 0.0 -> "$v = ${fmt(roots[0])}  (double root)"
            else -> "No real solution  (complex: $v = ${fmt(re)} ± ${fmt(im)}i)"
        }

        // Method 1: quadratic formula
        val m1 = ArrayList<Step>(head)
        m1.add(Step("Discriminant  D = b² − 4ac", "D = (${fmt(b)})² − 4·(${fmt(a)})·(${fmt(c)}) = ${fmt(d)}"))
        when {
            d > 0 -> {
                val s = sqrt(d)
                m1.add(Step("D > 0, so there are two real solutions:  $v = (−b ± √D) / 2a", "$v = (${fmt(-b)} ± √${fmt(d)}) / ${fmt(2 * a)}"))
                m1.add(Step("Evaluate with + and with −", "${v}₁ = (${fmt(-b)} + ${fmt(s, 8)}) / ${fmt(2 * a)} = ${fmt((-b + s) / (2 * a))}\n${v}₂ = (${fmt(-b)} − ${fmt(s, 8)}) / ${fmt(2 * a)} = ${fmt((-b - s) / (2 * a))}"))
            }
            d == 0.0 -> m1.add(Step("D = 0, so there is exactly one (double) solution:  $v = −b / 2a", "$v = ${fmt(-b)} / ${fmt(2 * a)} = ${fmt(roots[0])}"))
            else -> m1.add(Step("D < 0, so there is no real solution. The two complex solutions are", "$v = ${fmt(re)} ± ${fmt(im)}i"))
        }

        // Method 2: completing the square
        val p = b / a; val q = c / a; val half = p / 2
        val rhs = Ev.clean(half * half - q)
        val inner = if (abs(half) < 1e-12) v else "$v ${if (half < 0) "−" else "+"} ${fmt(abs(half))}"
        val m2 = ArrayList<Step>(head)
        m2.add(Step("Divide everything by a = ${fmt(a)}", "${polyStr(1.0, p, q, v)} = 0"))
        m2.add(Step("Move the constant term to the right", "${polyStr(1.0, p, 0.0, v)} = ${fmt(-q)}"))
        m2.add(Step("Add (p/2)² = (${fmt(half)})² = ${fmt(half * half)} to both sides", "${polyStr(1.0, p, half * half, v)} = ${fmt(rhs)}"))
        m2.add(Step("The left side is a perfect square", "($inner)² = ${fmt(rhs)}"))
        if (rhs > 1e-12) {
            val s = sqrt(rhs)
            m2.add(Step("Take the square root of both sides (±)", "$inner = ± ${fmt(s, 8)}"))
            m2.add(Step("Solve for $v", "$v = ${fmt(-half)} ± ${fmt(s, 8)}   →   ${fmt(Ev.clean(-half + s))}  and  ${fmt(Ev.clean(-half - s))}"))
        } else if (abs(rhs) <= 1e-12) {
            m2.add(Step("The right side is 0, so", "$v = ${fmt(-half)}"))
        } else {
            m2.add(Step("A square can never be negative, so there is no real solution", "complex: $v = ${fmt(-half)} ± ${fmt(sqrt(-rhs), 8)}i"))
        }

        val methods = mutableListOf(Method("Quadratic formula", m1), Method("Completing the square", m2))

        // Method 3: factoring (only when roots are rational)
        val isInt = listOf(a, b, c).all { abs(it - Math.rint(it)) < 1e-9 }
        val sq = if (d >= 0) sqrt(d) else Double.NaN
        if (isInt && d >= 0 && abs(sq - Math.rint(sq)) < 1e-9) {
            val m3 = ArrayList<Step>(head)
            val ac = Math.rint(a * c).toLong()
            if (ac != 0L && abs(ac) <= 20000L) {
                for (mm in -abs(ac)..abs(ac)) {
                    if (mm == 0L || ac % mm != 0L) continue
                    val nn = ac / mm
                    if (mm + nn == Math.rint(b).toLong()) {
                        m3.add(Step("Find two numbers with product a·c = $ac and sum b = ${fmt(b)}", "$mm  and  $nn"))
                        m3.add(Step("Split the middle term using those numbers", "${fmt(a)}${v}² + ($mm)$v + ($nn)$v + ${fmt(c)} = 0"))
                        break
                    }
                }
            }
            val r1 = (-b + sq) / (2 * a); val r2 = (-b - sq) / (2 * a)
            m3.add(Step("Factorised form", "${fmt(a)}${lin(v, r1)}${lin(v, r2)} = 0"))
            m3.add(Step("Zero-product rule: a product is 0 when one factor is 0", "$v = ${fmt(Ev.clean(r1))}  or  $v = ${fmt(Ev.clean(r2))}"))
            methods.add(Method("Factoring", m3))
        }
        methods.add(numeric(f, v, eq, 100.0).second)
        return Solution("Quadratic equation", answer, methods)
    }

    private fun trigSimple(l: Node, r: Node, v: String, angle: AngleMode, eq: String, f: (Double) -> Double, lim: Double): Solution? {
        val fn: Fn
        val c: Double
        if (l is Fn && r is Num) { fn = l; c = r.v } else if (r is Fn && l is Num) { fn = r; c = l.v } else return null
        if (fn.args.size != 1 || fn.args[0] != Var(v)) return null
        if (fn.name !in listOf("sin", "cos", "tan")) return null
        val deg = angle == AngleMode.DEG
        val period = if (deg) 360.0 else 2 * PI
        val halfT = if (deg) 180.0 else PI
        val unit = if (deg) "°" else " rad"
        fun toUnit(x: Double) = if (deg) Math.toDegrees(x) else x
        val steps = ArrayList<Step>()
        steps.add(Step("Write the equation", eq))
        val answer: String
        when (fn.name) {
            "sin" -> {
                if (abs(c) > 1) return Solution("Trigonometric equation", "No real solution (|sin| ≤ 1)",
                    listOf(Method("Inverse function", listOf(Step("sin can only take values between −1 and 1", "${fmt(c)} is outside [−1, 1]")))))
                val t1 = Ev.clean(toUnit(asin(c))); val t2 = Ev.clean(halfT - t1)
                steps.add(Step("Apply the inverse function (principal value)", "$v = sin⁻¹(${fmt(c)}) = ${fmt(t1, 8)}$unit"))
                steps.add(Step("sin is symmetric: sin(θ) = sin(${fmt(halfT)}$unit − θ)", "$v = ${fmt(halfT)}$unit − ${fmt(t1, 8)}$unit = ${fmt(t2, 8)}$unit"))
                steps.add(Step("Add whole periods (${fmt(period)}$unit)", "$v = ${fmt(t1, 8)}$unit + ${fmt(period)}$unit·k   or   $v = ${fmt(t2, 8)}$unit + ${fmt(period)}$unit·k,   k ∈ ℤ"))
                answer = "$v = ${fmt(t1, 8)}$unit + ${fmt(period)}$unit·k   or   $v = ${fmt(t2, 8)}$unit + ${fmt(period)}$unit·k"
            }
            "cos" -> {
                if (abs(c) > 1) return Solution("Trigonometric equation", "No real solution (|cos| ≤ 1)",
                    listOf(Method("Inverse function", listOf(Step("cos can only take values between −1 and 1", "${fmt(c)} is outside [−1, 1]")))))
                val t1 = Ev.clean(toUnit(acos(c)))
                steps.add(Step("Apply the inverse function (principal value)", "$v = cos⁻¹(${fmt(c)}) = ${fmt(t1, 8)}$unit"))
                steps.add(Step("cos is even: cos(θ) = cos(−θ)", "$v = −${fmt(t1, 8)}$unit"))
                steps.add(Step("Add whole periods (${fmt(period)}$unit)", "$v = ±${fmt(t1, 8)}$unit + ${fmt(period)}$unit·k,   k ∈ ℤ"))
                answer = "$v = ±${fmt(t1, 8)}$unit + ${fmt(period)}$unit·k"
            }
            else -> {
                val t1 = Ev.clean(toUnit(atan(c)))
                steps.add(Step("Apply the inverse function (principal value)", "$v = tan⁻¹(${fmt(c)}) = ${fmt(t1, 8)}$unit"))
                steps.add(Step("tan repeats every ${fmt(halfT)}$unit", "$v = ${fmt(t1, 8)}$unit + ${fmt(halfT)}$unit·k,   k ∈ ℤ"))
                answer = "$v = ${fmt(t1, 8)}$unit + ${fmt(halfT)}$unit·k"
            }
        }
        return Solution("Trigonometric equation", answer, listOf(Method("Inverse function", steps), numeric(f, v, eq, lim).second))
    }

    private fun numeric(f: (Double) -> Double, v: String, eq: String, lim: Double): Pair<List<Double>, Method> {
        val steps = mutableListOf(Step("Rewrite as f($v) = 0 with f($v) = (left side) − (right side)", eq))
        steps.add(Step("Scan [−${fmt(lim)}, ${fmt(lim)}] for places where f changes sign", ""))
        val n = (lim * 200).toInt()
        val roots = ArrayList<Double>()
        var firstIter: List<String>? = null
        var firstBracket = ""
        var px = -lim
        var py = f(px)
        for (i in 1..n) {
            val x = -lim + 2 * lim * i / n
            val y = f(x)
            var root: Double? = null
            if (y == 0.0) {
                root = x
            } else if (!py.isNaN() && !y.isNaN() && py != 0.0 && py * y < 0) {
                var a = px; var b = x; var fa = py
                val iters = ArrayList<String>()
                for (k in 1..60) {
                    val m = (a + b) / 2
                    val fm = f(m)
                    if (k <= 5) iters.add("k=$k: [${fmt(a, 6)}, ${fmt(b, 6)}], midpoint ${fmt(m, 6)}, f = ${fmt(fm, 4)}")
                    if (fm.isNaN()) break
                    if (fa * fm <= 0) b = m else { a = m; fa = fm }
                }
                val rr = (a + b) / 2
                if (abs(f(rr)) < 1e-6) {
                    root = rr
                    if (firstIter == null) {
                        firstIter = iters
                        firstBracket = "f(${fmt(px, 6)}) = ${fmt(py, 4)} and f(${fmt(x, 6)}) = ${fmt(y, 4)} have opposite signs"
                    }
                }
            }
            val rt = root
            if (rt != null && roots.none { abs(it - rt) < 1e-6 }) roots.add(Ev.clean(rt))
            px = x; py = y
        }
        val fi = firstIter
        if (roots.isNotEmpty() && fi != null) {
            steps.add(Step("A sign change means a root lies in between", firstBracket))
            steps.add(Step("Bisection: repeatedly halve the interval that still contains the sign change (first 5 rounds)", fi.joinToString("\n")))
            steps.add(Step("Keep halving until the interval is tiny", roots.take(8).joinToString("\n") { "$v ≈ ${fmt(it, 8)}   (f = ${fmt(Ev.clean(f(it)), 3)})" }))
        } else {
            steps.add(Step("No sign change found – there is no real root in this range (a root where the curve only touches 0 can be missed)", ""))
        }
        return roots to Method("Numerical (bisection)", steps)
    }
}
