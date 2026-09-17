package com.docvault.app

import android.app.Application
import com.docvault.app.di.AppContainer

class DocVaultApplication : Application() {

    /** The app's dependency graph. See [AppContainer]. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
