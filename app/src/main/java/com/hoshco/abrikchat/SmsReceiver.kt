package com.hoshco.abrikchat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.messageBody
                Log.d("SmsReceiver", "Message received: $messageBody")
                val code = extractVerificationCode(messageBody)
                if (code != null) {
                    val broadcastIntent = Intent("SMS_CODE_RECEIVED")
                    broadcastIntent.putExtra("code", code)
                    context.sendBroadcast(broadcastIntent)
                }
            }
        }
    }

    private fun extractVerificationCode(message: String): String? {
        val regex = Regex("کد تایید شما برای ورود  به ابریک : (\\d{4})")
        val match = regex.find(message)
        return match?.groupValues?.get(1)
    }
}