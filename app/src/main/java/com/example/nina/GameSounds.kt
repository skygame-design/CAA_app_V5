package com.example.nina

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Bruitages des jeux : bulle qui éclate, réussite, « essaie encore », petit toc. */
enum class GameSound { POP, SUCCESS, TRY_AGAIN, TAP }

/**
 * Sons des jeux fabriqués par calcul (aucun fichier son à fournir) : notes douces, sans clic au début,
 * qui s'éteignent progressivement. Le son « essaie encore » est volontairement léger, jamais un buzzer.
 */
object GameTones {
    const val SAMPLE_RATE = 22050

    private class Builder {
        val data = ArrayList<Float>()
        private val random = Random(7)

        /** Note de fréquence glissante (Hz), durée en ms, volume 0..1, extinction (plus grand = plus rapide), part de souffle. */
        fun note(freqStart: Double, freqEnd: Double, ms: Int, volume: Double, decay: Double, noise: Double = 0.0) {
            val n = SAMPLE_RATE * ms / 1000
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / n
                phase += 2 * PI * (freqStart + (freqEnd - freqStart) * t) / SAMPLE_RATE
                val attack = min(1.0, i / (SAMPLE_RATE * 0.005)) // 5 ms de montée : pas de clic
                val envelope = attack * exp(-decay * t) * (1 - t * t * t) // finit à zéro
                val sample = sin(phase) * (1 - noise) + (random.nextDouble() * 2 - 1) * noise
                data.add((sample * envelope * volume).toFloat())
            }
        }

        fun pcm(): ShortArray = ShortArray(data.size) { (data[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    }

    fun samples(sound: GameSound): ShortArray = Builder().apply {
        when (sound) {
            GameSound.POP -> note(950.0, 280.0, 90, 0.8, 5.0, noise = 0.15)
            GameSound.SUCCESS -> {
                listOf(523.25, 659.25, 783.99).forEach { note(it, it, 110, 0.5, 2.0) } // do, mi, sol
                note(1046.5, 1046.5, 320, 0.5, 4.0) // do aigu
            }
            GameSound.TRY_AGAIN -> { note(392.0, 392.0, 150, 0.35, 3.0); note(330.0, 330.0, 220, 0.35, 4.0) }
            GameSound.TAP -> note(660.0, 700.0, 70, 0.4, 6.0)
        }
    }.pcm()

    /** Fichier WAV (PCM 16 bits, mono) prêt à être lu par Android. */
    fun wav(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataSize = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataSize).put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            .putShort(1) // PCM
            .putShort(1) // mono
            .putInt(sampleRate).putInt(sampleRate * 2)
            .putShort(2) // octets par échantillon
            .putShort(16) // bits par échantillon
        buffer.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataSize)
        pcm.forEach { buffer.putShort(it) }
        return buffer.array()
    }
}

/** Lecteur des bruitages : plusieurs sons peuvent se superposer (bulles éclatées coup sur coup). */
class GameSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<GameSound, Int> = GameSound.entries.associateWith { sound ->
        runCatching {
            val file = File(context.cacheDir, "jeu_${sound.name.lowercase()}.wav")
            file.writeBytes(GameTones.wav(GameTones.samples(sound)))
            pool.load(file.path, 1)
        }.getOrDefault(0)
    }

    fun play(sound: GameSound) {
        ids[sound]?.takeIf { it != 0 }?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun release() = pool.release()
}
