package fr.pgragnic.force.gl

import android.content.Context
import fr.pgragnic.force.Prog
import fr.pgragnic.force.ui.Poses
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

// ---------- petite algèbre 3D ----------
class V3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun set(a: Float, b: Float, c: Float) = apply { x = a; y = b; z = c }
    fun set(o: V3) = apply { x = o.x; y = o.y; z = o.z }
    fun copy() = V3(x, y, z)
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun len() = sqrt(x * x + y * y + z * z)
    fun norm(): V3 { val l = len(); if (l > 1e-9f) { x /= l; y /= l; z /= l }; return this }
    fun lerp(o: V3, t: Float) = V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
}

class Q4(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    fun mul(b: Q4): Q4 = Q4(
        w * b.x + x * b.w + y * b.z - z * b.y, w * b.y - x * b.z + y * b.w + z * b.x,
        w * b.z + x * b.y - y * b.x + z * b.w, w * b.w - x * b.x - y * b.y - z * b.z)
    fun rot(v: V3): V3 { // v' = q v q*
        val ix = w * v.x + y * v.z - z * v.y; val iy = w * v.y + z * v.x - x * v.z
        val iz = w * v.z + x * v.y - y * v.x; val iw = -x * v.x - y * v.y - z * v.z
        return V3(ix * w + iw * -x + iy * -z - iz * -y, iy * w + iw * -y + iz * -x - ix * -z, iz * w + iw * -z + ix * -y - iy * -x)
    }
    /** matrice 3×3 colonne-majeure */
    fun mat(): FloatArray {
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val xy = x * y2; val xz = x * z2; val yy = y * y2; val yz = y * z2; val zz = z * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2
        return floatArrayOf(1 - (yy + zz), xy + wz, xz - wy, xy - wz, 1 - (xx + zz), yz + wx, xz + wy, yz - wx, 1 - (xx + yy))
    }
    companion object {
        fun fromUnit(a: V3, b: V3): Q4 { // rotation de a vers b (unitaires)
            var r = a.dot(b) + 1f
            val q: Q4
            if (r < 1e-6f) { r = 0f; q = if (abs(a.x) > abs(a.z)) Q4(-a.y, a.x, 0f, r) else Q4(0f, -a.z, a.y, r) }
            else { val c = a.cross(b); q = Q4(c.x, c.y, c.z, r) }
            val l = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w); q.x /= l; q.y /= l; q.z /= l; q.w /= l; return q
        }
        fun fromMat(m: FloatArray): Q4 { // m colonne-majeure 3×3
            val m11 = m[0]; val m12 = m[3]; val m13 = m[6]; val m21 = m[1]; val m22 = m[4]; val m23 = m[7]; val m31 = m[2]; val m32 = m[5]; val m33 = m[8]
            val tr = m11 + m22 + m33
            return if (tr > 0) { val s = 0.5f / sqrt(tr + 1f); Q4((m32 - m23) * s, (m13 - m31) * s, (m21 - m12) * s, 0.25f / s) }
            else if (m11 > m22 && m11 > m33) { val s = 2f * sqrt(1f + m11 - m22 - m33); Q4(0.25f * s, (m12 + m21) / s, (m13 + m31) / s, (m32 - m23) / s) }
            else if (m22 > m33) { val s = 2f * sqrt(1f + m22 - m11 - m33); Q4((m12 + m21) / s, 0.25f * s, (m23 + m32) / s, (m13 - m31) / s) }
            else { val s = 2f * sqrt(1f + m33 - m11 - m22); Q4((m13 + m31) / s, (m23 + m32) / s, 0.25f * s, (m21 - m12) / s) }
        }
    }
}

fun m3v(m: FloatArray, v: V3) = V3(m[0] * v.x + m[3] * v.y + m[6] * v.z, m[1] * v.x + m[4] * v.y + m[7] * v.z, m[2] * v.x + m[5] * v.y + m[8] * v.z)

class Bone(val a0: V3, val u0: V3, val L0: Float, val p: Int, val ka: String, val kb: String) {
    var A = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f); val t = V3(); var Q = Q4()
}

class Group(val g: String, val rp: FloatArray, val rn: FloatArray, val idx: ShortArray, val bi: ByteArray, val w: ByteArray, val lab: ByteArray?) {
    val pos = rp.copyOf(); val nrm = rn.copyOf(); val col = FloatArray(rp.size) { 1f }
    /** main : côté (0 aucun, 1 droite, 2 gauche) et poids (0 au poignet → 1 dans la main), calculés au chargement */
    var hs: ByteArray? = null; var hw: FloatArray? = null
    var key: String? = null
}

/** Personnage MakeHuman : chargement de corps.bin, poses (portées de la page web) et déformation. */
class Body(ctx: Context) {
    val groups = ArrayList<Group>()
    val bones = ArrayList<Bone>()
    var labels: List<String> = emptyList()
    private var grip: Map<String, FloatArray> = emptyMap()
    private var shX = 16.7f; private var hipX = 8.8f
    private val BMAP = mapOf("Pc" to "Pc", "Nc" to "Nc", "H" to "H", "SR" to "SR", "ER" to "E1", "WR" to "W1", "SL" to "SL", "EL" to "E2", "WL" to "W2",
        "HR" to "HR", "KR" to "K1", "AR" to "A1", "TR" to "T1", "HL" to "HL", "KL" to "K2", "AL" to "A2", "TL" to "T2")
    /** poses tenues à deux mains : liste de base + liste TWOHAND du programme (swing, halo, squat sumo…) */
    private val TWOHAND: Set<String> get() = setOf("pull", "ohtri", "squat", "crunch", "dbugw") + (Prog.raw["TWOHAND"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())
    private val FLIPX: Set<String> get() = Prog.raw["FLIPX"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()

    init {
        val buf = ctx.assets.open("corps.bin").readBytes()
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val hl = bb.getInt(0)
        val hdr = Json.parseToJsonElement(String(buf, 4, hl, Charsets.UTF_8)).jsonObject
        var o = 4 + hl
        fun al(n: Int) { o += n; o += (4 - o % 4) % 4 }
        val joints = hdr["joints"]!!.jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.float } }
        fun key(p: List<Float>) = joints.entries.first { it.value == p }.key
        hdr["bones"]!!.jsonArray.forEach { b ->
            val bo = b.jsonObject
            val a = bo["a"]!!.jsonArray.map { it.jsonPrimitive.float }; val c = bo["b"]!!.jsonArray.map { it.jsonPrimitive.float }
            val av = V3(a[0], a[1], a[2]); val u = V3(c[0] - a[0], c[1] - a[1], c[2] - a[2]); val L = u.len(); u.norm()
            bones += Bone(av, u, L, bo["p"]!!.jsonPrimitive.int, key(a), key(c))
        }
        labels = hdr["labels"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        grip = hdr["grip"]?.jsonObject?.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.float }.toFloatArray() } ?: emptyMap()
        shX = joints["SR"]!![0]; hipX = joints["HR"]!![0]
        hdr["groups"]!!.jsonArray.forEach { gj ->
            val g = gj.jsonObject; val nv = g["nv"]!!.jsonPrimitive.int; val ni = g["ni"]!!.jsonPrimitive.int
            val mn = g["mn"]!!.jsonArray.map { it.jsonPrimitive.float }; val mx = g["mx"]!!.jsonArray.map { it.jsonPrimitive.float }
            val pos = FloatArray(nv * 3) { i -> val a = i % 3; val q = bb.getShort(o + i * 2).toInt() and 0xFFFF; mn[a] + q / 65535f * (mx[a] - mn[a]) }; al(nv * 6)
            val idx = ShortArray(ni) { bb.getShort(o + it * 2) }; al(ni * 2)
            val bi = buf.copyOfRange(o, o + nv * 2); al(nv * 2)
            val w = buf.copyOfRange(o, o + nv); al(nv)
            val nr = if (g["nrm"] != null) FloatArray(nv * 3) { buf[o + it] / 127f }.also { al(nv * 3) } else FloatArray(nv * 3)
            val lab = if (g["lab"] != null) buf.copyOfRange(o, o + nv * 4).also { al(nv * 4) } else null
            groups += Group(g["g"]!!.jsonPrimitive.content, pos, nr, idx, bi, w, lab)
        }
        // la main n'a pas d'os : les sommets au-delà du poignet (le long de l'avant-bras) forment une « main » qu'on peut tourner
        for (G in groups) {
            val n = G.w.size; val hs = ByteArray(n); val hw = FloatArray(n); var any = false
            for (i in 0 until n) for ((sd, bk) in listOf(1 to 3, 2 to 5)) {
                val b = bones[bk]; val v = V3(G.rp[i * 3], G.rp[i * 3 + 1], G.rp[i * 3 + 2])
                val t = (v - b.a0).dot(b.u0) / b.L0
                val near = (v - (b.a0 + b.u0 * ((v - b.a0).dot(b.u0)))).len() < 9f
                if (t > .97f && near) { hs[i] = sd.toByte(); hw[i] = ((t - .97f) / .1f).coerceIn(0f, 1f); any = true }
            }
            if (any) { G.hs = hs; G.hw = hw }
        }
    }

    // ---------- poses ----------
    private fun pFlag(id: String, k: String) = Prog.raw["POSES"]!!.jsonObject[id]?.jsonObject?.get(k)
    /** drapeau de pose : true ou 1 (le programme utilise les deux) */
    private fun flag(id: String, k: String) = pFlag(id, k)?.jsonPrimitive?.let { it.booleanOrNull ?: (it.doubleOrNull?.let { v -> v != 0.0 }) } == true
    fun isFront(id: String) = flag(id, "front")
    fun isFly(id: String) = flag(id, "fly")
    fun camOf(id: String) = pFlag(id, "cam")?.jsonPrimitive?.floatOrNull
    /** Pose où les pieds ne touchent pas tous le sol (montée sur chaise…) : le plus bas des pieds donne la hauteur. */
    fun isFree(id: String) = flag(id, "free")
    /** Accessoire de la pose : chaise, mur ou banc, et sa position (repère 2D de la pose). */
    fun propOf(id: String): Pair<String, Float>? = pFlag(id, "prop")?.jsonObject?.let { it["t"]!!.jsonPrimitive.content to (it["x"]?.jsonPrimitive?.floatOrNull ?: 100f) }
    /** Outil dessiné par la pose elle-même (échauffement au bâton…). */
    fun poseTool(id: String) = pFlag(id, "tool")?.jsonPrimitive?.contentOrNull
    /** Prise de l'haltère : "n" = neutre (pouces vers le haut, poignée perpendiculaire à l'avant-bras, dans l'axe avant-arrière) ; absent = paumes vers le haut ou le bas (poignée en travers, sur le côté). */
    fun gripOf(id: String) = pFlag(id, "grip")?.jsonPrimitive?.contentOrNull
    fun isStat(id: String) = flag(id, "stat")

    fun build(id: String, k: Double): HashMap<String, V3> {
        val p = Poses.at(id, k) ?: Poses.at("calf", 0.0)!!
        val front = isFront(id); val two = id in TWOHAND
        fun sv(q: DoubleArray, lat: Float = 0f) = if (front) V3((q[0] - 100).toFloat(), (180 - q[1]).toFloat(), 0f) else V3(lat, (180 - q[1]).toFloat(), (q[0] - 100).toFloat())
        fun pt(n: String) = p[n] ?: p["N"]!!
        val J = HashMap<String, V3>()
        J["H"] = sv(pt("H")); J["Nc"] = sv(pt("N")); J["Pc"] = sv(pt("P"))
        J["SR"] = J["Nc"]!! + V3(shX, -1.4f, 0f); J["SL"] = J["Nc"]!! + V3(-shX, -1.4f, 0f)
        J["HR"] = J["Pc"]!! + V3(hipX, 0f, 0f); J["HL"] = J["Pc"]!! + V3(-hipX, 0f, 0f)
        val lat = pFlag(id, "lat")?.jsonArray?.map { r -> r.jsonArray.map { it.jsonPrimitive.float } }
        val LT = lat?.let { floatArrayOf(it[0][0] + (it[1][0] - it[0][0]) * k.toFloat(), it[0][1] + (it[1][1] - it[0][1]) * k.toFloat()) }
        val aw = if (LT != null) shX + LT[1] else if (two) 5f else shX + 1.5f
        val ae = if (LT != null) shX + LT[0] else if (two) 10f else shX + 2.5f
        if (front) {
            var e1 = sv(pt("E1")); var w1 = sv(pt("W1")); var e2 = sv(pt("E2")); var w2 = sv(pt("W2"))
            val ds = shX - 11
            listOf(e1, w1, e2, w2).forEach { it.x += sign(it.x) * ds }
            if (e1.x > 0) { val te = e1; e1 = e2; e2 = te; val tw = w1; w1 = w2; w2 = tw }
            J["E1"] = e2; J["W1"] = w2; J["E2"] = e1; J["W2"] = w1
            var k1 = sv(pt("K2")); var a1 = sv(pt("A2")); var k2 = sv(pt("K1")); var a2 = sv(pt("A1"))
            if (k1.x < 0) { val tk = k1; k1 = k2; k2 = tk; val ta = a1; a1 = a2; a2 = ta }
            val dl = hipX - 6
            listOf(k1, a1, k2, a2).forEach { it.x += sign(it.x) * dl }
            J["K1"] = k1; J["A1"] = a1; J["K2"] = k2; J["A2"] = a2
            J["T1"] = a1 + V3(0f, 0f, 9f); J["T2"] = a2 + V3(0f, 0f, 9f)
        } else {
            J["E1"] = sv(pt("E1"), ae); J["W1"] = sv(pt("W1"), aw); J["E2"] = sv(pt("E2"), -ae); J["W2"] = sv(pt("W2"), -aw)
            // écart des jambes sur les côtés (squat sumo…) : drapeau legw = [genoux, chevilles]
            val lw = pFlag(id, "legw")?.jsonArray?.map { it.jsonPrimitive.float }
            val LG = hipX + .3f + (lw?.get(0) ?: 0f); val LA = hipX + .6f + (lw?.get(1) ?: 0f)
            J["K1"] = sv(pt("K1"), LG); J["A1"] = sv(pt("A1"), LA); J["K2"] = sv(pt("K2"), -LG); J["A2"] = sv(pt("A2"), -LA)
            val A1 = pt("A1"); val A2 = pt("A2")
            J["T1"] = sv(doubleArrayOf(A1[0] + 14.5, A1[1] + 4), LA); J["T2"] = sv(doubleArrayOf(A2[0] + 14.5, A2[1] + 4), -LA)
            // orteils (T1/T2) et bouts des doigts (F1/F2) donnés par la pose : pieds à plat derrière à genoux, paumes à plat au sol ou au mur
            p["T1"]?.let { J["T1"] = sv(it, LA) }; p["T2"]?.let { J["T2"] = sv(it, -LA) }
            p["F1"]?.let { J["F1"] = sv(it, aw) }; p["F2"]?.let { J["F2"] = sv(it, -aw) }
            // milieu du dos (S) : courbure de la colonne, dos rond ou creusé (retour de Philippe sur le chat-vache : « Le dos ne bouge pas du tout ? »)
            p["S"]?.let { J["S"] = sv(it) }
            // talons levés (mollets) : drapeau heel = [début, fin] en unités de pose ; les orteils restent au sol, la cheville monte
            // (retour de Philippe sur les mollets sur une jambe : « Rien ne bouge »)
            pFlag(id, "heel")?.jsonArray?.map { it.jsonPrimitive.float }?.let { h ->
                val lift = h[0] + (h[1] - h[0]) * k.toFloat()
                listOf("A1", "A2").forEach { n -> if (pt(n)[1] >= 176) J[n]!!.y += lift }
            }
            if (isFly(id)) {
                val th = k * 1.35
                listOf(Triple(1f, "SR", "E1" to "W1"), Triple(-1f, "SL", "E2" to "W2")).forEach { (sd, S_, ew) ->
                    val d = V3((sin(th) * sd).toFloat(), (-cos(th)).toFloat(), 0f)
                    J[ew.first] = J[S_]!! + d * 24f + V3(0f, 0f, 2f); J[ew.second] = J[S_]!! + d * 45f
                }
            }
        }
        // buste penché sur le côté (étirement latéral) : drapeau side = angle en degrés, rotation du haut du corps autour du bassin
        pFlag(id, "side")?.jsonPrimitive?.floatOrNull?.let { deg ->
            val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val sn = sin(r).toFloat(); val o = J["Pc"]!!
            listOf("H", "Nc", "SR", "SL", "E1", "W1", "E2", "W2").forEach { n -> J[n]?.let { v -> val dx = v.x - o.x; val dy = v.y - o.y; v.x = o.x + dx * c - dy * sn; v.y = o.y + dx * sn + dy * c } }
        }
        if (id in FLIPX) J.values.forEach { it.x = -it.x }
        return J
    }

    // ---------- squelette ----------
    fun poseBones(J: Map<String, V3>, front: Boolean) {
        val LAT = V3(1f, 0f, 0f); val FR = V3(0f, 0f, 1f)
        fun fr(u: V3): FloatArray {
            var f = if (front) FR.copy() else LAT.cross(u); if (f.len() < .2f) f = FR.copy(); f.norm()
            val x = u.cross(f).norm(); f = x.cross(u).norm()
            return floatArrayOf(x.x, x.y, x.z, u.x, u.y, u.z, f.x, f.y, f.z)
        }
        bones.forEachIndexed { i, b ->
            val ra = J[BMAP[b.ka]]!!; val rb = J[BMAP[b.kb]]!!
            val u1 = (rb - ra).norm()
            val a1 = if (b.p < 0) ra.copy() else m3v(bones[b.p].A, b.a0) + bones[b.p].t
            if (b.p < 0) {
                val R0 = fr(b.u0); val R1 = fr(u1)
                // M = R1 * R0^T
                val M = FloatArray(9)
                for (c in 0..2) for (r in 0..2) { var s = 0f; for (kk in 0..2) s += R1[kk * 3 + r] * R0[kk * 3 + c]; M[c * 3 + r] = s }
                b.Q = Q4.fromMat(M)
            } else {
                val pq = bones[b.p].Q
                val v = pq.rot(b.u0)
                var tgt = u1
                if (i == 1) { val td = (J["Nc"]!! - J["Pc"]!!).norm(); tgt = Q4.fromUnit(td, u1).rot(v) }
                b.Q = Q4.fromUnit(v, tgt.norm()).mul(pq)
            }
            b.A = b.Q.mat()
            val ta = a1 - m3v(b.A, b.a0); b.t.set(ta)
        }
    }

    /** Déforme le maillage pour la pose, puis le pose au sol. */
    fun skin(J: Map<String, V3>, front: Boolean, lift: Float = 0f) {
        poseBones(J, front)
        // rotation de chaque main vers le bout des doigts demandé (F1/F2), autour du poignet
        val hand = arrayOfNulls<Pair<Q4, V3>>(3)
        listOf(Triple(1, 3, "F1" to "W1"), Triple(2, 5, "F2" to "W2")).forEach { (sd, bk, fw) ->
            val F = J[fw.first] ?: return@forEach; val b = bones[bk]
            val f = m3v(b.A, b.u0).norm(); val h = (F - J[fw.second]!!).norm()
            hand[sd] = Q4.fromUnit(f, h) to boneEnd(b)
        }
        // courbure du dos : déplacement du buste en sinus entre le bassin et le cou, vers le point S de la pose
        val bend: V3? = J["S"]?.let { S -> val m = J["Pc"]!!.lerp(J["Nc"]!!, .5f); val sp = (J["Nc"]!! - J["Pc"]!!).norm(); val d = S - m; d - sp * d.dot(sp) }
        val sb = bones[0]
        var my = 1e9f
        for (G in groups) {
            val rp = G.rp; val rn = G.rn; val P = G.pos; val N = G.nrm; val n = G.w.size
            for (i in 0 until n) {
                val j = i * 3; val x = rp[j]; val y = rp[j + 1]; val z = rp[j + 2]
                val b1 = bones[G.bi[i * 2].toInt() and 0xFF]; val b2 = bones[G.bi[i * 2 + 1].toInt() and 0xFF]
                val w1 = (G.w[i].toInt() and 0xFF) / 255f; val w2 = 1 - w1
                val a = b1.A; val c = b2.A; val t1 = b1.t; val t2 = b2.t
                P[j] = w1 * (a[0] * x + a[3] * y + a[6] * z + t1.x) + w2 * (c[0] * x + c[3] * y + c[6] * z + t2.x)
                P[j + 1] = w1 * (a[1] * x + a[4] * y + a[7] * z + t1.y) + w2 * (c[1] * x + c[4] * y + c[7] * z + t2.y)
                P[j + 2] = w1 * (a[2] * x + a[5] * y + a[8] * z + t1.z) + w2 * (c[2] * x + c[5] * y + c[8] * z + t2.z)
                val nx = rn[j]; val ny = rn[j + 1]; val nz = rn[j + 2]
                val ox = w1 * (a[0] * nx + a[3] * ny + a[6] * nz) + w2 * (c[0] * nx + c[3] * ny + c[6] * nz)
                val oy = w1 * (a[1] * nx + a[4] * ny + a[7] * nz) + w2 * (c[1] * nx + c[4] * ny + c[7] * nz)
                val oz = w1 * (a[2] * nx + a[5] * ny + a[8] * nz) + w2 * (c[2] * nx + c[5] * ny + c[8] * nz)
                val l = sqrt(ox * ox + oy * oy + oz * oz).takeIf { it > 0 } ?: 1f
                N[j] = ox / l; N[j + 1] = oy / l; N[j + 2] = oz / l
                if (bend != null) {
                    val bw = (if ((G.bi[i * 2].toInt() and 0xFF) == 0) w1 else 0f) + (if ((G.bi[i * 2 + 1].toInt() and 0xFF) == 0) w2 else 0f)
                    val tt = (V3(x, y, z) - sb.a0).dot(sb.u0) / sb.L0
                    if (bw > 0f && tt > 0f && tt < 1f) { val f = sin(PI.toFloat() * tt) * bw; P[j] += bend.x * f; P[j + 1] += bend.y * f; P[j + 2] += bend.z * f }
                }
                val hs = G.hs
                if (hs != null && hs[i] > 0) hand[hs[i].toInt()]?.let { (q, W) ->
                    val t = G.hw!![i]; val r = q.rot(V3(P[j] - W.x, P[j + 1] - W.y, P[j + 2] - W.z)); val rn = q.rot(V3(N[j], N[j + 1], N[j + 2]))
                    P[j] += (W.x + r.x - P[j]) * t; P[j + 1] += (W.y + r.y - P[j + 1]) * t; P[j + 2] += (W.z + r.z - P[j + 2]) * t
                    N[j] += (rn.x - N[j]) * t; N[j + 1] += (rn.y - N[j + 1]) * t; N[j + 2] += (rn.z - N[j + 2]) * t
                }
                if (P[j + 1] < my) my = P[j + 1]
            }
        }
        val dy = .7f + lift - my
        bones.forEach { it.t.y += dy }
        for (G in groups) { val P = G.pos; var j = 1; while (j < P.size) { P[j] += dy; j += 3 } }
    }

    /** Point de prise de l'haltère dans le poing ('R' = W1, 'L' = W2). */
    fun grip(sd: String): V3 {
        val b = bones[if (sd == "R") 3 else 5]; val u = b.u0
        val W0 = b.a0 + u * b.L0
        val fr = (V3(0f, 0f, 1f) - u * u.z).norm()
        val g = grip[sd] ?: floatArrayOf(6.8f, .9f, 0f)
        val G = W0 + u * g[0] + fr * g[1] + u.cross(fr) * g[2]
        return m3v(b.A, G) + b.t
    }

    fun boneEnd(b: Bone) = m3v(b.A, b.a0 + b.u0 * b.L0) + b.t

    /** Couleurs par sommet : muscles travaillés (hl) ou couleurs de récupération (colors). */
    fun colorize(base: Int, acc: Int, hl: String?, colors: Map<String, Int>?): Boolean {
        val key = "$base/$acc/$hl/${colors?.hashCode()}"
        val ext = mapOf("Avant-bras" to listOf("Biceps" to .55, "Triceps" to .35), "Jambe" to listOf("Mollets" to .45))
        val mp = hl?.let { Prog.MAP[it] } ?: emptyMap()
        fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)
        val bc = rgb(base); val ac = rgb(acc)
        fun mix(a: FloatArray, b: FloatArray, t: Float) = floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)
        val cols = labels.map { n ->
            if (n == "Peau") bc
            else if (colors != null) colors[n]?.let { mix(bc, rgb(it), .8f) } ?: bc
            else { var w = mp[n] ?: 0.0; ext[n]?.forEach { (m, f) -> w = max(w, (mp[m] ?: 0.0) * f) }; mix(bc, ac, min(1.0, w * 1.05).toFloat()) }
        }
        if (groups.all { it.key == key }) return false
        for (G in groups) {
            G.key = key
            val L = G.lab
            if (L == null) { val c = if (G.g == "Yeux") floatArrayOf(.23f, .25f, .27f) else bc; for (i in 0 until G.col.size / 3) { G.col[i * 3] = c[0]; G.col[i * 3 + 1] = c[1]; G.col[i * 3 + 2] = c[2] }; continue }
            for (i in 0 until L.size / 4) {
                val a = cols[L[4 * i].toInt() and 0xFF]; val b = cols[L[4 * i + 1].toInt() and 0xFF]
                val w = (L[4 * i + 2].toInt() and 0xFF) / 255f; val sh = (L[4 * i + 3].toInt() and 0xFF) / 255f * 1.06f
                for (c in 0..2) G.col[i * 3 + c] = (a[c] * w + b[c] * (1 - w)) * sh
            }
        }
        return true
    }

    fun dbs(id: String) = Poses.dbs(id)
    fun twoHand(id: String) = id in TWOHAND
}
