package com.hoshco.abrikchat.Api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hoshco.abrikchat.MainActivity
import com.hoshco.abrikchat.R
import com.hoshco.lawyers.Data.FcmTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.gson.Gson
import timber.log.Timber

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID   = "fcm_default_channel"
        private const val CHANNEL_NAME = "FCM Messages"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for FCM messages"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "توکن جدید دریافت شد: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "پیام دریافت شد از: ${remoteMessage.from}")

        remoteMessage.notification?.let {
            Log.d("FCM", "Notification Title: ${it.title}, Body: ${it.body}")
        }
        showNotification(
            remoteMessage.notification?.title,
            remoteMessage.notification?.body
        )
    }

    private fun showNotification(title: String?, body: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title ?: "پیام جدید")
            .setContentText(body ?: "")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun sendTokenToServer(token: String) {
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        val request = FcmTokenRequest(token, deviceId)

        val sharedPref = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        val accessToken = sharedPref.getString("access_token", null)

        if (accessToken == null) {
            Log.w("FCM", "توکن Bearer در دسترس نیست")
            return
        }

        val authHeader = "Bearer $accessToken"

        // جدید: لاگ JSON درخواست با Timber
        val gson = Gson()
        Timber.d("FCM: Sending FCM request JSON: ${gson.toJson(request)}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.api.addFcmInfo(authHeader, request)
                if (response.isSuccessful) {
                    Log.d("FCM", "توکن جدید با موفقیت به سرور ارسال شد")
                } else {
                    Log.w("FCM", "ارسال توکن جدید ناموفق بود: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("FCM", "خطا در ارسال توکن جدید", e)
            }
        }
    }
}