package com.byd.carcontrol

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Repeats named seatbelt warnings until a verified buckled reading clears them. */
class SeatbeltVoiceAnnouncer(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var active = emptyList<String>()
    private var nextIndex = 0
    private var generation = 0
    private var speaking = false
    private var closed = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
        handler.post {
            if (!closed && status == TextToSpeech.SUCCESS) {
                ready = true
                tts.language = Locale("pt", "BR")
                speakNext()
            }
        }
    }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?) = finish(utteranceId)
            private fun finish(id: String?) {
                handler.post {
                    if (id == generation.toString()) {
                        speaking = false
                        handler.postDelayed({ speakNext() }, 2000L)
                    }
                }
            }
        })
    }

    fun setActive(seats: List<BydSeatbeltReader.Seat>) {
        handler.post {
            if (closed) return@post
            val newActive = seats.filter { it.raw == 2 }.map { it.label }.distinct()
            if (newActive == active) return@post
            active = newActive
            nextIndex = 0
            generation++
            speaking = false
            tts.stop()
            handler.removeCallbacksAndMessages(null)
            speakNext()
        }
    }

    fun announce(seats: List<BydSeatbeltReader.Seat>) = setActive(seats)

    private fun speakNext() {
        if (!ready || closed || speaking || active.isEmpty()) return
        val label = active[nextIndex % active.size]
        nextIndex++
        speaking = true
        val id = (++generation).toString()
        tts.speak("Por favor, afivele o cinto do assento ${label}.", TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() = setActive(emptyList())

    fun release() {
        handler.post {
            closed = true
            active = emptyList()
            generation++
            handler.removeCallbacksAndMessages(null)
            tts.stop()
            tts.shutdown()
        }
    }
}
