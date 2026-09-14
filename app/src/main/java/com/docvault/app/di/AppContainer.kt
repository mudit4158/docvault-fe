package com.docvault.app.di

import android.content.Context
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.data.TokenStore
import com.docvault.app.data.net.ApiProvider
import com.docvault.app.ui.screens.scan.data.ScanCacheStore

/**
 * The app's dependency graph, wired by hand.
 *
 * Deliberately not Hilt: the graph is four objects deep, and annotation
 * processing would add build time and version-alignment risk for no benefit at
 * this size. If the graph grows past a handful of scoped dependencies, this is
 * the single file to replace with a Hilt module.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val tokenStore: TokenStore by lazy { TokenStore(appContext) }
    private val apiProvider: ApiProvider by lazy { ApiProvider(tokenStore) }
    val repository: DocVaultRepository by lazy { DocVaultRepository(apiProvider, tokenStore) }

    // Eager, not lazy: a scan session's cache must be swept for leftovers
    // from a killed process before any new session can start, which means
    // "at app start" — not "whenever Scan happens to be opened first".
    val scanCacheStore: ScanCacheStore = ScanCacheStore(appContext).also { it.sweepStale() }
}
