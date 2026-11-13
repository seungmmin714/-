package ai.moa.app.platform

import ai.moa.core.Theme
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Short, locally synthesized textures. Never changes vehicle settings or system volume. */
class SoundscapeController(context: Context) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var track: AudioTrack? = null
    private var focused = false
    private val stopTask = Runnable { stop() }
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change -> if (change < 0) stop() }, handler)
        .build()

    /** Explicit preview only; quiet envelope, 1.4 seconds maximum, no looping or flashing. */
    fun play(theme: Theme): Boolean {
        stop()
        return try {
            if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            focused = true
            val rate = 22050
            val samples = ShortArray((rate * 1.4).toInt())
            val random = Random(14)
            var filteredNoise = 0.0
            for (i in samples.indices) {
                val t = i.toDouble() / rate
                val envelope = sin(PI * i / samples.size).let { it * it }
                filteredNoise = 0.96 * filteredNoise + 0.04 * (random.nextDouble() * 2 - 1)
                val sound = when (theme) {
                    Theme.SEA -> filteredNoise * 2.4 * (0.7 + 0.3 * sin(t * 4))
                    Theme.FOREST -> filteredNoise * 1.8 + 0.10 * sin(2 * PI * (650 * t + 40 * sin(t * 4)))
                    Theme.SPACE -> 0.22 * sin(2 * PI * 440 * t) + 0.12 * sin(2 * PI * 660 * t)
                }
                samples[i] = (sound * envelope * 2200).toInt().coerceIn(-3000, 3000).toShort()
            }
            val audio = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(samples.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            track = audio
            if (audio.state != AudioTrack.STATE_INITIALIZED || audio.write(samples, 0, samples.size) != samples.size) {
                stop()
                false
            } else {
                audio.play()
                handler.postDelayed(stopTask, 1500)
                true
            }
        } catch (_: Exception) { stop(); false }
    }

    fun stop() {
        handler.removeCallbacks(stopTask)
        val old = track
        track = null
        try { old?.stop() } catch (_: Exception) { }
        try { old?.release() } catch (_: Exception) { }
        if (focused) {
            focused = false
            try { manager.abandonAudioFocusRequest(focus) } catch (_: Exception) { }
        }
    }

    fun close() = stop()
}
