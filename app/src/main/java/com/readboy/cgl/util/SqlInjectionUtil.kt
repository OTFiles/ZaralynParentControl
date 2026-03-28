package com.readboy.cgl.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

object SqlInjectionUtil {

    private const val AUTHORITY = "com.readboy.parentmanager.AppContentProvider"

    fun executeSql(context: Context, sql: String): String {
        return try {
            // 使用正确的ContentProvider authority
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
            "SQL执行失败: ${e.message}\n请检查：\n1. ParentManager应用是否已安装\n2. SQL语法是否正确\n3. 表名是否正确"
        }
    }

    // 测试数据库连接
    fun testConnection(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://$AUTHORITY/raw_sql")
            val cursor = context.contentResolver.query(uri, null, "SELECT 1", null, null)
            val connected = cursor != null
            cursor?.close()
            connected
        } catch (e: Exception) {
            false
        }
    }
}