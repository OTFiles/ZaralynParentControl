package com.zaralyn.cgl.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.zaralyn.cgl.R
import com.zaralyn.cgl.service.DatabaseCleanupService
import com.zaralyn.cgl.service.VpnInterceptorService

class InterceptorFragment : Fragment() {

    private lateinit var methodSpinner: Spinner
    private lateinit var enableSwitch: Switch
    private lateinit var intervalInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView

    private var selectedMethod = 0  // 0: 数据库删除, 1: VPN拦截

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_interceptor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        methodSpinner = view.findViewById(R.id.methodSpinner)
        enableSwitch = view.findViewById(R.id.enableSwitch)
        intervalInput = view.findViewById(R.id.intervalInput)
        startButton = view.findViewById(R.id.startButton)
        stopButton = view.findViewById(R.id.stopButton)
        statusText = view.findViewById(R.id.statusText)

        setupMethodSpinner()
        setupButtons()
    }

    private fun setupMethodSpinner() {
        val methods = arrayOf("后台删除数据库", "VPN拦截请求")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, methods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        methodSpinner.adapter = adapter

        methodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedMethod = position
                updateUI()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            intervalInput.isEnabled = isChecked
        }

        startButton.setOnClickListener {
            startService()
        }

        stopButton.setOnClickListener {
            stopService()
        }
    }

    private fun updateUI() {
        when (selectedMethod) {
            0 -> {
                intervalInput.hint = "删除间隔（秒）"
                intervalInput.setText("60")
            }
            1 -> {
                intervalInput.hint = "拦截规则"
                intervalInput.setText("/api/v1/time/UploadTimeUsage,/api/v1/current_usage/upload")
            }
        }
    }

    private fun startService() {
        when (selectedMethod) {
            0 -> {
                // 数据库删除服务
                val interval = intervalInput.text.toString().toIntOrNull() ?: 60
                val intent = Intent(requireContext(), DatabaseCleanupService::class.java)
                intent.putExtra(DatabaseCleanupService.EXTRA_INTERVAL, interval * 1000L)
                requireActivity().startService(intent)
                statusText.text = "后台删除服务已启动"
            }
            1 -> {
                // VPN拦截服务
                val rules = intervalInput.text.toString()
                if (rules.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入拦截规则", Toast.LENGTH_SHORT).show()
                    return
                }
                val intent = VpnInterceptorService.prepare(requireContext())
                if (intent != null) {
                    startActivityForResult(intent, 0)
                } else {
                    val serviceIntent = Intent(requireContext(), VpnInterceptorService::class.java)
                    serviceIntent.putExtra(VpnInterceptorService.EXTRA_RULES, rules)
                    requireActivity().startService(serviceIntent)
                    statusText.text = "VPN拦截服务已启动"
                }
            }
        }
    }

    private fun stopService() {
        when (selectedMethod) {
            0 -> {
                val intent = Intent(requireContext(), DatabaseCleanupService::class.java)
                requireActivity().stopService(intent)
                statusText.text = "后台删除服务已停止"
            }
            1 -> {
                val intent = Intent(requireContext(), VpnInterceptorService::class.java)
                requireActivity().stopService(intent)
                statusText.text = "VPN拦截服务已停止"
            }
        }
    }
}