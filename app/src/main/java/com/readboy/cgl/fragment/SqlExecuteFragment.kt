package com.readboy.cgl.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.zaralyn.cgl.R
import com.zaralyn.cgl.util.SqlInjectionUtil

class SqlExecuteFragment : Fragment() {

    private lateinit var sqlInput: EditText
    private lateinit var executeButton: Button
    private lateinit var clearButton: Button
    private lateinit var resultOutput: TextView
    private lateinit var resultScrollView: ScrollView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sql_execute, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sqlInput = view.findViewById(R.id.sqlInput)
        executeButton = view.findViewById(R.id.executeButton)
        clearButton = view.findViewById(R.id.clearButton)
        resultOutput = view.findViewById(R.id.resultOutput)
        resultScrollView = view.findViewById(R.id.resultScrollView)

        executeButton.setOnClickListener {
            executeSql()
        }

        clearButton.setOnClickListener {
            sqlInput.text.clear()
            resultOutput.text = ""
        }
    }

    private fun executeSql() {
        val sql = sqlInput.text.toString().trim()
        if (sql.isEmpty()) {
            resultOutput.text = "请输入SQL语句"
            return
        }

        val result = SqlInjectionUtil.executeSql(requireContext(), sql)
        resultOutput.text = result
    }
}