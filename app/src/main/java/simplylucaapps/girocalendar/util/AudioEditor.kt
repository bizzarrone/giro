package simplylucaapps.girocalendar.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Taglia (trim) e applica fade-in/fade-out a una clip audio con Media3 Transformer:
 * decodifica il file sorgente in PCM, applica gli effetti campione per campione, e ricodifica
 * in un nuovo file. Necessario perché MP3/AAC sono formati compressi — non si possono
 * "tagliare" troncando i byte del file originale come si farebbe con testo o immagini raw.
 *
 * Usato dall'editor evento per produrre il file finale da comprimere in base64 e salvare in
 * [simplylucaapps.girocalendar.data.remote.firestore.RideEvent.audioData].
 */
@UnstableApi
object AudioEditor {

    /**
     * @param startMs inizio del taglio in millisecondi
     * @param endMs fine del taglio in millisecondi (durata risultante = endMs - startMs, max 60s)
     * @param fadeInMs durata del fade-in in millisecondi (0 = nessun fade)
     * @param fadeOutMs durata del fade-out in millisecondi (0 = nessun fade)
     * @return il file esportato (nella cache dell'app), o null se l'esportazione fallisce
     */
    suspend fun trimWithFade(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        fadeInMs: Long,
        fadeOutMs: Long,
    ): File? {
        // Copia il sorgente in un file locale prima di passarlo al Transformer: per URI di
        // provider remoti (Nextcloud e simili) il ClippingConfiguration basato su timestamp
        // può fallire silenziosamente se il media non è pienamente seekable, esportando il
        // file intero invece del segmento richiesto.
        val localSource = withContext(Dispatchers.IO) {
            val tmp = File.createTempFile("audio_source", ".tmp", context.cacheDir)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp
        }
        try {
            return trimWithFadeFromFile(context, Uri.fromFile(localSource), startMs, endMs, fadeInMs, fadeOutMs)
        } finally {
            localSource.delete()
        }
    }

    private suspend fun trimWithFadeFromFile(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        fadeInMs: Long,
        fadeOutMs: Long,
    ): File? = suspendCancellableCoroutine { cont ->
        val outputFile = File.createTempFile("audio_edit", ".m4a", context.cacheDir)

        val clipped = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val clipDurationMs = endMs - startMs
        val fadeProcessor = FadeAudioProcessor(
            durationMs = clipDurationMs,
            fadeInMs = fadeInMs.coerceIn(0, clipDurationMs / 2),
            fadeOutMs = fadeOutMs.coerceIn(0, clipDurationMs / 2),
        )
        // Resample a 44.1 kHz: qualità piena (non solo voce), a differenza del precedente
        // downsampling a 16 kHz. Il canale (mono/stereo) resta quello della sorgente: niente
        // downmix forzato, così una registrazione stereo resta stereo. Il file pesa
        // sensibilmente di più (vedi bitrate sotto), quindi con foto+GPX nello stesso evento
        // l'errore "documento troppo grande" (DocumentTooLargeException) può comparire più
        // spesso: è un compromesso qualità/rischio scelto esplicitamente dall'utente.
        val resampleProcessor = SonicAudioProcessor().apply { setOutputSampleRateHz(44_100) }

        val editedItem = EditedMediaItem.Builder(clipped)
            .setEffects(Effects(listOf(fadeProcessor, resampleProcessor), emptyList()))
            .build()

        // Bitrate alto (qualità musicale, non solo vocale): a 128 kbps un clip stereo di 60s
        // pesa ~960 KB, vicino al limite Firestore di 1 MiB per documento — l'utente ha scelto
        // qualità alta accettando il rischio di dover accorciare la clip o alleggerire la foto
        // se il documento risulta troppo grande.
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(128_000).build())
            .build()

        val transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(outputFile)
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            })
            .build()

        transformer.start(editedItem, outputFile.absolutePath)
        cont.invokeOnCancellation { transformer.cancel() }
    }
}

/**
 * AudioProcessor Media3 che applica una rampa lineare di volume (0→1 all'inizio, 1→0 alla
 * fine) ai campioni PCM 16-bit in transito, per il fade-in/fade-out della clip. Lavora sui
 * campioni già decodificati, prima della ricodifica finale — indipendente dal formato
 * compresso originale.
 */
@UnstableApi
private class FadeAudioProcessor(
    private val durationMs: Long,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
) : BaseAudioProcessor() {

    private var sampleRate = 0
    private var channelCount = 0
    private var samplesProcessed = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        samplesProcessed = 0L
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val fadeInSamples = (fadeInMs * sampleRate / 1000).toInt()
        val fadeOutStartSample = (durationMs - fadeOutMs) * sampleRate / 1000
        val totalSamples = (durationMs * sampleRate / 1000)

        val remaining = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(remaining)
        val inShorts = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val outShorts = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        val frameCount = inShorts.remaining() / channelCount
        for (frame in 0 until frameCount) {
            val currentSample = samplesProcessed + frame
            val gain = when {
                fadeInMs > 0 && currentSample < fadeInSamples ->
                    currentSample.toFloat() / fadeInSamples
                fadeOutMs > 0 && currentSample >= fadeOutStartSample ->
                    (1f - (currentSample - fadeOutStartSample).toFloat() / maxOf(totalSamples - fadeOutStartSample, 1)).coerceIn(0f, 1f)
                else -> 1f
            }
            for (ch in 0 until channelCount) {
                val sample = inShorts.get()
                outShorts.put((sample * gain).toInt().toShort())
            }
        }
        samplesProcessed += frameCount
        inputBuffer.position(inputBuffer.limit())
        outputBuffer.position(0).limit(remaining)
    }
}
