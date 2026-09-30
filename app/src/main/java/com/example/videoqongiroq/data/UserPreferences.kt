package com.example.videoqongiroq.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("video_qongiroq_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PHONE = "saved_user_phone"
        private const val KEY_NAME = "saved_user_name"
    }

    fun saveUser(phone: String, name: String) {
        prefs.edit()
            .putString(KEY_PHONE, phone)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun getUser(): User? {
        val phone = prefs.getString(KEY_PHONE, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: "Foydalanuvchi"
        return User(phone = phone, name = name, isLocalUser = true)
    }

    fun clearUser() {
        prefs.edit().clear().apply()
    }
}
