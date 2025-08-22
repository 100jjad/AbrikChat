package com.hoshco.abrikchat.DataClasses

data class LoginResponse(
    val twoStepVertification: Boolean? = null,
    val verifyNumber: Int? = null,
    val AccessToken: String? = null,
    val RefreshToken: String? = null,
    val userId: Int? = null,
    val username: String? = null,
    val homepage: String? = null,
    val domain: String? = null
)