package com.shuli.reader.database

import android.content.Context
import androidx.room.Room
import com.shuli.reader.core.database.ShuLiDatabase

/**
 * 测试专用数据库工厂，提供内存型 Room 实例。
 * 每次调用 create() 创建独立实例，测试间互不干扰。
 */
object TestDatabaseFactory {

    fun create(context: Context): ShuLiDatabase {
        return Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            ShuLiDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }
}
