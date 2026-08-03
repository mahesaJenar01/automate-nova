package com.nova.automate

import android.content.Context
import org.json.JSONObject
import java.io.File

object ProductDatabaseManager {
    private const val CUSTOM_FILE_NAME = "custom_products.json"

    fun getProducts(context: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()

        // 1. Read from assets
        try {
            val jsonString = context.assets.open("json/products.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.getString(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Read from internal storage (overrides assets if conflicts exist)
        try {
            val file = File(context.filesDir, CUSTOM_FILE_NAME)
            if (file.exists()) {
                val jsonString = file.readText()
                val jsonObject = JSONObject(jsonString)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObject.getString(key)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return map
    }

    fun saveProduct(context: Context, name: String, code: String) {
        try {
            val file = File(context.filesDir, CUSTOM_FILE_NAME)
            val jsonObject = if (file.exists()) {
                JSONObject(file.readText())
            } else {
                JSONObject()
            }
            
            jsonObject.put(name, code)
            file.writeText(jsonObject.toString(4)) // 4 spaces indent
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
