package dev.chungjungsoo.gptmobile.data.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioTrackPcmSink : PcmSink {
    private val lock = Any()
    private var session = 0
    private var track: AudioTrack? = null

    override fun open(sampleRateHz: Int) {
        stop()
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer > 0) minBuffer * 2 else sampleRateHz
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        synchronized(lock) {
            session += 1
            track = created
        }
        created.play()
    }

    override fun write(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        val active: AudioTrack
        val token: Int
        synchronized(lock) {
            active = track ?: return
            token = session
        }
        var offset = 0
        while (offset < pcm.size) {
            val current = synchronized(lock) { session == token && track === active }
            if (!current) return
            val wrote = runCatching { active.write(pcm, offset, pcm.size - offset) }.getOrDefault(-1)
            if (wrote <= 0) return
            offset += wrote
        }
    }

    override fun stop() {
        val active = synchronized(lock) {
            session += 1
            track.also { track = null }
        } ?: return
        runCatching { active.pause() }
        runCatching { active.flush() }
        runCatching { active.stop() }
        runCatching { active.release() }
    }
}
