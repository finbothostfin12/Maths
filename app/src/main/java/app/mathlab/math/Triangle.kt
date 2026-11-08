package app.mathlab.math

import kotlin.math.*

object TriangleSolver {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun nf(v: Double) = fmt(Ev.clean(v), 6)
    private fun ang(v: Double) = fmt(Ev.clean(v), 6) + "°"
    private fun sinD(d: Double) = if (abs(d - 90.0) < 1e-12) 1.0 else sin(rad(d))
    private fun cosD(d: Double) = if (abs(d - 90.0) < 1e-12) 0.0 else cos(rad(d))
    private fun tanD(d: Double) = tan(rad(d))

    private val SN = listOf("a", "b", "c")
    private val AN = listOf("α", "β", "γ")

    // ───────────────────────── Right triangle ─────────────────────────
    // a, b = legs, c = hypotenuse, α opposite a, β opposite b, γ = 90°

    fun right(a0: Double?, b0: Double?, c0: Double?, al0: Double?, be0: Double?): Solution {
        val sideCount = listOfNotNull(a0, b0, c0).size
        val angCount = listOfNotNull(al0, be0).size
        if (sideCount == 0) throw MathError("Enter at least one side length")
        if (sideCount + angCount < 2) throw MathError("Enter at least two values (one of them a side)")
        listOfNotNull(a0, b0, c0).forEach { if (it <= 0) throw MathError("Side lengths must be positive") }
        listOfNotNull(al0, be0).forEach { if (it <= 0 || it >= 90) throw MathError("The acute angles must be between 0° and 90°") }
        if (al0 != null && be0 != null && abs(al0 + be0 - 90) > 1e-6) throw MathError("In a right triangle α + β must equal 90°")
        return if (sideCount >= 2) twoSides(a0, b0, c0) else sideAndAngle(a0, b0, c0, al0, be0)
    }

    private fun rightNotes(a: Double, b: Double, c: Double): List<String> = listOf(
        "Area = ½·a·b = ${nf(a * b / 2)}",
        "Perimeter = a + b + c = ${nf(a + b + c)}"
    )

    private fun rightAnswer(a: Double, b: Double, c: Double, al: Double, be: Double) =
        "a = ${nf(a)},  b = ${nf(b)},  c = ${nf(c)},  α = ${ang(al)},  β = ${ang(be)}"

    private fun twoSides(a0: Double?, b0: Double?, c0: Double?): Solution {
        val kind: Int
        val a: Double
        val b: Double
        val c: Double
        when {
            a0 != null && b0 != null -> {
                kind = 0; a = a0; b = b0; c = hypot(a0, b0)
                if (c0 != null && abs(c0 - c) > 1e-6 * c) throw MathError("a² + b² ≠ c² – these sides do not form a right triangle")
            }
            a0 != null && c0 != null -> {
                if (c0 <= a0) throw MathError("The hypotenuse must be longer than each leg")
                kind = 1; a = a0; c = c0; b = sqrt(c0 * c0 - a0 * a0)
            }
            else -> {
                val bb = b0!!; val cc = c0!!
                if (cc <= bb) throw MathError("The hypotenuse must be longer than each leg")
                kind = 2; b = bb; c = cc; a = sqrt(cc * cc - bb * bb)
            }
        }
        val al = deg(atan2(a, b))
        val be = 90 - al

        val pyth = when (kind) {
            0 -> Step("Hypotenuse from the Pythagorean theorem  c² = a² + b²", "c = √(${nf(a)}² + ${nf(b)}²) = √${nf(a * a + b * b)} = ${nf(c)}")
            1 -> Step("Other leg from the Pythagorean theorem  b² = c² − a²", "b = √(${nf(c)}² − ${nf(a)}²) = √${nf(c * c - a * a)} = ${nf(b)}")
            else -> Step("Other leg from the Pythagorean theorem  a² = c² − b²", "a = √(${nf(c)}² − ${nf(b)}²) = √${nf(c * c - b * b)} = ${nf(a)}")
        }
        val angStep = when (kind) {
            0 -> Step("Angle α from tan α = opposite / adjacent = a / b", "α = tan⁻¹(${nf(a)} / ${nf(b)}) = ${ang(al)}")
            1 -> Step("Angle α from sin α = opposite / hypotenuse = a / c", "α = sin⁻¹(${nf(a)} / ${nf(c)}) = ${ang(al)}")
            else -> Step("Angle α from cos α = adjacent / hypotenuse = b / c", "α = cos⁻¹(${nf(b)} / ${nf(c)}) = ${ang(al)}")
        }
        val betaStep = Step("The angles of a right triangle add up to 90°", "β = 90° − ${ang(al)} = ${ang(be)}")
        val m1 = Method("Pythagoras, then inverse trig", listOf(pyth, angStep, betaStep))

        val trigSide = when (kind) {
            0 -> Step("Hypotenuse from sin α = a / c  →  c = a / sin α", "c = ${nf(a)} / sin(${ang(al)}) = ${nf(c)}")
            1 -> Step("Other leg from cos α = b / c  →  b = c · cos α", "b = ${nf(c)} · cos(${ang(al)}) = ${nf(b)}")
            else -> Step("Other leg from sin α = a / c  →  a = c · sin α", "a = ${nf(c)} · sin(${ang(al)}) = ${nf(a)}")
        }
        val m2 = Method("Trigonometry first", listOf(angStep, trigSide, betaStep))
        return Solution("Right triangle", rightAnswer(a, b, c, al, be), listOf(m1, m2), rightNotes(a, b, c))
    }

    private fun ratioSteps(
        th: Double, thN: String, oN: String, jN: String, hN: String,
        o: Double, j: Double, h: Double, role: Char
    ): List<Step> {
        val s = ArrayList<Step>()
        s.add(Step("Relative to angle $thN = ${ang(th)}: opposite = $oN, adjacent = $jN, hypotenuse = $hN", ""))
        when (role) {
            'o' -> {
                s.add(Step("sin $thN = $oN / $hN  →  $hN = $oN / sin $thN", "$hN = ${nf(o)} / sin(${ang(th)}) = ${nf(h)}"))
                s.add(Step("tan $thN = $oN / $jN  →  $jN = $oN / tan $thN", "$jN = ${nf(o)} / tan(${ang(th)}) = ${nf(j)}"))
            }
            'j' -> {
                s.add(Step("cos $thN = $jN / $hN  →  $hN = $jN / cos $thN", "$hN = ${nf(j)} / cos(${ang(th)}) = ${nf(h)}"))
                s.add(Step("tan $thN = $oN / $jN  →  $oN = $jN · tan $thN", "$oN = ${nf(j)} · tan(${ang(th)}) = ${nf(o)}"))
            }
            else -> {
                s.add(Step("sin $thN = $oN / $hN  →  $oN = $hN · sin $thN", "$oN = ${nf(h)} · sin(${ang(th)}) = ${nf(o)}"))
                s.add(Step("cos $thN = $jN / $hN  →  $jN = $hN · cos $thN", "$jN = ${nf(h)} · cos(${ang(th)}) = ${nf(j)}"))
            }
        }
        return s
    }

    private fun sideAndAngle(a0: Double?, b0: Double?, c0: Double?, al0: Double?, be0: Double?): Solution {
        val al = al0 ?: (90 - be0!!)
        val be = 90 - al
        val role = when { a0 != null -> 'a'; b0 != null -> 'b'; else -> 'c' }
        val k = when (role) { 'a' -> a0!! / sinD(al); 'b' -> b0!! / sinD(be); else -> c0!! }
        val a = k * sinD(al); val b = k * sinD(be); val c = k

        val angSteps = ArrayList<Step>()
        if (al0 != null && be0 == null) angSteps.add(Step("Second acute angle", "β = 90° − ${ang(al)} = ${ang(be)}"))
        else if (be0 != null && al0 == null) angSteps.add(Step("Second acute angle", "α = 90° − ${ang(be)} = ${ang(al)}"))

        val m1 = Method("Using α (SOH-CAH-TOA)", angSteps + ratioSteps(al, "α", "a", "b", "c", a, b, c, when (role) { 'a' -> 'o'; 'b' -> 'j'; else -> 'h' }))
        val m2 = Method("Using β (SOH-CAH-TOA)", angSteps + ratioSteps(be, "β", "b", "a", "c", b, a, c, when (role) { 'a' -> 'j'; 'b' -> 'o'; else -> 'h' }))
        val ks = ArrayList<Step>(angSteps)
        ks.add(Step("Law of sines with γ = 90°", "a / sin α = b / sin β = c / sin 90° = c"))
        ks.add(Step("Common ratio from the known side", when (role) {
            'a' -> "K = a / sin α = ${nf(a)} / sin(${ang(al)}) = ${nf(k)}"
            'b' -> "K = b / sin β = ${nf(b)} / sin(${ang(be)}) = ${nf(k)}"
            else -> "K = c = ${nf(k)}"
        }))
        ks.add(Step("Every side is K · sin(opposite angle)", "a = K·sin α = ${nf(a)}\nb = K·sin β = ${nf(b)}\nc = K·sin 90° = ${nf(c)}"))
        val m3 = Method("Law of sines", ks)
        return Solution("Right triangle", rightAnswer(a, b, c, al, be), listOf(m1, m2, m3), rightNotes(a, b, c))
    }

    // ───────────────────────── General triangle ─────────────────────────

    fun general(sides: List<Double?>, angs: List<Double?>): Solution {
        val ns = sides.count { it != null }
        val na = angs.count { it != null }
        if (ns + na != 3) throw MathError("Enter exactly three known values (at least one must be a side)")
        if (ns == 0) throw MathError("At least one side is needed – angles alone do not fix the size")
        sides.filterNotNull().forEach { if (it <= 0) throw MathError("Side lengths must be positive") }
        angs.filterNotNull().forEach { if (it <= 0 || it >= 180) throw MathError("Angles must be between 0° and 180°") }
        val s = DoubleArray(3) { sides[it] ?: Double.NaN }
        val a = DoubleArray(3) { angs[it] ?: Double.NaN }
        return when {
            ns == 3 -> sss(s)
            ns == 2 -> {
                val m = s.indexOfFirst { it.isNaN() }
                if (!a[m].isNaN()) sas(s, a, m) else ssa(s, a, m)
            }
            else -> asa(s, a)
        }
    }

    private fun answer(s: DoubleArray, a: DoubleArray) =
        "a = ${nf(s[0])},  b = ${nf(s[1])},  c = ${nf(s[2])},  α = ${ang(a[0])},  β = ${ang(a[1])},  γ = ${ang(a[2])}"

    private fun finish(s: DoubleArray, a: DoubleArray): List<Step> {
        val p = s.sum() / 2
        val area = sqrt(max(0.0, p * (p - s[0]) * (p - s[1]) * (p - s[2])))
        return listOf(
            Step("Check: the angles add up to 180°", "α + β + γ = ${ang(a[0])} + ${ang(a[1])} + ${ang(a[2])} = ${ang(a.sum())}"),
            Step("Area with Heron's formula", "s = (a+b+c)/2 = ${nf(p)}\nArea = √(s(s−a)(s−b)(s−c)) = ${nf(area)}")
        )
    }

    private fun cosAngle(s: DoubleArray, i: Int): Double {
        val j = (i + 1) % 3; val k = (i + 2) % 3
        return deg(acos(((s[j] * s[j] + s[k] * s[k] - s[i] * s[i]) / (2 * s[j] * s[k])).coerceIn(-1.0, 1.0)))
    }

    private fun cosStep(s: DoubleArray, a: DoubleArray, i: Int): Step {
        val j = (i + 1) % 3; val k = (i + 2) % 3
        return Step("Law of cosines for ${AN[i]}",
            "cos ${AN[i]} = (${SN[j]}² + ${SN[k]}² − ${SN[i]}²) / (2·${SN[j]}·${SN[k]})\n" +
                    "= (${nf(s[j])}² + ${nf(s[k])}² − ${nf(s[i])}²) / (2·${nf(s[j])}·${nf(s[k])})\n→ ${AN[i]} = ${ang(a[i])}")
    }

    private fun sss(s: DoubleArray): Solution {
        if (s[0] + s[1] <= s[2] || s[0] + s[2] <= s[1] || s[1] + s[2] <= s[0])
            throw MathError("These sides cannot form a triangle (the two shorter sides must add up to more than the longest)")
        val a1 = DoubleArray(3) { cosAngle(s, it) }
        val st1 = ArrayList<Step>()
        for (i in 0..2) st1.add(cosStep(s, a1, i))
        st1.addAll(finish(s, a1))

        val big = s.indices.maxByOrNull { s[it] }!!
        val i2 = (big + 1) % 3; val k2 = (big + 2) % 3
        val a2 = DoubleArray(3)
        a2[big] = a1[big]
        a2[i2] = deg(asin((s[i2] * sinD(a2[big]) / s[big]).coerceIn(-1.0, 1.0)))
        a2[k2] = 180 - a2[big] - a2[i2]
        val st2 = ArrayList<Step>()
        st2.add(Step("Start with the largest side so its angle is found safely with the law of cosines", ""))
        st2.add(cosStep(s, a2, big))
        st2.add(Step("Law of sines for ${AN[i2]} (it must be acute, so no ambiguity)",
            "sin ${AN[i2]} / ${SN[i2]} = sin ${AN[big]} / ${SN[big]}\nsin ${AN[i2]} = ${nf(s[i2])}·sin(${ang(a2[big])}) / ${nf(s[big])}\n→ ${AN[i2]} = ${ang(a2[i2])}"))
        st2.add(Step("Third angle from the angle sum", "${AN[k2]} = 180° − ${ang(a2[big])} − ${ang(a2[i2])} = ${ang(a2[k2])}"))
        st2.addAll(finish(s, a2))
        return Solution("Triangle (SSS)", answer(s, a1), listOf(Method("Law of cosines for all angles", st1), Method("Law of cosines + law of sines", st2)))
    }

    private fun sas(s0: DoubleArray, a0: DoubleArray, m: Int): Solution {
        val i = (m + 1) % 3; val j = (m + 2) % 3
        val s = s0.copyOf()
        s[m] = sqrt(s[i] * s[i] + s[j] * s[j] - 2 * s[i] * s[j] * cosD(a0[m]))
        val thirdSide = Step("Law of cosines for the third side",
            "${SN[m]}² = ${SN[i]}² + ${SN[j]}² − 2·${SN[i]}·${SN[j]}·cos ${AN[m]}\n" +
                    "${SN[m]} = √(${nf(s0[i])}² + ${nf(s0[j])}² − 2·${nf(s0[i])}·${nf(s0[j])}·cos(${ang(a0[m])})) = ${nf(s[m])}")

        val a1 = a0.copyOf()
        a1[i] = cosAngle(s, i)
        a1[j] = cosAngle(s, j)
        val st1 = arrayListOf(thirdSide, cosStep(s, a1, i), cosStep(s, a1, j))
        st1.addAll(finish(s, a1))

        val small = if (s[i] < s[j]) i else j
        val other = if (small == i) j else i
        val a2 = a0.copyOf()
        a2[small] = deg(asin((s[small] * sinD(a0[m]) / s[m]).coerceIn(-1.0, 1.0)))
        a2[other] = 180 - a0[m] - a2[small]
        val st2 = arrayListOf(
            thirdSide,
            Step("Law of sines for the angle opposite the shorter known side (always acute)",
                "sin ${AN[small]} = ${SN[small]}·sin ${AN[m]} / ${SN[m]} = ${nf(s[small])}·sin(${ang(a0[m])}) / ${nf(s[m])}\n→ ${AN[small]} = ${ang(a2[small])}"),
            Step("Last angle from the angle sum", "${AN[other]} = 180° − ${ang(a0[m])} − ${ang(a2[small])} = ${ang(a2[other])}")
        )
        st2.addAll(finish(s, a2))
        return Solution("Triangle (SAS)", answer(s, a1), listOf(Method("Cosines, then cosines again", st1), Method("Cosines, then sines", st2)))
    }

    private fun asa(s0: DoubleArray, a0: DoubleArray): Solution {
        val t = a0.indexOfFirst { it.isNaN() }
        val known = a0.filter { !it.isNaN() }.sum()
        if (known >= 180) throw MathError("The two angles add up to 180° or more – no triangle")
        val a = a0.copyOf()
        a[t] = 180 - known
        val k = s0.indexOfFirst { !it.isNaN() }
        val ratio = s0[k] / sinD(a[k])
        val s = DoubleArray(3) { if (it == k) s0[k] else ratio * sinD(a[it]) }
        val angStep = Step("Third angle from the angle sum", "${AN[t]} = 180° − ${ang(known)} = ${ang(a[t])}")
        val ratioStep = Step("Law of sines: the ratio side / sin(opposite angle) is the same for all three",
            "${SN[k]} / sin ${AN[k]} = ${nf(s0[k])} / sin(${ang(a[k])}) = ${nf(ratio)}")
        val others = (0..2).filter { it != k }
        val st1 = arrayListOf(angStep, ratioStep)
        for (o in others) st1.add(Step("Side ${SN[o]}", "${SN[o]} = ${nf(ratio)} · sin(${ang(a[o])}) = ${nf(s[o])}"))
        st1.addAll(finish(s, a))

        val j = others[0]; val m = others[1]
        val st2 = arrayListOf(
            angStep, ratioStep,
            Step("Side ${SN[j]} with the law of sines", "${SN[j]} = ${nf(ratio)} · sin(${ang(a[j])}) = ${nf(s[j])}"),
            Step("Side ${SN[m]} with the law of cosines (angle ${AN[m]} lies between sides ${SN[k]} and ${SN[j]})",
                "${SN[m]} = √(${nf(s[k])}² + ${nf(s[j])}² − 2·${nf(s[k])}·${nf(s[j])}·cos(${ang(a[m])})) = ${nf(s[m])}")
        )
        st2.addAll(finish(s, a))
        return Solution("Triangle (ASA / AAS)", answer(s, a), listOf(Method("Law of sines for both sides", st1), Method("Law of sines + law of cosines", st2)))
    }

    private fun ssa(s0: DoubleArray, a0: DoubleArray, m: Int): Solution {
        val kk = a0.indexOfFirst { !it.isNaN() }
        val jj = (0..2).first { it != kk && it != m }
        val sj = s0[jj] * sinD(a0[kk]) / s0[kk]
        if (sj > 1 + 1e-9) throw MathError("No triangle exists: sin ${AN[jj]} would be ${nf(sj)}, which is larger than 1")
        val sjc = min(sj, 1.0)
        val t1 = deg(asin(sjc))
        val cands = if (abs(sjc - 1) < 1e-9) listOf(t1) else listOf(t1, 180 - t1)
        val valid = cands.filter { a0[kk] + it < 180 - 1e-9 }
        if (valid.isEmpty()) throw MathError("No triangle exists with these values")
        val methods = ArrayList<Method>()
        val solutions = ArrayList<Pair<DoubleArray, DoubleArray>>()
        valid.forEachIndexed { idx, th ->
            val a = a0.copyOf(); val s = s0.copyOf()
            a[jj] = th
            a[m] = 180 - a0[kk] - th
            s[m] = s0[kk] * sinD(a[m]) / sinD(a0[kk])
            val st = ArrayList<Step>()
            st.add(Step("Law of sines to find ${AN[jj]}",
                "sin ${AN[jj]} = ${SN[jj]}·sin ${AN[kk]} / ${SN[kk]} = ${nf(s0[jj])}·sin(${ang(a0[kk])}) / ${nf(s0[kk])} = ${nf(sjc)}"))
            st.add(Step(if (idx == 0) "Acute possibility" else "Obtuse possibility (180° − the acute value)",
                "${AN[jj]} = ${ang(th)}"))
            st.add(Step("Third angle from the angle sum", "${AN[m]} = 180° − ${ang(a0[kk])} − ${ang(th)} = ${ang(a[m])}"))
            st.add(Step("Law of sines for the last side",
                "${SN[m]} = ${SN[kk]}·sin ${AN[m]} / sin ${AN[kk]} = ${nf(s0[kk])}·sin(${ang(a[m])}) / sin(${ang(a0[kk])}) = ${nf(s[m])}"))
            st.addAll(finish(s, a))
            methods.add(Method(if (valid.size == 1) "Solution" else "Triangle ${idx + 1} (${AN[jj]} ${if (idx == 0) "acute" else "obtuse"})", st))
            solutions.add(s to a)
        }
        val ans = if (solutions.size == 1) answer(solutions[0].first, solutions[0].second)
        else solutions.mapIndexed { i, p -> "Triangle ${i + 1}:  " + answer(p.first, p.second) }.joinToString("\n")
        val notes = if (solutions.size > 1) listOf("This is the ambiguous SSA case: two different triangles fit the data. Pick the tab for each.") else emptyList()
        return Solution("Triangle (SSA)", ans, methods, notes)
    }
}
