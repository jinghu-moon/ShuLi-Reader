package com.shuli.reader

import android.app.Application
import android.os.StrictMode
import com.shuli.reader.core.ShuLiAppContainer

class ShuLiApplication : Application() {
    lateinit var appContainer: ShuLiAppContainer
        private set

    override fun onCreate() {
        applyStrictModeIfDebug()
        super.onCreate()
        appContainer = ShuLiAppContainer(this)
    }

    /**
     * 仅在 debug 包启用 StrictMode：及早发现主线程 IO/网络阻塞与 Activity/Closeable 泄漏。
     * release 包零开销。
     */
    private fun applyStrictModeIfDebug() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }
}
