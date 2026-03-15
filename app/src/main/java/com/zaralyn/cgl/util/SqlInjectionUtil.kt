package com.zaralyn.cgl.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

object SqlInjectionUtil {

    private const val AUTHORITY = "com.readboy.parentmanager.provider"

    fun executeSql(context: Context, sql: String): String {
        return try {
            val uri = Uri.parse("content://$AUTHORITY/raw_sql")
            val cursor = context.contentResolver.query(uri, null, sql, null, null)

            if (cursor != null) {
                val result = StringBuilder()
                val columns = cursor.columnNames

                // 表头
                result.append(columns.joinToString("\t"))
                result.append("\n")

                // 数据行
                while (cursor.moveToNext()) {
                    val row = columns.map { columnName ->
                        val index = cursor.getColumnIndex(columnName)
                        cursor.getString(index) ?: "NULL"
                    }.joinToString("\t")
                    result.append(row).append("\n")
                }

                cursor.close()

                if (result.isEmpty()) {
                    "SQL执行成功，无返回结果"
                } else {
                    result.toString()
                }
            } else {
                "SQL执行成功，无返回结果"
            }
        } catch (e: Exception) {
            "SQL执行失败: ${e.message}"
        }
    }
}
