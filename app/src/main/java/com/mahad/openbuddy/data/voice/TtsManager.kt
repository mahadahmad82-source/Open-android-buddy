package com.mahad.openbuddy.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Phase 5 — text-to-speech for spoken replies and the hands-free voice loop.
 * Wraps the platform TextToSpeech engine; degrades quietly if no engine exists.
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile
    private var ready = false

    var onSpeakingFinished: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    ready = true
                }
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("ob-final") == true) {
                        onSpeakingFinished?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId?.startsWith("ob-final") == true) {
                        onSpeakingFinished?.invoke()
                    }
                }
            })
        } catch (_: Exception) {
            tts = null
        }
    }

    fun isReady(): Boolean = ready

    /** Speaks the text, splitting long replies into sentence-sized utterances. */
    fun speak(rawText: String) {
        val engine = tts ?: return
        if (!ready) return
        val cleaned = cleanForSpeech(rawText)
        if (cleaned.isBlank()) {
            onSpeakingFinished?.invoke()
            return
        }
        val chunks = chunk(cleaned)
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val id = if (index == chunks.lastIndex) "ob-final-${UUID.randomUUID()}" else "ob-${UUID.randomUUID()}"
            engine.speak(chunk, mode, null, id)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) { /* engine gone */ }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) { /* ignore */ }
        tts = null
        ready = false
    }

    private fun cleanForSpeech(text: String): String {
        var out = text
        // Drop tool/media markers and code blocks — they are not speakable.
        out = out.replace(Regex("\\[\\[(image|video):[^]]+]]"), "")
        out = out.replace(Regex("```[\\s\\S]*?```"), " (code block omitted) ")
        out = out.replace(Regex("[#>*_`~|]"), "")
        out = out.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        return out.replace(Regex("\\s+"), " ").trim()
    }

    private fun chunk(text: String, maxLen: Int = 350): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (current.length + sentence.length > maxLen && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
            }
            if (sentence.length > maxLen) {
                // Hard-split very long sentences.
                var remaining = sentence
                while (remaining.length > maxLen) {
                    chunks.add(remaining.take(maxLen))
                    remaining = remaining.drop(maxLen)
                }
                current.append(remaining)
            } else {
                current.append(sentence).append(" ")
            }
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())
        return chunks.ifEmpty { listOf(text.take(maxLen)) }
    }
}
