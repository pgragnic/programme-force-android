package fr.pgragnic.force.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Vignettes des exercices (liste de la séance, inspirée de la capture envoyée par Philippe : « Peux-tu t'inspirer de la première image
 * pour la présentation des séances et on montre tous les exercices ? ») : le personnage 3D de l'appli, muscles travaillés en couleur,
 * rendu une fois hors écran puis gardé en mémoire et dans le cache.
 */
object Thumbs {
    private val pool = Executors.newSingleThreadExecutor()
    private val dispatcher = pool.asCoroutineDispatcher()
    private val mem = HashMap<String, Bitmap>()
    private var dpy: EGLDisplay? = null; private var surf: EGLSurface? = null; private var egl: EGLContext? = null
    private var r: BodyRenderer? = null
    private const val S = 360   // rendu 2× puis réduit (bords lisses)

    fun cached(key: String): Bitmap? = synchronized(mem) { mem[key] }

    suspend fun get(ctx: Context, key: String, st: BodyState, px: Int): Bitmap? = cached(key) ?: withContext(dispatcher) {
        cached(key)?.let { return@withContext it }
        val f = File(ctx.cacheDir, "vignettes/$key.png")
        val bm = runCatching { if (f.exists()) BitmapFactory.decodeFile(f.path) else null }.getOrNull() ?: runCatching { render(ctx, st, px) }.getOrNull()?.also { b ->
            runCatching { f.parentFile?.mkdirs(); f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        bm?.also { synchronized(mem) { mem[key] = it } }
    }

    private fun setup(ctx: Context): BodyRenderer {
        r?.let { return it }
        val d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY); val v = IntArray(2); EGL14.eglInitialize(d, v, 0, v, 1)
        val a = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_STENCIL_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, 0x40 /* ES3 */, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
        val cs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        check(EGL14.eglChooseConfig(d, a, 0, cs, 0, 1, n, 0) && n[0] > 0)
        val c = EGL14.eglCreateContext(d, cs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        val s = EGL14.eglCreatePbufferSurface(d, cs[0], intArrayOf(EGL14.EGL_WIDTH, S, EGL14.EGL_HEIGHT, S, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(d, s, s, c)
        dpy = d; surf = s; egl = c
        return BodyRenderer(ctx.applicationContext).also { it.onSurfaceCreated(null, null); it.onSurfaceChanged(null, S, S); r = it }
    }

    private fun render(ctx: Context, st: BodyState, px: Int): Bitmap {
        val rr = setup(ctx)
        rr.reset(); rr.state = st; rr.onDrawFrame(null); GLES30.glFinish()
        val buf = ByteBuffer.allocateDirect(S * S * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, S, S, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf); buf.rewind()
        val raw = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(buf) }
        val m = android.graphics.Matrix().apply { postScale(px.toFloat() / S, -px.toFloat() / S) }   // OpenGL lit de bas en haut
        return Bitmap.createBitmap(raw, 0, 0, S, S, m, true)
    }
}
