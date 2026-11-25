package simplylucaapps.girocalendar.ui.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Consigli vocali della navigazione: motore vocale di Android nella lingua dell'app, con «audio ducking»
 * (abbassa la musica mentre parla, poi la rimette). [onReady](true/false) dice se la voce è utilizzabile.
 */
class NavVoice(private val ctx: Context, private val onReady: (Boolean) -> Unit) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
    private var tts: TextToSpeech? = null
    private var ok = false

    init {
        tts = TextToSpeech(ctx) { status ->
            val t = tts
            ok = status == TextToSpeech.SUCCESS && t != null &&
                (t.setLanguage(ctx.resources.configuration.locales[0] ?: Locale.getDefault())).let { it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED }
            if (ok) {
                t?.setAudioAttributes(attrs)
                t?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { am.abandonAudioFocusRequest(focus) }
                    @Deprecated("API < 21") override fun onError(id: String?) { am.abandonAudioFocusRequest(focus) }
                })
            }
            onReady(ok)
        }
    }

    fun speak(text: String) {
        if (!ok) return
        am.requestAudioFocus(focus)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    fun shutdown() { runCatching { tts?.stop(); tts?.shutdown(); am.abandonAudioFocusRequest(focus) }; tts = null }
}

/** Cosa è già stato detto lungo il percorso (per non ripetere). */
class NavVoiceState {
    val farSaid = HashSet<Int>(); val nearSaid = HashSet<Int>(); val trailSaid = HashSet<Int>(); val forkSaid = HashSet<Int>()
    var off = false; var lastDone = 0f
    fun reset() { farSaid.clear(); nearSaid.clear(); trailSaid.clear(); forkSaid.clear(); off = false }
}
