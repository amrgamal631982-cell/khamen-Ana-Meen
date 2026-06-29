package com.example

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundHelper {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playTone(frequencies: List<Float>, durationMs: Int, volume: Float = 0.4f) {
        scope.launch {
            try {
                val sampleRate = 22050
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val sample = ShortArray(numSamples)
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    var sum = 0.0
                    for (freq in frequencies) {
                        sum += sin(2.0 * Math.PI * freq * t)
                    }
                    val value = (sum / frequencies.size * 32767.0 * volume).toInt().toShort()
                    sample[i] = value
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    numSamples * 2,
                    AudioTrack.MODE_STATIC
                )
                audioTrack.write(sample, 0, numSamples)
                audioTrack.play()
                
                // Keep playing until done, then release
                delay(durationMs.toLong() + 30)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Effect 1: Start Spin (mystery rising arpeggio ticks)
    fun playSpinSound() {
        scope.launch {
            val notes = listOf(261.63f, 329.63f, 392.00f, 523.25f, 659.25f, 784.00f, 1046.50f)
            for (freq in notes) {
                playTone(listOf(freq), 90, 0.35f)
                delay(100)
            }
        }
    }

    // Effect 2: Correct Guess (Happy Pharaonic Victory Chime)
    fun playCorrectSound() {
        scope.launch {
            // High-pitched cheerful major scale arpeggio
            val chords = listOf(523.25f, 659.25f, 784.00f, 1046.50f)
            for (note in chords) {
                playTone(listOf(note), 120, 0.45f)
                delay(110)
            }
            // Double final ringing chord
            playTone(listOf(1046.50f, 1318.51f), 300, 0.5f)
        }
    }

    // Effect 3: Timeout (Dramatic descending buzz sound)
    fun playTimeoutSound() {
        scope.launch {
            playTone(listOf(180f, 90f), 200, 0.6f)
            delay(220)
            playTone(listOf(140f, 70f), 500, 0.65f)
        }
    }

    // Effect 4: Click or generic tap feedback sound
    fun playClickSound() {
        playTone(listOf(600f), 50, 0.3f)
    }

    // Effect 5: Round skip or wrong guess (neutral tone)
    fun playSkipSound() {
        playTone(listOf(220f), 150, 0.4f)
    }
}
