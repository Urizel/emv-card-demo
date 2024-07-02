package net.urizel.cardkit

import timber.log.Timber

class Application: android.app.Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
    }
}
