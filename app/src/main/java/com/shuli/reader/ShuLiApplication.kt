package com.shuli.reader

import android.app.Application
import com.shuli.reader.core.ShuLiAppContainer

class ShuLiApplication : Application() {
    lateinit var appContainer: ShuLiAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = ShuLiAppContainer(this)
    }
}
