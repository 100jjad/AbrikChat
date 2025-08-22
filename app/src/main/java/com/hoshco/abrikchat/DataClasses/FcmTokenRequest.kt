package com.hoshco.lawyers.Data


data class FcmTokenRequest(
    val fcmToken: String,
    val deviceId: String
)

data class FcmResponse(
    val success: Boolean
)