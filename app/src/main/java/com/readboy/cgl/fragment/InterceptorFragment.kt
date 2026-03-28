package com.readboy.cgl.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.readboy.cgl.R
import com.readboy.cgl.service.*

class InterceptorFragment : Fragment() {

    private var currentFunction = FUNCTION_DELETE
    private var currentKeepAlive = KEEPALIVE_NOTIFICATION
    private var vpnPermissionRequestCode = 100

    // 服务运行状态
    private var isCleanupRunning = false
    private var isMonitorRunning = false
    private var isVpnRunning = false

    // UI组件
    private lateinit var btnDatabaseDelete: MaterialButton
    private lateinit var btnDatabaseMonitor: MaterialButton
    private lateinit var btnVpnIntercept: MaterialButton
    private lateinit var rbNotification: MaterialRadioButton
    private lateinit var rbJobScheduler: MaterialRadioButton
    private lateinit var rbWorkManager: MaterialRadioButton
    private lateinit var filterAppSwitch: SwitchMaterial
    private lateinit var filterPackagesInput: TextInputEditText
    private lateinit var paramLayout: TextInputLayout
    private lateinit var paramInput: TextInputEditText
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var statusText: TextView

    companion object {
        const val FUNCTION_DELETE = 0
        const val FUNCTION_MONITOR = 1
        const val FUNCTION_VPN = 2

        const val KEEPALIVE_NOTIFICATION = 0
        const val KEEPALIVE_JOBSCHEDULER = 1
        const val KEEPALIVE_WORKMANAGER = 2
    }

    private val vpnPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.readboy.cgl.VPN_PERMISSION_REQUIRED") {
                Toast.makeText(requireContext(), "需要VPN权限，请手动启动VPN", Toast.LENGTH_LONG).show()
                statusText.text = "VPN需要权限，请手动授权"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_interceptor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        updateUI()
        registerReceiver()

        // 检查服务状态
        checkServicesStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(vpnPermissionReceiver)
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter("com.readboy.cgl.VPN_PERMISSION_REQUIRED")
        requireContext().registerReceiver(vpnPermissionReceiver, filter)
    }

    private fun initViews(view: View) {
        btnDatabaseDelete = view.findViewById(R.id.btnDatabaseDelete)
        btnDatabaseMonitor = view.findViewById(R.id.btnDatabaseMonitor)
        btnVpnIntercept = view.findViewById(R.id.btnVpnIntercept)

        rbNotification = view.findViewById(R.id.rbNotification)
        rbJobScheduler = view.findViewById(R.id.rbJobScheduler)
        rbWorkManager = view.findViewById(R.id.rbWorkManager)

        filterAppSwitch = view.findViewById(R.id.filterAppSwitch)
        filterPackagesInput = view.findViewById(R.id.filterPackagesInput)

        paramLayout = view.findViewById(R.id.paramLayout)
        paramInput = view.findViewById(R.id.paramInput)

        startButton = view.findViewById(R.id.startButton)
        stopButton = view.findViewById(R.id.stopButton)
        statusText = view.findViewById(R.id.statusText)

        // 默认选中
        rbNotification.isChecked = true
        btnDatabaseDelete.isChecked = true
    }

    private fun setupListeners() {
        // 功能切换
        btnDatabaseDelete.setOnClickListener {
            currentFunction = FUNCTION_DELETE
            btnDatabaseDelete.isChecked = true
            btnDatabaseMonitor.isChecked = false
            btnVpnIntercept.isChecked = false
            updateUI()
        }

        btnDatabaseMonitor.setOnClickListener {
            currentFunction = FUNCTION_MONITOR
            btnDatabaseDelete.isChecked = false
            btnDatabaseMonitor.isChecked = true
            btnVpnIntercept.isChecked = false
            updateUI()
        }

        btnVpnIntercept.setOnClickListener {
            currentFunction = FUNCTION_VPN
            btnDatabaseDelete.isChecked = false
            btnDatabaseMonitor.isChecked = false
            btnVpnIntercept.isChecked = true
            updateUI()
        }

        // 保活机制切换
        view?.findViewById<RadioGroup>(R.id.keepAliveRadioGroup)?.setOnCheckedChangeListener { _, checkedId ->
            currentKeepAlive = when (checkedId) {
                R.id.rbNotification -> KEEPALIVE_NOTIFICATION
                R.id.rbJobScheduler -> KEEPALIVE_JOBSCHEDULER
                R.id.rbWorkManager -> KEEPALIVE_WORKMANAGER
                else -> KEEPALIVE_NOTIFICATION
            }
        }

        // 过滤开关
        filterAppSwitch.setOnCheckedChangeListener { _, isChecked ->
            filterPackagesInput.isEnabled = isChecked
        }

        // 启动/停止按钮
        startButton.setOnClickListener {
            startService()
        }

        stopButton.setOnClickListener {
            stopService()
        }
    }

    private fun updateUI() {
        when (currentFunction) {
            FUNCTION_DELETE -> {
                paramLayout.hint = "删除间隔（秒）"
                paramInput.setText("60")
                paramInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                updateStatusDisplay()
            }
            FUNCTION_MONITOR -> {
                paramLayout.hint = "监控间隔（秒）"
                paramInput.setText("30")
                paramInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                updateStatusDisplay()
            }
            FUNCTION_VPN -> {
                paramLayout.hint = "拦截规则（逗号分隔）"
                paramInput.setText("/api/v1/time/UploadTimeUsage,/api/v1/current_usage/upload")
                paramInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                updateStatusDisplay()
            }
        }
    }

    private fun updateStatusDisplay() {
        val status = StringBuilder()
        if (isCleanupRunning) status.append("数据库删除运行中 | ")
        if (isMonitorRunning) status.append("数据库监控运行中 | ")
        if (isVpnRunning) status.append("VPN拦截运行中")

        if (status.isEmpty()) {
            statusText.text = "服务未运行"
        } else {
            statusText.text = status.toString()
        }
    }

    private fun checkServicesStatus() {
        // 检查各个服务是否在运行
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)

        for (service in runningServices) {
            when (service.service.className) {
                DatabaseCleanupService::class.java.name -> isCleanupRunning = true
                DatabaseMonitorService::class.java.name -> isMonitorRunning = true
                VpnInterceptorService::class.java.name -> isVpnRunning = true
            }
        }

        updateStatusDisplay()
    }

    private fun startService() {
        val interval = paramInput.text.toString().toLongOrNull() ?: 60L
        val packages = if (filterAppSwitch.isChecked) {
            filterPackagesInput.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        when (currentFunction) {
            FUNCTION_DELETE -> startDatabaseCleanupService(interval, packages)
            FUNCTION_MONITOR -> startDatabaseMonitorService(interval, packages)
            FUNCTION_VPN -> startVpnInterceptorService()
        }
    }

    private fun startDatabaseCleanupService(interval: Long, packages: List<String>) {
        try {
            val intent = Intent(requireContext(), DatabaseCleanupService::class.java).apply {
                putExtra(DatabaseCleanupService.EXTRA_INTERVAL, interval * 1000L)
                putExtra(DatabaseCleanupService.EXTRA_KEEP_ALIVE_TYPE, currentKeepAlive)
                putExtra(DatabaseCleanupService.EXTRA_FILTER_PACKAGES, packages.toTypedArray())
            }
            requireActivity().startService(intent)
            isCleanupRunning = true
            updateStatusDisplay()
            Toast.makeText(requireContext(), "数据库删除服务已启动", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDatabaseMonitorService(interval: Long, packages: List<String>) {
        try {
            val intent = Intent(requireContext(), DatabaseMonitorService::class.java).apply {
                putExtra(DatabaseMonitorService.EXTRA_INTERVAL, interval * 1000L)
                putExtra(DatabaseMonitorService.EXTRA_KEEP_ALIVE_TYPE, currentKeepAlive)
                putExtra(DatabaseMonitorService.EXTRA_FILTER_PACKAGES, packages.toTypedArray())
            }
            requireActivity().startService(intent)
            isMonitorRunning = true
            updateStatusDisplay()
            Toast.makeText(requireContext(), "数据库监控服务已启动", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVpnInterceptorService() {
        try {
            val rules = paramInput.text.toString()
            if (rules.isEmpty()) {
                Toast.makeText(requireContext(), "请输入拦截规则", Toast.LENGTH_SHORT).show()
                return
            }

            val packages = if (filterAppSwitch.isChecked) {
                filterPackagesInput.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            // 检查VPN权限
            val intent = VpnService.prepare(requireContext())
            if (intent != null) {
                // 需要请求VPN权限
                startActivityForResult(intent, vpnPermissionRequestCode)
            } else {
                // 已有权限，直接启动
                val serviceIntent = Intent(requireContext(), VpnInterceptorService::class.java).apply {
                    putExtra(VpnInterceptorService.EXTRA_RULES, rules)
                    putExtra(VpnInterceptorService.EXTRA_KEEP_ALIVE_TYPE, currentKeepAlive)
                    putExtra(VpnInterceptorService.EXTRA_FILTER_PACKAGES, packages.toTypedArray())
                }
                requireActivity().startService(serviceIntent)
                isVpnRunning = true
                updateStatusDisplay()
                Toast.makeText(requireContext(), "VPN拦截服务已启动", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == vpnPermissionRequestCode) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                // 用户授权了VPN权限，重新启动VPN
                startVpnInterceptorService()
            } else {
                Toast.makeText(requireContext(), "VPN权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopService() {
        when (currentFunction) {
            FUNCTION_DELETE -> {
                val intent = Intent(requireContext(), DatabaseCleanupService::class.java)
                requireActivity().stopService(intent)
                isCleanupRunning = false
                updateStatusDisplay()
            }
            FUNCTION_MONITOR -> {
                val intent = Intent(requireContext(), DatabaseMonitorService::class.java)
                requireActivity().stopService(intent)
                isMonitorRunning = false
                updateStatusDisplay()
            }
            FUNCTION_VPN -> {
                val intent = Intent(requireContext(), VpnInterceptorService::class.java)
                requireActivity().stopService(intent)
                isVpnRunning = false
                updateStatusDisplay()
            }
        }
    }
}