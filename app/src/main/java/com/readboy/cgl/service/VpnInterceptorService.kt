package com.readboy.cgl.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.readboy.cgl.MainActivity
import com.readboy.cgl.R
import java.io.FileInputStream
import java.io.FileOutputStream

class VpnInterceptorService : VpnService() {

    companion object {
        const val EXTRA_RULES = "rules"
        const val EXTRA_KEEP_ALIVE_TYPE = "keep_alive_type"
        const val EXTRA_FILTER_PACKAGES = "filter_packages"
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "VpnInterceptor"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var interceptorThread: Thread? = null
    private var isRunning = false
    private var interceptRules: List<String> = emptyList()
    private var keepAliveType = DatabaseCleanupService.KEEPALIVE_NOTIFICATION
    private var filterPackages: Array<String>? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            intent?.let {
                val rules = it.getStringExtra(EXTRA_RULES)
                if (rules != null) {
                    interceptRules = rules.split(",").map { it.trim() }
                }
                keepAliveType = it.getIntExtra(EXTRA_KEEP_ALIVE_TYPE, DatabaseCleanupService.KEEPALIVE_NOTIFICATION)
                filterPackages = it.getStringArrayExtra(EXTRA_FILTER_PACKAGES)
            }

            startForeground(NOTIFICATION_ID, createNotification())

            if (!isRunning) {
                isRunning = true
                startVpn()
            }

            return START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun startVpn() {
        try {
            // 检查VPN权限
            val intent = VpnService.prepare(this)
            if (intent != null) {
                // 没有VPN权限，需要用户授权
                Log.e(TAG, "VPN permission required")
                // 发送广播通知UI请求权限
                val permissionIntent = Intent("com.readboy.cgl.VPN_PERMISSION_REQUIRED")
                sendBroadcast(permissionIntent)
                stopSelf()
                return
            }

            // 配置VPN
            val builder = Builder()
                .setSession("CGL VPN Interceptor")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)

            // 添加DNS服务器
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("8.8.4.4")

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                Log.d(TAG, "VPN interface established")
                interceptorThread = Thread {
                    runVpn()
                }.apply {
                    start()
                }
            } else {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN: ${e.message}", e)
            stopSelf()
        }
    }

    private fun runVpn() {
        try {
            val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)

            // 保护VpnInterface不被过早关闭
            val inputFd = vpnInterface?.fileDescriptor
            val outputFd = vpnInterface?.fileDescriptor

            if (inputFd != null && outputFd != null) {
                val buffer = ByteArray(32767)
                var readCount = 0

                while (isRunning) {
                    try {
                        val length = vpnInput.read(buffer)
                        if (length > 0) {
                            val requestData = String(buffer, 0, length)

                            if (shouldIntercept(requestData)) {
                                Log.d(TAG, "Intercepted request")
                                val response = buildErrorResponse()
                                vpnOutput.write(response.toByteArray())
                            } else {
                                // 简化实现：透传数据
                                vpnOutput.write(buffer, 0, length)
                            }
                            readCount++
                        } else if (length < 0) {
                            // EOF，重新建立连接
                            Log.d(TAG, "EOF reached, reconnecting...")
                            break
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "VPN data transfer error: ${e.message}", e)
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPN run error: ${e.message}", e)
        } finally {
            if (isRunning) {
                // 如果还在运行，尝试重启VPN
                Thread.sleep(1000)
                if (isRunning) {
                    Log.d(TAG, "Restarting VPN...")
                    vpnInterface?.close()
                    vpnInterface = null
                    interceptorThread = null
                    startVpn()
                }
            }
        }
    }

    private fun shouldIntercept(requestData: String): Boolean {
        // 检查URL是否在拦截规则中
        for (rule in interceptRules) {
            if (requestData.contains(rule)) {
                return true
            }
        }

        // 检查包名过滤
        if (filterPackages != null && filterPackages!!.isNotEmpty()) {
            for (pkg in filterPackages!!) {
                if (requestData.contains(pkg)) {
                    return true
                }
            }
        }

        return false
    }

    private fun buildErrorResponse(): String {
        return "HTTP/1.1 403 Forbidden\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 13\r\n" +
                "\r\n" +
                "Access Denied"
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        vpnInterface?.close()
        vpnInterface = null

        interceptorThread?.interrupt()
        interceptorThread = null

        Log.d(TAG, "VPN stopped")
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN拦截服务",
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
            .setContentTitle("CGL - VPN拦截")
            .setContentText("正在拦截家长管理请求...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}