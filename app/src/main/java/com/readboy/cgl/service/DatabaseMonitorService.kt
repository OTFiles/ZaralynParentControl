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

class DatabaseMonitorService : Service() {

    companion object {
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_KEEP_ALIVE_TYPE = "keep_alive_type"
        const val EXTRA_FILTER_PACKAGES = "filter_packages"
        private const val CHANNEL_ID = "monitor_channel"
        private const val NOTIFICATION_ID = 3
    }

    private var monitorInterval = 30000L  // 默认30秒
    private var keepAliveType = DatabaseCleanupService.KEEPALIVE_NOTIFICATION
    private var filterPackages: Array<String>? = null
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                monitorDatabase()
                handler.postDelayed(this, monitorInterval)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            monitorInterval = it.getLongExtra(EXTRA_INTERVAL, 30000L)
            keepAliveType = it.getIntExtra(EXTRA_KEEP_ALIVE_TYPE, DatabaseCleanupService.KEEPALIVE_NOTIFICATION)
            filterPackages = it.getStringArrayExtra(EXTRA_FILTER_PACKAGES)
        }

        startForeground(NOTIFICATION_ID, createNotification())

        if (!isRunning) {
            isRunning = true
            handler.post(monitorRunnable)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
    }

    private fun monitorDatabase() {
        try {
            // 监控app_record表
            val sql1 = buildSql("app_record", "SELECT package_name, COUNT(*) as count FROM app_record")
            val result1 = SqlInjectionUtil.executeSql(this, sql1)

            // 监控use_situation表
            val sql2 = buildSql("use_situation", "SELECT package_name, use_time FROM use_situation")
            val result2 = SqlInjectionUtil.executeSql(this, sql2)

            // 记录监控结果（可以保存到日志或发送通知）
            val summary = buildString {
                append("数据库监控结果:\n")
                append("App记录: ${if (result1.contains("SQL执行失败")) "查询失败" else "正常"}\n")
                append("使用情况: ${if (result2.contains("SQL执行失败")) "查询失败" else "正常"}")
            }

            // 可以选择是否显示通知
            // showMonitoringNotification(summary)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildSql(table: String, baseSql: String): String {
        return if (filterPackages != null && filterPackages!!.isNotEmpty()) {
            val packageList = filterPackages!!.joinToString("','", "'", "'")
            "$baseSql WHERE package_name IN ($packageList)"
        } else {
            baseSql
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "数据库监控服务",
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
            .setContentTitle("CGL - 数据库监控")
            .setContentText("正在监控家长管理数据库...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
