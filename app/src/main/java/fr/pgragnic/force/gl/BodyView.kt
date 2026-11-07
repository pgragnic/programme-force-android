package fr.pgragnic.force.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import fr.pgragnic.force.Prog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** Ce que la vue 3D doit afficher. */
data class BodyState(
    val pose: String? = null, val k: Double = 0.0, val mirror: Boolean = false, val ghost: Boolean = false, val nodb: Boolean = false,
    val hl: String? = null, val colors: Map<String, Int>? = null, val az: Float? = null, val map: Boolean = false,
    val bg: Int = 0x0C0F12, val ground: Int = 0x181C21, val mat: Int = 0x21262C, val db: Int = 0x2EC089,
    val base: Int = 0xE8DAD5, val acc: Int = 0xE0453A,
    /** exercice affiché : donne l'outil (haltère, kettlebell, élastique) */
    val ex: String? = null, val band: Int = 0xE3A33E, val prop: Int = 0x6B7680,
)

private fun fb(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

/** Maillage simple (sol, tapis, haltères) : positions + normales entrelacées. */
private class Mesh(data: FloatArray, idx: ShortArray) {
    val n = idx.size; val vbo = IntArray(1); val ibo = IntArray(1)
    init {
        glGenBuffers(1, vbo, 0); glBindBuffer(GL_ARRAY_BUFFER, vbo[0])
        glBufferData(GL_ARRAY_BUFFER, data.size * 4, fb(data.size).put(data).position(0), GL_STATIC_DRAW)
        glGenBuffers(1, ibo, 0); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        val sb = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(idx).position(0)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, sb, GL_STATIC_DRAW)
    }
    fun draw() {
        glBindBuffer(GL_ARRAY_BUFFER, vbo[0])
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 24, 0)
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, false, 24, 12)
        glDisableVertexAttribArray(2); glVertexAttrib3f(2, 1f, 1f, 1f)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo[0]); glDrawElements(GL_TRIANGLES, n, GL_UNSIGNED_SHORT, 0)
    }
    companion object {
        /** Cylindre d'axe x, longueur 1, rayon 1, centré. */
        fun cylinder(seg: Int = 28): Mesh {
            val d = ArrayList<Float>(); val ix = ArrayList<Short>()
            fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) { d += listOf(x, y, z, nx, ny, nz) }
            for (i in 0..seg) { val a = 2 * PI.toFloat() * i / seg; val c = cos(a); val s = sin(a); v(-.5f, c, s, 0f, c, s); v(.5f, c, s, 0f, c, s) }
            for (i in 0 until seg) { val a = (i * 2).toShort(); ix += listOf(a, (a + 2).toShort(), (a + 1).toShort(), (a + 1).toShort(), (a + 2).toShort(), (a + 3).toShort()) }
            listOf(-.5f, .5f).forEach { x ->
                val c0 = (d.size / 6).toShort(); v(x, 0f, 0f, sign(x), 0f, 0f)
                for (i in 0..seg) { val a = 2 * PI.toFloat() * i / seg; v(x, cos(a), sin(a), sign(x), 0f, 0f) }
                for (i in 0 until seg) ix += if (x > 0) listOf(c0, (c0 + 1 + i).toShort(), (c0 + 2 + i).toShort()) else listOf(c0, (c0 + 2 + i).toShort(), (c0 + 1 + i).toShort())
            }
            return Mesh(d.toFloatArray(), ix.toShortArray())
        }
        /** Disque horizontal de rayon 1 au niveau y = 0. */
        fun disc(seg: Int = 72): Mesh {
            val d = ArrayList<Float>(); val ix = ArrayList<Short>()
            d += listOf(0f, 0f, 0f, 0f, 1f, 0f)
            for (i in 0..seg) { val a = 2 * PI.toFloat() * i / seg; d += listOf(cos(a), 0f, sin(a), 0f, 1f, 0f) }
            for (i in 0 until seg) ix += listOf(0, (i + 2).toShort(), (i + 1).toShort())
            return Mesh(d.toFloatArray(), ix.toShortArray())
        }
        /** Sphère de rayon 1. */
        fun sphere(seg: Int = 20): Mesh {
            val d = ArrayList<Float>(); val ix = ArrayList<Short>()
            for (i in 0..seg) { val t = PI.toFloat() * i / seg
                for (j in 0..seg) { val p = 2 * PI.toFloat() * j / seg; val x = sin(t) * cos(p); val y = cos(t); val z = sin(t) * sin(p); d += listOf(x, y, z, x, y, z) } }
            for (i in 0 until seg) for (j in 0 until seg) { val a = (i * (seg + 1) + j).toShort(); val b = (a + seg + 1).toShort()
                ix += listOf(a, (a + 1).toShort(), b, (a + 1).toShort(), (b + 1).toShort(), b) }
            return Mesh(d.toFloatArray(), ix.toShortArray())
        }
        /** Boîte unité centrée. */
        fun box(): Mesh {
            val d = ArrayList<Float>(); val ix = ArrayList<Short>()
            val faces = listOf(
                floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f))
            faces.forEach { n ->
                val u = if (abs(n[1]) > .5f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
                val w = floatArrayOf(n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0])
                val b = (d.size / 6).toShort()
                listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).forEach { (s, t) ->
                    d += listOf((n[0] + u[0] * s + w[0] * t) * .5f, (n[1] + u[1] * s + w[1] * t) * .5f, (n[2] + u[2] * s + w[2] * t) * .5f, n[0], n[1], n[2]) }
                ix += listOf(b, (b + 1).toShort(), (b + 2).toShort(), b, (b + 2).toShort(), (b + 3).toShort())
            }
            return Mesh(d.toFloatArray(), ix.toShortArray())
        }
    }
}

class BodyRenderer(private val ctx: Context) : GLSurfaceView.Renderer {
    @Volatile var state = BodyState()
    @Volatile var user = 0f; @Volatile var uel = 0f
    @Volatile var snap = true
    private var body: Body? = null
    private var prog = 0
    private val gbuf = ArrayList<IntArray>() // vbo pos, nrm, col, ibo
    private val fbs = ArrayList<Triple<FloatBuffer, FloatBuffer, FloatBuffer>>()
    private lateinit var cyl: Mesh; private lateinit var disc: Mesh; private lateinit var box: Mesh; private lateinit var ball: Mesh
    private var W = 1; private var H = 1
    private val proj = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16)
    private var lastKey = ""; private var lastId: String? = null
    private var az: Float? = null; private var ta = 1.05f; private var elx = 0f; private var dist = 360f
    private val tgt = V3(0f, 80f, 0f)
    private val t0 = System.currentTimeMillis()
    private var uMVP = 0; private var uM = 0; private var uN = 0; private var uCol = 0; private var uUse = 0; private var uAlpha = 0; private var uFlat = 0

    private val VS = """#version 300 es
        layout(location=0) in vec3 aP; layout(location=1) in vec3 aN; layout(location=2) in vec3 aC;
        uniform mat4 uMVP; uniform mat4 uM; uniform mat3 uN;
        out vec3 vN; out vec3 vC; out vec3 vW;
        void main(){ vN = normalize(uN * aN); vC = aC; vW = (uM * vec4(aP,1.0)).xyz; gl_Position = uMVP * vec4(aP,1.0); }"""
    private val FS = """#version 300 es
        precision mediump float;
        in vec3 vN; in vec3 vC; in vec3 vW;
        uniform vec3 uCol; uniform float uUse; uniform float uAlpha; uniform float uFlat;
        out vec4 o;
        void main(){
          if (uFlat > 0.5) { o = vec4(uCol, uAlpha); return; }
          vec3 n = normalize(vN);
          vec3 c = mix(uCol, vC, uUse);
          vec3 hemi = mix(vec3(0.333,0.376,0.439), vec3(1.0), n.y*0.5+0.5) * 0.8;
          vec3 L1 = normalize(vec3(120.0,260.0,160.0)); vec3 L2 = normalize(vec3(-120.0,160.0,-200.0));
          float d = 0.85*max(dot(n,L1),0.0) + 0.45*max(dot(n,L2),0.0);
          o = vec4(min(c*(hemi + vec3(d)), vec3(1.0)), uAlpha);
        }"""

    private fun shader(t: Int, src: String) = glCreateShader(t).also { glShaderSource(it, src); glCompileShader(it) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        prog = glCreateProgram()
        glAttachShader(prog, shader(GL_VERTEX_SHADER, VS)); glAttachShader(prog, shader(GL_FRAGMENT_SHADER, FS)); glLinkProgram(prog)
        uMVP = glGetUniformLocation(prog, "uMVP"); uM = glGetUniformLocation(prog, "uM"); uN = glGetUniformLocation(prog, "uN")
        uCol = glGetUniformLocation(prog, "uCol"); uUse = glGetUniformLocation(prog, "uUse"); uAlpha = glGetUniformLocation(prog, "uAlpha"); uFlat = glGetUniformLocation(prog, "uFlat")
        cyl = Mesh.cylinder(); disc = Mesh.disc(); box = Mesh.box(); ball = Mesh.sphere()
        val b = body ?: Body(ctx).also { body = it }
        gbuf.clear(); fbs.clear(); lastKey = ""; b.groups.forEach { it.key = null }
        b.groups.forEach { G ->
            val ids = IntArray(4); glGenBuffers(4, ids, 0)
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ids[3])
            val sb: ShortBuffer = ByteBuffer.allocateDirect(G.idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(G.idx)
            sb.position(0); glBufferData(GL_ELEMENT_ARRAY_BUFFER, G.idx.size * 2, sb, GL_STATIC_DRAW)
            gbuf += ids; fbs += Triple(fb(G.pos.size), fb(G.nrm.size), fb(G.col.size))
        }
        glEnable(GL_DEPTH_TEST)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { W = max(1, width); H = max(1, height); glViewport(0, 0, W, H) }

    private fun setM(m: FloatArray, col: FloatArray, use: Float, alpha: Float, flat: Boolean = false) {
        val mvp = FloatArray(16); Matrix.multiplyMM(mvp, 0, vp, 0, m, 0)
        glUniformMatrix4fv(uMVP, 1, false, mvp, 0); glUniformMatrix4fv(uM, 1, false, m, 0)
        // matrice des normales : inverse transposée de la partie 3×3
        val inv = FloatArray(16); val nt = FloatArray(16); Matrix.invertM(inv, 0, m, 0); Matrix.transposeM(nt, 0, inv, 0)
        glUniformMatrix3fv(uN, 1, false, floatArrayOf(nt[0], nt[1], nt[2], nt[4], nt[5], nt[6], nt[8], nt[9], nt[10]), 0)
        glUniform3f(uCol, col[0], col[1], col[2]); glUniform1f(uUse, use); glUniform1f(uAlpha, alpha); glUniform1f(uFlat, if (flat) 1f else 0f)
    }

    private fun drawBody(b: Body) {
        b.groups.forEachIndexed { i, G ->
            val ids = gbuf[i]
            glBindBuffer(GL_ARRAY_BUFFER, ids[0]); glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0)
            glBindBuffer(GL_ARRAY_BUFFER, ids[1]); glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0)
            glBindBuffer(GL_ARRAY_BUFFER, ids[2]); glEnableVertexAttribArray(2); glVertexAttribPointer(2, 3, GL_FLOAT, false, 0, 0)
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ids[3]); glDrawElements(GL_TRIANGLES, G.idx.size, GL_UNSIGNED_SHORT, 0)
        }
    }

    /** Matrice qui écrase un objet sur le sol selon la lumière principale (ombre portée). */
    private fun shadowM(): FloatArray {
        val lx = 120f; val ly = 260f; val lz = 160f; val y0 = 1.3f
        // P' = P - L * (P.y - y0) / L.y
        return floatArrayOf(1f, 0f, 0f, 0f, -lx / ly, 0f, -lz / ly, 0f, 0f, 0f, 1f, 0f, lx / ly * y0, y0, lz / ly * y0, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = state; val b = body ?: return
        val bg = rgb(s.bg)
        glClearColor(bg[0], bg[1], bg[2], 1f); glClearStencil(0)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
        val id = s.pose ?: return
        glUseProgram(prog)

        // pose et déformation
        val key = "$id/${s.k}"
        val J = b.build(id, s.k)
        if (key != lastKey) {
            val lift = if (b.isFree(id)) maxOf(0f, minOf(J["A1"]!!.y, J["A2"]!!.y) - 2f) else 0f
            b.skin(J, b.isFront(id), lift); lastKey = key
            b.groups.forEachIndexed { i, G ->
                val ids = gbuf[i]; val (pf, nf, _) = fbs[i]
                pf.position(0); pf.put(G.pos).position(0); glBindBuffer(GL_ARRAY_BUFFER, ids[0]); glBufferData(GL_ARRAY_BUFFER, G.pos.size * 4, pf, GL_DYNAMIC_DRAW)
                nf.position(0); nf.put(G.nrm).position(0); glBindBuffer(GL_ARRAY_BUFFER, ids[1]); glBufferData(GL_ARRAY_BUFFER, G.nrm.size * 4, nf, GL_DYNAMIC_DRAW)
            }
        }
        if (b.colorize(s.base, s.acc, if (s.colors != null) null else s.hl, s.colors)) b.groups.forEachIndexed { i, G ->
            val cf = fbs[i].third; cf.position(0); cf.put(G.col).position(0)
            glBindBuffer(GL_ARRAY_BUFFER, gbuf[i][2]); glBufferData(GL_ARRAY_BUFFER, G.col.size * 4, cf, GL_DYNAMIC_DRAW)
        }

        // articulations après miroir (pour le cadrage)
        val sx = if (s.mirror) -1f else 1f
        val pts = ArrayList<V3>()
        J.values.forEach { pts += V3(it.x * sx, it.y, it.z) }
        // la déformation a recalé le corps au sol : on recale les articulations de la même façon via les os
        b.bones.forEach { val e = b.boneEnd(it); pts += V3(e.x * sx, e.y, e.z) }
        val mn = V3(1e9f, 1e9f, 1e9f); val mx = V3(-1e9f, -1e9f, -1e9f)
        pts.forEach { mn.set(min(mn.x, it.x), min(mn.y, it.y), min(mn.z, it.z)); mx.set(max(mx.x, it.x), max(mx.y, it.y), max(mx.z, it.z)) }
        val want = V3((mn.x + mx.x) / 2, (mn.y + mx.y) / 2, (mn.z + mx.z) / 2)

        // caméra (comme la page web)
        val Nc = J["Nc"]!!; val Pc = J["Pc"]!!; val tl = Nc - Pc
        val lying = !s.map && abs(tl.y) < .55f * tl.len()
        if (id != lastId) { lastId = id; ta = b.camOf(id) ?: if (b.isFront(id) || b.isFly(id)) .35f else if (lying) 1.4f else 1.05f; if (az == null) az = ta }
        val still = s.map
        if (s.az != null) { ta = s.az; if (az == null || snap) az = s.az }
        az = az!! + (ta - az!!) * .06f
        val f = if (snap || still) 1f else .08f
        tgt.set(tgt.lerp(want, f))
        elx += ((if (lying) .3f else 0f) - elx) * f
        val A = az!! + user + if (still) 0f else (sin((System.currentTimeMillis() - t0) / 9000.0) * .12).toFloat()
        val E = min(1.35f, (if (s.map) .12f else .32f) + uel + elx)
        val asp = W.toFloat() / H
        val dd = if (s.map) { val span = maxOf(mx.y - mn.y, mx.z - mn.z, mx.x - mn.x, 120f); span * 1.55f + 22 } else {
            val dv = V3(sin(A) * cos(E), sin(E), cos(A) * cos(E)); val rt = V3(0f, 1f, 0f).cross(dv).norm(); val up = dv.cross(rt).norm()
            var hx = 0f; var hy = 0f
            pts.forEach { val d = it - want; hx = max(hx, abs(d.dot(rt))); hy = max(hy, abs(d.dot(up))) }
            val tf = tan(16 * PI.toFloat() / 180)
            maxOf(80f, (hy + 16) / tf, (hx + 16) / (tf * asp)) * 1.1f
        }
        dist = if (snap || still) dd else dist + (dd - dist) * .08f
        snap = false
        Matrix.perspectiveM(proj, 0, 32f, asp, 5f, 3000f)
        Matrix.setLookAtM(view, 0, tgt.x + dist * sin(A) * cos(E), tgt.y + dist * sin(E), tgt.z + dist * cos(A) * cos(E), tgt.x, tgt.y, tgt.z, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        val I = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        glDisable(GL_BLEND); glDepthMask(true); glDepthFunc(GL_LESS)
        // sol et tapis
        if (!s.map) {
            glEnable(GL_CULL_FACE); glFrontFace(GL_CCW); glCullFace(GL_BACK)
            val m = FloatArray(16); Matrix.setIdentityM(m, 0); Matrix.scaleM(m, 0, 170f, 1f, 170f); setM(m, rgb(s.ground), 0f, 1f); disc.draw()
            Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, 0f, .6f, 10f); Matrix.scaleM(m, 0, 64f, 1.2f, 190f); setM(m, rgb(s.mat), 0f, 1f); box.draw()
        }
        val bodyM = FloatArray(16).also { Matrix.setIdentityM(it, 0); if (s.mirror) Matrix.scaleM(it, 0, -1f, 1f, 1f) }

        // objets : haltères, kettlebell, élastiques, accessoires (maillage, matrice, couleur)
        val objs = ArrayList<Triple<Mesh, FloatArray, FloatArray>>()
        fun mat(p: V3, sxx: Float, syy: Float, szz: Float, rot: FloatArray? = null): FloatArray {
            var m = FloatArray(16); Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, p.x, p.y, p.z)
            if (rot != null) { val t = FloatArray(16); Matrix.multiplyMM(t, 0, m, 0, rot, 0); m = t }
            Matrix.scaleM(m, 0, sxx, syy, szz); return m
        }
        fun rotTo(d: V3): FloatArray { val q = Q4.fromUnit(V3(1f, 0f, 0f), d.copy().norm()).mat(); return floatArrayOf(q[0], q[1], q[2], 0f, q[3], q[4], q[5], 0f, q[6], q[7], q[8], 0f, 0f, 0f, 0f, 1f) }
        /** Segment cylindrique entre deux points (élastique, pieds de chaise…). */
        fun seg(a: V3, c: V3, r: Float, col: FloatArray) { val d = c - a; val L = d.len(); if (L < .5f) return; objs += Triple(cyl, mat(a.lerp(c, .5f), L, r, r, rotTo(d)), col) }
        val exo = s.ex?.let { Prog.EX[it] }
        val tool = exo?.tool ?: (if (s.nodb) "" else "db")
        fun inHand(w: String): V3 { val g = b.grip(if (w == "W1") "R" else "L"); g.x *= sx; return g }
        fun jt(n: String): V3 { val v = J[n]!!; return V3(v.x * sx, v.y, v.z) }
        val handle = rgb(0x2A2F35); val dbc = rgb(s.db)
        if (!s.map && (tool == "db" || tool == "kb") && !s.nodb) {
            val dbs = b.dbs(id)
            fun dumbbell(p: V3, rot: FloatArray?) {
                listOf(Triple(0f, 12f, 1.6f), Triple(8f, 4.5f, 6.5f), Triple(-8f, 4.5f, 6.5f)).forEach { (ox, L, r) ->
                    var m = FloatArray(16); Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, p.x, p.y, p.z)
                    if (rot != null) { val t = FloatArray(16); Matrix.multiplyMM(t, 0, m, 0, rot, 0); m = t }
                    Matrix.translateM(m, 0, ox, 0f, 0f); Matrix.scaleM(m, 0, L, r, r); objs += Triple(cyl, m, if (ox == 0f) handle else dbc)
                }
            }
            fun kettlebell(p: V3, fore: V3) {
                // la cloche pend sous la main, ou repose le long de l'avant-bras quand le bras est levé
                val down = V3(0f, -1f, 0f)
                val off = if (fore.y < -.3f) down else (fore * -1f + down).norm()
                val c = p + off * 9f
                objs += Triple(ball, mat(c, 8f, 8f, 8f), dbc)
                seg(p + V3(-4.5f, 0f, 0f), p + V3(4.5f, 0f, 0f), 1.1f, handle)
                seg(p + V3(-4.5f, 0f, 0f), c + V3(-4f, 3f, 0f), 1.1f, handle)
                seg(p + V3(4.5f, 0f, 0f), c + V3(4f, 3f, 0f), 1.1f, handle)
            }
            fun fore(w: String) = (jt(w) - jt(if (w == "W1") "E1" else "E2")).norm()
            if (b.twoHand(id) && dbs.isNotEmpty()) {
                val p = inHand("W1").lerp(inHand("W2"), .5f)
                if (tool == "kb") kettlebell(p, fore("W1").lerp(fore("W2"), .5f))
                else dumbbell(p, if (id == "squat" || id == "ohtri") FloatArray(16).also { Matrix.setRotateM(it, 0, 90f, 0f, 0f, 1f) } else null)
            } else dbs.forEach { w ->
                if (tool == "kb") kettlebell(inHand(w), fore(w))
                else dumbbell(inHand(w), if (id == "hammer") rotTo(fore(w)) else null)
            }
        }
        // barre longue et roue abdominale (tenues à deux mains)
        if (!s.map && (tool == "bb" || tool == "wheel")) {
            val gr = inHand("W1"); val gl = inHand("W2"); val c = gr.lerp(gl, .5f)
            var ax = gr - gl; if (ax.len() < 2f) ax = V3(1f, 0f, 0f); ax.norm()
            if (tool == "bb") {
                seg(c - ax * 65f, c + ax * 65f, 1.4f, handle)
                listOf(1f, -1f).forEach { sg -> objs += Triple(cyl, mat(c + ax * (48f * sg), 4.5f, 14f, 14f, rotTo(ax)), dbc); objs += Triple(cyl, mat(c + ax * (43f * sg), 1.5f, 3f, 3f, rotTo(ax)), handle) }
            } else {
                objs += Triple(cyl, mat(c + V3(0f, 0f, 0f), 6f, 9f, 9f, rotTo(ax)), dbc)
                seg(c - ax * 14f, c + ax * 14f, 1.3f, handle)
            }
        }
        // élastiques
        if (!s.map && exo != null && exo.band.isNotEmpty()) {
            val bc = rgb(s.band); val mini = exo.tool == "mini"
            fun pt(n: String): V3 = when (n) {
                "W1", "W2" -> inHand(n)
                "A1", "A2" -> jt(n).also { it.y = 1.2f }
                "K1", "K2" -> jt(n).lerp(jt("Pc"), .18f)
                else -> jt(n)
            }
            exo.band.forEach { (a, c) -> seg(pt(a), pt(c), if (mini) 2.4f else .8f, bc) }
        }
        // accessoires de la pose
        if (!s.map) b.propOf(id)?.let { (t, x) ->
            val z = x - 100f; val pc = rgb(s.prop)
            when (t) {
                "chair" -> {
                    objs += Triple(box, mat(V3(0f, 44f, z), 42f, 3f, 40f), pc)
                    listOf(-18f, 18f).forEach { lx -> listOf(-17f, 17f).forEach { lz -> objs += Triple(box, mat(V3(lx, 21.5f, z + lz), 2.5f, 43f, 2.5f), pc) } }
                    val back = if (x < 100f) -19f else 19f
                    objs += Triple(box, mat(V3(0f, 66f, z + back * 1.0f), 42f, 40f, 2.5f), pc)
                }
                "bench" -> {
                    objs += Triple(box, mat(V3(0f, 40f, z), 28f, 6f, 115f), pc)
                    listOf(-45f, 45f).forEach { lz -> objs += Triple(box, mat(V3(0f, 18.5f, z + lz), 22f, 37f, 4f), handle) }
                }
                "pullbar" -> {
                    val gr = b.grip("R"); val gl = b.grip("L"); val y = (gr.y + gl.y) / 2 + 1.5f; val zz = (gr.z + gl.z) / 2
                    seg(V3(-60f, y, zz), V3(60f, y, zz), 1.6f, handle)
                    listOf(-60f, 60f).forEach { xx -> seg(V3(xx, 0f, zz), V3(xx, y, zz), 2.5f, pc) }
                }
                "dipbars" -> {
                    val gr = b.grip("R"); val gl = b.grip("L"); val y = (gr.y + gl.y) / 2 - 2f; val zz = (gr.z + gl.z) / 2
                    listOf(gr.x * sx, gl.x * sx).forEach { xx -> seg(V3(xx, y, zz - 30f), V3(xx, y, zz + 30f), 2f, handle); listOf(-26f, 26f).forEach { dz -> seg(V3(xx, 0f, zz + dz), V3(xx, y, zz + dz), 2.5f, pc) } }
                }
                "incbench" -> {
                    objs += Triple(box, mat(V3(0f, 40f, z + 20f), 28f, 6f, 45f), pc)
                    val back = Q4.fromUnit(V3(0f, 0f, 1f), V3(0f, .7f, -.71f)).mat()
                    objs += Triple(box, mat(V3(0f, 66f, z - 22f), 28f, 6f, 70f, floatArrayOf(back[0], back[1], back[2], 0f, back[3], back[4], back[5], 0f, back[6], back[7], back[8], 0f, 0f, 0f, 0f, 1f)), pc)
                    listOf(-10f, 40f).forEach { lz -> objs += Triple(box, mat(V3(0f, 18.5f, z + lz), 22f, 37f, 4f), handle) }
                }
                "roller" -> { val n = jt("Nc"); objs += Triple(cyl, mat(V3(0f, 7.5f, n.z), 45f, 7.5f, 7.5f), rgb(0x3B82C4)) }
                "wall" -> objs += Triple(box, mat(V3(0f, 85f, z + (if (x < 100f) -2f else 2f)), 110f, 170f, 3f), pc)
            }
        }

        // ombre portée (une seule couche grâce au stencil)
        if (!s.map) {
            glDisable(GL_CULL_FACE)
            glEnable(GL_STENCIL_TEST); glStencilFunc(GL_EQUAL, 0, 0xFF); glStencilOp(GL_KEEP, GL_KEEP, GL_INCR)
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDepthMask(false)
            val sm = FloatArray(16); Matrix.multiplyMM(sm, 0, shadowM(), 0, bodyM, 0)
            val a = if (s.ghost) .14f else .28f
            setM(sm, floatArrayOf(0f, 0f, 0f), 0f, a, true); drawBody(b)
            objs.forEach { (me, m, _) -> val t = FloatArray(16); Matrix.multiplyMM(t, 0, shadowM(), 0, m, 0); setM(t, floatArrayOf(0f, 0f, 0f), 0f, a, true); me.draw() }
            glDisable(GL_STENCIL_TEST); glDisable(GL_BLEND); glDepthMask(true)
        }

        // corps
        glEnable(GL_CULL_FACE); glCullFace(GL_BACK); glFrontFace(if (s.mirror) GL_CW else GL_CCW)
        val op = if (s.ghost) .55f else 1f
        if (s.ghost) { // pré-passe de profondeur pour un fantôme propre
            glColorMask(false, false, false, false); setM(bodyM, rgb(s.base), 1f, 1f); drawBody(b); glColorMask(true, true, true, true)
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDepthFunc(GL_LEQUAL)
        }
        setM(bodyM, rgb(s.base), 1f, op); drawBody(b)
        glDepthFunc(GL_LESS); glFrontFace(GL_CCW)
        objs.forEach { (me, m, col) -> setM(m, col, 0f, op); me.draw() }
        glDisable(GL_BLEND)
    }
}

/** Choix d'une configuration EGL avec stencil et, si possible, anticrénelage. */
private class Chooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, d: EGLDisplay): EGLConfig {
        fun pick(samples: Int): EGLConfig? {
            val a = intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_STENCIL_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, 0x40 /* ES3 */,
                EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0, EGL10.EGL_SAMPLES, samples, EGL10.EGL_NONE)
            val n = IntArray(1); val c = arrayOfNulls<EGLConfig>(1)
            return if (egl.eglChooseConfig(d, a, c, 1, n) && n[0] > 0) c[0] else null
        }
        return pick(4) ?: pick(0) ?: run {
            val n = IntArray(1); val c = arrayOfNulls<EGLConfig>(1)
            egl.eglChooseConfig(d, intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_STENCIL_SIZE, 8, EGL10.EGL_NONE), c, 1, n); c[0]!!
        }
    }
}

@SuppressLint("ViewConstructor")
class BodyGL(ctx: Context) : GLSurfaceView(ctx) {
    val r = BodyRenderer(ctx)
    private var lx = 0f; private var ly = 0f; private var u0 = 0f; private var v0 = 0f; private var lastTap = 0L
    init {
        setEGLContextClientVersion(3); setEGLConfigChooser(Chooser()); preserveEGLContextOnPause = true
        setRenderer(r); renderMode = RENDERMODE_CONTINUOUSLY
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val dp = resources.displayMetrics.density
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lx = e.x; ly = e.y; u0 = r.user; v0 = r.uel
                val now = System.currentTimeMillis(); if (now - lastTap < 300) { r.user = 0f; r.uel = 0f }; lastTap = now
            }
            MotionEvent.ACTION_MOVE -> { r.user = u0 + (e.x - lx) / dp * .012f; r.uel = (v0 + (e.y - ly) / dp * .006f).coerceIn(-.25f, .6f) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}

/** Personnage 3D dans Compose. */
@Composable
fun Body3D(state: BodyState, modifier: Modifier = Modifier) {
    AndroidView(factory = { BodyGL(it) }, modifier = modifier, update = { v -> v.r.state = state },
        onRelease = { it.onPause() })
}
