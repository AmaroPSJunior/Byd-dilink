package com.byd.carcontrol

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks explicit Portuguese seatbelt warnings; avoids misidentified bundled audio. */
class SeatbeltVoiceAnnouncer(context: Context) {
    private var ready = false
    private val pending = mutableListOf<String>()
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun announce(seats: List<BydSeatbeltReader.Seat>) {
        seats.filter { it.raw == 2 }.forEach {
            pending.add("Atenção. Cinto do assento ${it.label} desafivelado. Por favor, afivele o cinto.")
        }
        if (ready) {
            tts.language = Locale("pt", "BR")
            pending.forEachIndexed { index, phrase ->
                tts.speak(phrase, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "belt_${index}")
            }
            pending.clear()
        }
    }

    fun stop() {
        pending.clear()
        tts.stop()
    }

    fun release() {
        stop()
        tts.shutdown()
    }
}
