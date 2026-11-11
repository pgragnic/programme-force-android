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

// ---------- matrices 3×3 (colonne-majeure, comme m3v) ----------
private fun mm(a: FloatArray, b: FloatArray): FloatArray { val r = FloatArray(9); for (c in 0..2) for (rr in 0..2) { var s = 0f; for (k in 0..2) s += a[k * 3 + rr] * b[c * 3 + k]; r[c * 3 + rr] = s }; return r }
private fun mT(a: FloatArray) = floatArrayOf(a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8])
private val M_ID = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
private fun nz(v: V3): V3 { val l = v.len(); return if (l > 1e-6f) V3(v.x / l, v.y / l, v.z / l) else v.copy() }
/** repère orthonormé en colonnes : u principal, v secondaire */
private fun frame(u0: V3, v0: V3): FloatArray { val u = nz(u0); var v = v0 - u * u.dot(v0); if (v.len() < 1e-4f) v = if (abs(u.x) < .9f) V3(1f, 0f, 0f) - u * u.x else V3(0f, 0f, 1f) - u * u.z; v = nz(v); val w = u.cross(v); return floatArrayOf(u.x, u.y, u.z, v.x, v.y, v.z, w.x, w.y, w.z) }
private fun axisAngle(a0: V3, th: Float): FloatArray {
    val a = nz(a0); val c = cos(th); val s = sin(th); val C = 1 - c; val x = a.x; val y = a.y; val z = a.z
    return floatArrayOf(c + x * x * C, y * x * C + z * s, z * x * C - y * s, x * y * C - z * s, c + y * y * C, z * y * C + x * s, x * z * C + y * s, y * z * C - x * s, c + z * z * C)
}
private fun rotBetween(a0: V3, b0: V3): FloatArray {
    val a = nz(a0); val b = nz(b0); val c = a.dot(b)
    if (c < -.99999f) return axisAngle(nz(a.cross(if (abs(a.x) < .9f) V3(1f, 0f, 0f) else V3(0f, 1f, 0f))), PI.toFloat())
    val v = a.cross(b); val k = 1f / (1f + c)
    // I + [v]x + [v]x² / (1 + c)
    return floatArrayOf(1 + k * (-v.y * v.y - v.z * v.z), v.z + k * v.x * v.y, -v.y + k * v.x * v.z,
        -v.z + k * v.x * v.y, 1 + k * (-v.x * v.x - v.z * v.z), v.x + k * v.y * v.z,
        v.y + k * v.x * v.z, -v.x + k * v.y * v.z, 1 + k * (-v.x * v.x - v.y * v.y))
}

class Bone(val name: String, val a0: V3, val b0: V3, val p: Int) {
    val u0 = nz(b0 - a0); val L0 = (b0 - a0).len()
    var A = M_ID.copyOf(); val t = V3()
}

class Group(val g: String, val rp: FloatArray, val rn: FloatArray, val idx: ShortArray, val bi: ByteArray, val bw: FloatArray, val lab: ByteArray?,
            /** par sommet : sens des fibres au repos (3 octets, 128 = 0) et marge à la frontière du muscle (0 = sillon, 255 = centre ou peau) */
            val fib: ByteArray? = null,
            /** par sommet : coordonnée des stries (cm), croissante perpendiculairement aux fibres */
            val ph: FloatArray? = null) {
    val pos = rp.copyOf(); val nrm = rn.copyOf(); val col = FloatArray(rp.size) { 1f }
    var key: String? = null
}

/**
 * Personnage MakeHuman (CC0) à 29 os : colonne en 3, cou, tête, clavicules, bras, avant-bras en deux (torsion), mains, doigts, pouces,
 * cuisses, jambes, pieds, orteils. Poses décrites en 3D (POSES3 du programme) : points du corps, direction des doigts, normale de paume,
 * fermeture des mains. Demande de Philippe (« Option 2 ») après plusieurs retours sur les mains et les mouvements de côté.
 */
class Body(ctx: Context) {
    val groups = ArrayList<Group>()
    val bones = ArrayList<Bone>()
    var labels: List<String> = emptyList()
    private val I = HashMap<String, Int>()
    private val palm0 = HashMap<String, V3>(); private val hlat0 = HashMap<String, V3>(); private val grip0 = HashMap<String, V3>()
    private var lastKey = ""; private var lastJ: HashMap<String, V3> = HashMap()

    init {
        val buf = ctx.assets.open("corps2.bin").readBytes()
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val hl = bb.getInt(0)
        val hdr = Json.parseToJsonElement(String(buf, 4, hl, Charsets.UTF_8)).jsonObject
        var o = 4 + hl
        fun al(n: Int) { o += n; o += (4 - o % 4) % 4 }
        fun v3(e: JsonElement) = e.jsonArray.map { it.jsonPrimitive.float }.let { V3(it[0], it[1], it[2]) }
        hdr["bones"]!!.jsonArray.forEachIndexed { i, b ->
            val bo = b.jsonObject; val n = bo["n"]!!.jsonPrimitive.content
            bones += Bone(n, v3(bo["a"]!!), v3(bo["b"]!!), bo["p"]!!.jsonPrimitive.int); I[n] = i
        }
        labels = hdr["labels"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        hdr["hand"]!!.jsonObject.forEach { (k, v) -> val h = v.jsonObject; palm0[k] = v3(h["n"]!!); hlat0[k] = v3(h["l"]!!); grip0[k] = v3(h["g"]!!) }
        hdr["groups"]!!.jsonArray.forEach { gj ->
            val g = gj.jsonObject; val nv = g["nv"]!!.jsonPrimitive.int; val ni = g["ni"]!!.jsonPrimitive.int
            val mn = g["mn"]!!.jsonArray.map { it.jsonPrimitive.float }; val mx = g["mx"]!!.jsonArray.map { it.jsonPrimitive.float }
            val pos = FloatArray(nv * 3) { i -> val a = i % 3; val q = bb.getShort(o + i * 2).toInt() and 0xFFFF; mn[a] + q / 65535f * (mx[a] - mn[a]) }; al(nv * 6)
            val idx = ShortArray(ni) { bb.getShort(o + it * 2) }; al(ni * 2)
            val bi = buf.copyOfRange(o, o + nv * 4); al(nv * 4)
            val bw = FloatArray(nv * 4) { (buf[o + it].toInt() and 0xFF) / 255f }; al(nv * 4)
            for (i in 0 until nv) { var s = 0f; for (j in 0..3) s += bw[i * 4 + j]; if (s > 0) for (j in 0..3) bw[i * 4 + j] /= s }
            val nr = FloatArray(nv * 3) { buf[o + it] / 127f }; al(nv * 3)
            val lab = if (g["lab"]?.jsonPrimitive?.int == 1) buf.copyOfRange(o, o + nv * 4).also { al(nv * 4) } else null
            val fib = if (g["fib"]?.jsonPrimitive?.int == 1) buf.copyOfRange(o, o + nv * 4).also { al(nv * 4) } else null
            val ph = if (g["ph"]?.jsonPrimitive?.int == 1) FloatArray(nv) { ((bb.getShort(o + it * 2).toInt() and 0xFFFF) - 32768) / 100f }.also { al(nv * 2) } else null
            groups += Group(g["g"]!!.jsonPrimitive.content, pos, nr, idx, bi, bw, lab, fib, ph)
        }
    }

    // ---------- poses ----------
    private fun P3(id: String) = Prog.raw["POSES3"]?.jsonObject?.get(id)?.jsonObject
    private fun oldFlag(id: String, k: String) = Prog.raw["POSES"]?.jsonObject?.get(id)?.jsonObject?.get(k)
    private fun flag(id: String, k: String) = P3(id)?.get(k)?.jsonPrimitive?.let { it.booleanOrNull ?: (it.doubleOrNull?.let { v -> v != 0.0 }) } == true
    fun isFront(id: String) = oldFlag(id, "front")?.jsonPrimitive?.booleanOrNull == true
    fun isFly(id: String) = oldFlag(id, "fly")?.jsonPrimitive?.let { it.booleanOrNull ?: (it.doubleOrNull?.let { v -> v != 0.0 }) } == true
    fun camOf(id: String) = P3(id)?.get("cam")?.jsonPrimitive?.floatOrNull
    fun isFree(id: String) = flag(id, "free")
    fun propOf(id: String): Pair<String, Float>? = P3(id)?.get("prop")?.jsonObject?.let { it["t"]!!.jsonPrimitive.content to (it["x"]?.jsonPrimitive?.floatOrNull ?: 100f) }
    /** hauteur du dessus de l'accessoire (assise, banc) quand la pose la précise (réglée sur le corps par tools/corps/fix4.py) */
    fun propTop(id: String): Float? = P3(id)?.get("prop")?.jsonObject?.get("y")?.jsonPrimitive?.floatOrNull
    fun poseTool(id: String) = P3(id)?.get("tool")?.jsonPrimitive?.contentOrNull
    fun gripOf(id: String): String? = null
    fun isStat(id: String) = flag(id, "stat")
    fun dbs(id: String): List<String> = P3(id)?.get("db")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    fun twoHand(id: String) = flag(id, "two")

    private val keyCache = HashMap<String, List<Pair<Float, Map<String, FloatArray>>>>()
    fun clearCache() = keyCache.clear()
    private fun keys(id: String) = keyCache.getOrPut(id) {
        (P3(id) ?: P3("calf"))!!["k"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            o["t"]!!.jsonPrimitive.float to o.filterKeys { it != "t" }.mapValues { (_, v) -> if (v is JsonArray) v.map { it.jsonPrimitive.float }.toFloatArray() else floatArrayOf(v.jsonPrimitive.float) }
        }
    }
    private fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float) = .5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t)
    /** pose interpolée (Catmull-Rom entre images clés) */
    fun at(id: String, k: Float): Map<String, FloatArray> {
        val ks = keys(id); if (ks.size == 1) return ks[0].second
        var i = 0; while (i < ks.size - 2 && k > ks[i + 1].first) i++
        val a = ks[i]; val b = ks[i + 1]; val z = ks[max(0, i - 1)]; val c = ks[min(ks.size - 1, i + 2)]
        val u = ((k - a.first) / ((b.first - a.first).takeIf { it != 0f } ?: 1f)).coerceIn(0f, 1f)
        return a.second.mapValues { (n, v) ->
            val r = FloatArray(v.size) { j ->
                fun g(q: Pair<Float, Map<String, FloatArray>>) = (q.second[n] ?: v)[j]
                if (ks.size == 2) g(a) + (g(b) - g(a)) * u else cr(g(z), g(a), g(b), g(c), u)
            }
            if (n[0] == 'U' && r.size == 3) { val l = sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]); if (l > 1e-6f) for (j in 0..2) r[j] /= l }
            r
        }
    }

    private fun setb(i: Int, R: FloatArray, P: V3) {
        val b = bones[i]; b.A = R
        if (b.p < 0) b.t.set(P) else { val pb = bones[b.p]; b.t.set(pb.t + m3v(pb.A, b.a0 - pb.a0)) }
    }
    private fun hp(i: Int): V3 { val b = bones[i]; val pb = bones[b.p]; return pb.t + m3v(pb.A, b.a0 - pb.a0) }
    fun boneEnd(b: Bone) = b.t + m3v(b.A, b.b0 - b.a0)

    /** Résout la pose : rotations et positions de chaque os. */
    fun solve(Q: Map<String, FloatArray>) {
        fun q(n: String) = Q[n]?.let { V3(it[0], it[1], it[2]) }
        val P = q("P")!!; val N = q("N")!!; val H = q("H")!!
        val S = q("S") ?: (P + (N - P) * .74f)
        val lat = q("lat") ?: V3(1f, 0f, 0f); val latc = q("latc") ?: lat
        val UP = V3(0f, 1f, 0f); val X = V3(1f, 0f, 0f)
        val F0 = mT(frame(UP, X))
        val Rt = mm(frame(S - P, lat), F0); val Rc = mm(frame(N - S, latc), F0); val Rn = mm(frame(H - N, latc), F0)
        setb(I["hips"]!!, Rt, P); setb(I["spine"]!!, Rt, P); setb(I["chest"]!!, Rc, P); setb(I["neck"]!!, Rn, P); setb(I["head"]!!, Rn, P)
        fun swing(i: Int, tgt: V3) { val b = bones[i]; val Rp = bones[b.p].A; val u = m3v(Rp, b.u0); setb(i, mm(rotBetween(u, tgt), Rp), P) }
        for ((k, s) in listOf("R" to "1", "L" to "2")) {
            val ci = I["clav$k"]!!
            setb(ci, Rc, P); q("Sh$s")?.let { swing(ci, it - hp(ci)) }
            val E = q("E$s")!!; val Wt = q("W$s")!!
            val ui = I["uarm$k"]!!; swing(ui, E - hp(ui))
            val fi = I["farm$k"]!!; val ti = I["twst$k"]!!
            val fdir = Wt - boneEnd(bones[ui])
            swing(fi, fdir); swing(ti, fdir)
            val hi = I["hand$k"]!!; val hb = bones[hi]
            val hd = q("F$s")?.let { nz(it - Wt) } ?: nz(fdir)
            val curN = m3v(bones[ti].A, palm0[k]!!)
            val U = q("U$s") ?: curN
            val Rh = mm(frame(hd, U), mT(frame(hb.u0, palm0[k]!!)))
            // torsion de l'avant-bras : part de la rotation de la main autour de son axe
            val ax = nz(fdir)
            var a1 = curN - ax * curN.dot(ax); var a2 = m3v(Rh, palm0[k]!!); a2 = a2 - ax * a2.dot(ax)
            if (a1.len() > 1e-3f && a2.len() > 1e-3f) {
                a1 = nz(a1); a2 = nz(a2); val th = atan2(a1.cross(a2).dot(ax), a1.dot(a2))
                setb(fi, mm(axisAngle(ax, th * .45f), bones[fi].A), P); setb(ti, mm(axisAngle(ax, th * .9f), bones[ti].A), P)
            }
            setb(hi, Rh, P)
            val c = Q["c$s"]?.get(0) ?: .3f
            val pn = m3v(Rh, palm0[k]!!); val axf = nz(m3v(Rh, hb.u0).cross(pn))
            val fa = I["finA$k"]!!; setb(fa, mm(axisAngle(axf, c * 1.35f), Rh), P)
            setb(I["finB$k"]!!, mm(axisAngle(axf, c * 1.6f), bones[fa].A), P)
            val tb = I["thmb$k"]!!; val td = m3v(Rh, bones[tb].u0); setb(tb, mm(axisAngle(nz(td.cross(pn)), c * .8f), Rh), P)
            // jambe
            val K = q("K$s")!!; val A = q("A$s")!!
            val th_ = I["thgh$k"]!!; swing(th_, K - hp(th_))
            val sh = I["shin$k"]!!; swing(sh, A - boneEnd(bones[th_]))
            val fo = I["foot$k"]!!
            val T = q("T$s"); if (T != null) swing(fo, T - A) else setb(fo, bones[sh].A, P)
            val to = I["toes$k"]!!; val TT = q("TT$s"); if (TT != null) swing(to, TT - boneEnd(bones[fo])) else setb(to, bones[fo].A, P)
        }
    }

    /** Articulations (même noms que l'ancien moteur) pour la caméra, les accessoires et les élastiques ; recalées au sol par skin(). */
    fun build(id: String, k: Double): HashMap<String, V3> {
        val key = "$id/$k"; if (key == lastKey) return lastJ
        solve(at(id, k.toFloat()))
        val J = HashMap<String, V3>()
        fun hd(n: String) = bones[I[n]!!].t.copy()
        J["Pc"] = hd("hips"); J["Nc"] = (hd("uarmR") + hd("uarmL")) * .5f; J["H"] = boneEnd(bones[I["neck"]!!]).lerp(boneEnd(bones[I["head"]!!]), .45f)
        J["SR"] = hd("uarmR"); J["SL"] = hd("uarmL"); J["HR"] = hd("thghR"); J["HL"] = hd("thghL")
        J["E1"] = hd("farmR"); J["E2"] = hd("farmL"); J["W1"] = hd("handR"); J["W2"] = hd("handL")
        J["K1"] = hd("shinR"); J["K2"] = hd("shinL"); J["A1"] = hd("footR"); J["A2"] = hd("footL")
        J["T1"] = boneEnd(bones[I["toesR"]!!]); J["T2"] = boneEnd(bones[I["toesL"]!!])
        lastJ = J; lastKey = ""; pendingKey = key
        return J
    }
    private var pendingKey = ""

    /** Déforme le maillage pour la pose (résolue par build), puis le pose au sol. */
    fun skin(J: Map<String, V3>, front: Boolean, lift: Float = 0f) {
        var my = 1e9f
        for (G in groups) {
            val rp = G.rp; val rn = G.rn; val P = G.pos; val N = G.nrm; val n = G.bw.size / 4
            for (i in 0 until n) {
                val j = i * 3; val x = rp[j]; val y = rp[j + 1]; val z = rp[j + 2]; val nx = rn[j]; val ny = rn[j + 1]; val nz_ = rn[j + 2]
                var px = 0f; var py = 0f; var pz = 0f; var ox = 0f; var oy = 0f; var oz = 0f
                for (q in 0..3) {
                    val w = G.bw[i * 4 + q]; if (w <= 0f) continue
                    val b = bones[G.bi[i * 4 + q].toInt() and 0xFF]; val a = b.A; val lx = x - b.a0.x; val ly = y - b.a0.y; val lz = z - b.a0.z
                    px += w * (a[0] * lx + a[3] * ly + a[6] * lz + b.t.x); py += w * (a[1] * lx + a[4] * ly + a[7] * lz + b.t.y); pz += w * (a[2] * lx + a[5] * ly + a[8] * lz + b.t.z)
                    ox += w * (a[0] * nx + a[3] * ny + a[6] * nz_); oy += w * (a[1] * nx + a[4] * ny + a[7] * nz_); oz += w * (a[2] * nx + a[5] * ny + a[8] * nz_)
                }
                P[j] = px; P[j + 1] = py; P[j + 2] = pz
                val l = sqrt(ox * ox + oy * oy + oz * oz).takeIf { it > 0 } ?: 1f
                N[j] = ox / l; N[j + 1] = oy / l; N[j + 2] = oz / l
                if (py < my) my = py
            }
        }
        val dy = .7f + lift - my
        bones.forEach { it.t.y += dy }
        for (G in groups) { val P = G.pos; var j = 1; while (j < P.size) { P[j] += dy; j += 3 } }
        J.values.forEach { it.y += dy }
        lastKey = pendingKey
    }

    /** Point de prise dans la main ('R' ou 'L') : milieu de la paume. */
    fun grip(sd: String): V3 { val b = bones[I["hand$sd"]!!]; return b.t + m3v(b.A, grip0[sd]!!) }
    /** Axe des jointures de la main : direction de la poignée d'un haltère tenu. */
    fun handAxis(sd: String): V3 = nz(m3v(bones[I["hand$sd"]!!].A, hlat0[sd]!!))
    /** Direction de la main (poignet vers doigts). */
    fun handDir(sd: String): V3 { val b = bones[I["hand$sd"]!!]; return nz(m3v(b.A, b.u0)) }
    fun palm(sd: String): V3 = nz(m3v(bones[I["hand$sd"]!!].A, palm0[sd]!!))

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

}
