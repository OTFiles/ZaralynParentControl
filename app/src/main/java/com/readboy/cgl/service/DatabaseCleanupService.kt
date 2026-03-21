package com.readboy.cgl.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.readboy.cgl.MainActivity
import com.readboy.cgl.R
import com.readboy.cgl.util.SqlInjectionUtil

class DatabaseCleanupService : Service() {

    companion object {
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_KEEP_ALIVE_TYPE = "keep_alive_type"
        const val EXTRA_FILTER_PACKAGES = "filter_packages"
        private const val CHANNEL_ID = "cleanup_channel"
        private const val NOTIFICATION_ID = 1

        const val KEEPALIVE_NOTIFICATION = 0
        const val KEEPALIVE_JOBSCHEDULER = 1
        const val KEEPALIVE_WORKMANAGER = 2
    }

    private var cleanupInterval = 60000L  // 默认60秒
    private var keepAliveType = KEEPALIVE_NOTIFICATION
    private var filterPackages: Array<String>? = null
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                cleanupDatabase()
                handler.postDelayed(this, cleanupInterval)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            cleanupInterval = it.getLongExtra(EXTRA_INTERVAL, 60000L)
            keepAliveType = it.getIntExtra(EXTRA_KEEP_ALIVE_TYPE, KEEPALIVE_NOTIFICATION)
            filterPackages = it.getStringArrayExtra(EXTRA_FILTER_PACKAGES)
        }

        startForeground(NOTIFICATION_ID, createNotification())

        if (!isRunning) {
            isRunning = true
            handler.post(cleanupRunnable)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(cleanupRunnable)
    }

    private fun cleanupDatabase() {
        try {
            // 根据是否过滤包名构建不同的SQL
            if (filterPackages != null && filterPackages!!.isNotEmpty()) {
                // 删除指定APP的使用记录
                val packageList = filterPackages!!.joinToString("','", "'", "'")

                val sql1 = "DELETE FROM app_record WHERE package_name IN ($packageList)"
                SqlInjectionUtil.executeSql(this, sql1)

                val sql2 = "DELETE FROM use_situation WHERE package_name IN ($packageList)"
                SqlInjectionUtil.executeSql(this, sql2)

                val sql3 = "DELETE FROM summary_time WHERE package_name IN ($packageList)"
                SqlInjectionUtil.executeSql(this, sql3)
            } else {
                // 删除所有应用使用记录
                val sql1 = "DELETE FROM app_record"
                SqlInjectionUtil.executeSql(this, sql1)

                val sql2 = "DELETE FROM use_situation"
                SqlInjectionUtil.executeSql(this, sql2)

                val sql3 = "DELETE FROM summary_time"
                SqlInjectionUtil.executeSql(this, sql3)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "数据清理服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CGL - 数据清理")
            .setContentText("正在清理家长管理数据...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}