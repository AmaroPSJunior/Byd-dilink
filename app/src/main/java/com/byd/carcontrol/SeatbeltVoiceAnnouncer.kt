package com.byd.carcontrol

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.util.ArrayDeque

/** Plays bundled Brazilian Portuguese seat specific prompts through Android's cabin audio route. */
class SeatbeltVoiceAnnouncer(context: Context) {
    private val appContext = context.applicationContext
    private val pending = ArrayDeque<Int>()
    private var player: MediaPlayer? = null
    private val prompts = mapOf(
        "SAFETY_BELT_AREA_MAIN" to R.raw.seat_driver,
        "SAFETY_BELT_AREA_DEPUTY" to R.raw.seat_front_passenger,
        "SAFETY_BELT_AREA_SECOND_ROW_SEAT_LEFT" to R.raw.seat_rear_left,
        "SAFETY_BELT_AREA_SECOND_ROW_SEAT_MID" to R.raw.seat_rear_middle,
        "SAFETY_BELT_AREA_SECOND_ROW_SEAT_RIGHT" to R.raw.seat_rear_right
    )

    fun announce(seats: List<BydSeatbeltReader.Seat>) {
        seats.mapNotNull { prompts[it.key] }.forEach(pending::addLast)
        playNext()
    }

    fun release() {
        pending.clear()
        player?.setOnCompletionListener(null)
        player?.release()
        player = null
    }

    private fun playNext() {
        if (player != null || pending.isEmpty()) return
        val resource = pending.removeFirst()
        val next = MediaPlayer()
        player = next
        next.setOnCompletionListener { completed ->
            completed.release()
            player = null
            playNext()
        }
        next.setOnErrorListener { failed, _, _ ->
            failed.release()
            player = null
            playNext()
            true
        }
        runCatching {
            next.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            appContext.resources.openRawResourceFd(resource).use { audio ->
                next.setDataSource(audio.fileDescriptor, audio.startOffset, audio.length)
            }
            next.setOnPreparedListener { it.start() }
            next.prepareAsync()
        }.onFailure {
            next.release()
            player = null
            playNext()
        }
    }
}
