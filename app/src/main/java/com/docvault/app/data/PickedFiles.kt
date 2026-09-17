package com.docvault.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

/** A file chosen with the system picker, described without reading its bytes. */
data class PickedFile(
    val uri: Uri,
    val name: String,
    /** -1 when the provider does not report a size. */
    val size: Long,
    val mimeType: String,
)

/**
 * Name, size and type of a picked Uri.
 *
 * The system picker grants read access to exactly the chosen files, so no
 * storage permission is needed.
 */
fun ContentResolver.describe(uri: Uri): PickedFile {
    var name = "document"
    var size = -1L
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    return PickedFile(uri, name, size, getType(uri) ?: "application/octet-stream")
}

/**
 * Streams a content Uri into the request body, reporting progress.
 *
 * Read in chunks straight from the provider, so a 20 MB file is never held in
 * memory whole.
 */
class ProgressRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mimeType: String,
    private val length: Long,
    private val onProgress: (sent: Long, total: Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mimeType.toMediaTypeOrNull()

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Can't read the selected file")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                sent += read
                onProgress(sent, length)
            }
        }
    }
}
