package com.mahad.openbuddy.data.media

import android.util.Base64
import java.io.File

/** A user-picked image/video copied into app storage, ready to send with a message. */
data class StagedAttachment(
    val path: String,
    val mimeType: String,
    /** "image" or "video" — matches the media-marker kinds used in chat. */
    val kind: String,
    val displayName: String
)

/**
 * User attachments ride inside the stored message text as a trailing media
 * marker (`[[image:/path]]` / `[[video:/path]]`) — the same convention the
 * agent's generated media uses, so chat rendering is shared. When a message
 * is sent to a provider, the marker is stripped from the text and the file
 * is attached natively (vision) for the most recent user message.
 */
object AttachmentSupport {

    private val MARKER_REGEX = Regex("""\[\[(image|video):([^\]]+)]]""")

    fun markerFor(attachment: StagedAttachment): String = "\n[[${attachment.kind}:${attachment.path}]]"

    /** Splits stored content into (clean text, attachment) — attachment is null when no marker exists. */
    fun extract(content: String): Pair<String, StagedAttachment?> {
        val match = MARKER_REGEX.find(content) ?: return content to null
        val kind = match.groupValues[1]
        val path = match.groupValues[2]
        val clean = content.replace(match.value, "").trim()
        return clean to StagedAttachment(
            path = path,
            mimeType = mimeForPath(path, kind),
            kind = kind,
            displayName = File(path).name
        )
    }

    fun mimeForPath(path: String, kind: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            else -> if (kind == "video") "video/mp4" else "image/jpeg"
        }
    }

    fun loadBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) null
            else Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}
