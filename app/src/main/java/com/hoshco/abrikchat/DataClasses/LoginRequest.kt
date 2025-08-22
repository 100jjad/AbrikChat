package com.hoshco.abrikchat.DataClasses

data class LoginRequest(
    val userName: String,
    val deviceInfo: DeviceInfo,
    val verifyCode: Int
)