package com.example.micboost

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MicBoostEngine(
    private val sampleRate: Int = 44100
) {
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    private val inMinBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
    private val outMinBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)

    private var recorder: AudioRecord? = null
    private var tracker: AudioTrack? = null
    private var workerThread: Thread? = null

    @Volatile var isRunning: Boolean = false
        private set

    @Volatile var boostGain: Float = 5.0f
    @Volatile var monitorToEar: Boolean = false
    @Volatile var limiterEnabled: Boolean = true

    fun start(
        onLevelUpdate: (peak: Float, clipped: Boolean) -> Unit
    ) {
        if (isRunning || inMinBuf <= 0 || outMinBuf <= 0) return

        val bufSamples = max(256, (inMinBuf / 2) / 2)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelIn,
            encoding,
            max(inMinBuf * 2, bufSamples * 4)
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }

        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(rec.audioSessionId)?.enabled = true
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(rec.audioSessionId)?.enabled = false
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelOut)
                    .build()
            )
            .setBufferSizeInBytes(max(outMinBuf * 2, bufSamples * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        rec.startRecording()
        if (monitorToEar && track.state == AudioTrack.STATE_INITIALIZED) {
            track.play()
        }

        recorder = rec
        tracker = track
        isRunning = true

        workerThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val pcm = ShortArray(bufSamples)

            while (isRunning) {
                val read = rec.read(pcm, 0, pcm.size)
                if (read > 0) {
                    var peakInt = 0
                    var hitClip = false
                    val gain = boostGain.coerceIn(0.5f, 10.0f)

                    for (i in 0 until read) {
                        val rawBoosted = (pcm[i].toFloat() * gain).roundToInt()

                        val finalSample = if (limiterEnabled) {
                            val ceiling = (Short.MAX_VALUE * 0.95f).toInt()
                            if (rawBoosted > ceiling || rawBoosted < -ceiling) {
                                hitClip = true
                            }
                            rawBoosted.coerceIn(-ceiling, ceiling).toShort()
                        } else {
                            if (rawBoosted > Short.MAX_VALUE.toInt() || rawBoosted < Short.MIN_VALUE.toInt()) {
                                hitClip = true
                            }
                            rawBoosted.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }

                        pcm[i] = finalSample
                        val mag = abs(finalSample.toInt())
                        if (mag > peakInt) peakInt = mag
                    }

                    if (monitorToEar && tracker?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        tracker?.write(pcm, 0, read)
                    }

                    val normPeak = min(1f, peakInt.toFloat() / Short.MAX_VALUE.toFloat())
                    onLevelUpdate(normPeak, hitClip)
                }
            }
        }.apply {
            name = "MicBoostAudioThread"
            isDaemon = true
            start()
        }
    }

    fun setMonitorEnabled(enabled: Boolean) {
        monitorToEar = enabled
        val tr = tracker ?: return
        if (!isRunning || tr.state != AudioTrack.STATE_INITIALIZED) return
        try {
            if (enabled) tr.play() else tr.pause()
        } catch (_: IllegalStateException) {
        }
    }

    fun stop() {
        isRunning = false
        try {
            workerThread?.join(250)
        } catch (_: InterruptedException) {
        }
        workerThread = null

        recorder?.run {
            try {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
            } catch (_: Exception) {
            }
            release()
        }
        recorder = null

        tracker?.run {
            try {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
            } catch (_: Exception) {
            }
            release()
        }
        tracker = null
    }
}
