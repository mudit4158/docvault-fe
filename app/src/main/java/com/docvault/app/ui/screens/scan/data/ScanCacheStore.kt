package com.docvault.app.ui.screens.scan.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * App-private cache lifecycle for a scan session.
 *
 * Every captured/edited page and the final export live only under
 * `cacheDir/scan/<sessionId>/` — never external or shared storage (a scanned
 * page can be an Aadhaar or passport, PRD §4.9). A session's directory is
 * deleted the moment it's saved or abandoned; [sweepStale] cleans up anything
 * a process death left behind, and should run once at app start, before any
 * session begins.
 */
class ScanCacheStore(context: Context) {
    private val appContext = context.applicationContext

    private val root: File get() = File(appContext.cacheDir, "scan").apply { mkdirs() }

    /**
     * Exposes a cache file as a content Uri, so the finished export can flow
     * through the exact same [com.docvault.app.data.describe] +
     * `DocVaultRepository.uploadDocument` path Vault already uses.
     */
    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)

    fun newSession(): String = UUID.randomUUID().toString()

    fun sessionDir(sessionId: String): File = File(root, sessionId).apply { mkdirs() }

    /** A fresh, uniquely-named file inside the session's directory. */
    fun newFile(sessionId: String, suffix: String = ".jpg"): File =
        File(sessionDir(sessionId), "${UUID.randomUUID()}$suffix")

    /** The one export file for a session — fixed name, since only one export exists at a time. */
    fun exportFile(sessionId: String, extension: String): File =
        File(sessionDir(sessionId), "export.$extension")

    fun delete(sessionId: String) {
        File(root, sessionId).deleteRecursively()
    }

    /** Removes every leftover session directory. Call once at app start, never mid-session. */
    fun sweepStale() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }
}
