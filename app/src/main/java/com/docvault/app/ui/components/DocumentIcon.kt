package com.docvault.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.ui.graphics.vector.ImageVector

/** A type icon for a document row. Thumbnails are not generated yet. */
fun documentIcon(mimeType: String): ImageVector = when {
    mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf
    mimeType.startsWith("image/") -> Icons.Filled.Image
    mimeType.contains("sheet") || mimeType.contains("excel") || mimeType == "text/csv" ->
        Icons.Filled.TableChart
    else -> Icons.Filled.Description
}
