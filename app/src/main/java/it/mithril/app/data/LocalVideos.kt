package it.mithril.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Video presi dalla memoria del telefono: Mithril non li copia, tiene il permesso di lettura
 * persistente sul file scelto e ne salva solo la miniatura.
 */
object LocalVideos {
    const val PREFIX = "local:"
    const val CHANNEL = "Sul telefono"

    suspend fun import(context: Context, uri: Uri): VideoMeta = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val id = UUID.randomUUID().toString()
        var duration: Int? = null
        var thumb = ""
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { (it / 1000).toInt() }
            val at = ((duration ?: 0) * 150_000L).coerceIn(1_000_000L, 20_000_000L)
            val frame = (if (Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270) else null)
                ?: mmr.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, 480, 480 * it.height / it.width.coerceAtLeast(1), true) }
            if (frame != null) {
                val file = File(thumbDir(context), "$id.jpg")
                file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                thumb = Uri.fromFile(file).toString()
            }
        } catch (_: Exception) {
        } finally { runCatching { mmr.release() } }
        VideoMeta(
            youtubeId = PREFIX + id,
            title = name?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.ifBlank { null } ?: "Video dal telefono",
            channel = CHANNEL,
            durationSec = duration,
            thumbnailUrl = thumb,
            embeddable = true,
            localUri = uri.toString(),
        )
    }

    /** Il file è ancora leggibile? (può essere stato cancellato o spostato). */
    fun readable(context: Context, uri: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    fun mimeType(context: Context, uri: String): String = context.contentResolver.getType(Uri.parse(uri)) ?: "video/mp4"

    fun forget(context: Context, v: VideoEntity) {
        val uri = v.localUri ?: return
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (v.thumbnailUrl.startsWith("file:")) runCatching { File(Uri.parse(v.thumbnailUrl).path!!).delete() }
    }

    private fun thumbDir(context: Context) = File(context.filesDir, "thumbs").apply { mkdirs() }
}
