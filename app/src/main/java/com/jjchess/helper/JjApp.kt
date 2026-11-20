package com.jjchess.helper

import android.app.Application

/**
 * 应用入口。全局单例在这里初始化，供无障碍服务、悬浮窗、Activity 共用。
 */
class JjApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        lateinit var instance: JjApp
            private set
    }
}
