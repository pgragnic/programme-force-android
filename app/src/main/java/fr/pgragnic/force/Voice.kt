package fr.pgragnic.force

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Voix (synthèse vocale du téléphone, en français), bips et vibrations. */
class Voice(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var ready = false
    var on = true
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.FRANCE; tts.setSpeechRate(1.05f); ready = true
        }
    }

    fun say(t: String) {
        if (!on || !ready || t.isBlank()) return
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "pf")
    }

    /** Bip court (aigu) ou long (grave). */
    fun beep(kind: Int) {
        if (!on) return
        when (kind) {
            0 -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            1 -> tone?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            2 -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        }
    }

    fun buzz(vararg ms: Long) {
        val v = vib ?: return
        if (ms.isEmpty()) return
        runCatching {
            if (ms.size == 1) v.vibrate(VibrationEffect.createOneShot(ms[0], VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(VibrationEffect.createWaveform(longArrayOf(0) + ms, -1))
        }
    }

    fun close() { runCatching { tts.stop(); tts.shutdown(); tone?.release() } }
}
