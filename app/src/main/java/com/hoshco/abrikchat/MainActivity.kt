package com.hoshco.abrikchat

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.hoshco.abrikchat.Api.ApiClient
import com.hoshco.abrikchat.DataClasses.DeviceInfo
import com.hoshco.abrikchat.DataClasses.ErrorResponse
import com.hoshco.abrikchat.DataClasses.LoginRequest
import com.hoshco.abrikchat.DataClasses.LoginResponse
import com.hoshco.lawyers.Data.FcmTokenRequest
import kotlinx.coroutines.launch
import retrofit2.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import android.os.Environment
import android.util.Log
import com.google.firebase.messaging.BuildConfig
import kotlinx.coroutines.Dispatchers
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var logcatProcess: Process? = null
    private var fileLoggingTree: CustomFileLoggingTree? = null
    private lateinit var editTextPhoneNumber: EditText
    private lateinit var txvchange_number: TextView
    private lateinit var txvresendcode: TextView
    private lateinit var verificationCodeLayout: LinearLayout
    private lateinit var phoneNumberLayout: ConstraintLayout
    private lateinit var buttonAction: Button
    private lateinit var digit1: EditText
    private lateinit var digit2: EditText
    private lateinit var digit3: EditText
    private lateinit var digit4: EditText
    private lateinit var smsReceiver: BroadcastReceiver
    private val permissionsToRequest = listOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private val SMS_PERMISSION_CODE = 100
    private val STORAGE_PERMISSION_CODE = 102
    private var isResendTimerRunning = false
    //private var fileLoggingTree: CustomFileLoggingTree? = null
    private val wifiManager: WifiManager by lazy {
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // تنظیم Timber برای لاگ‌گیری
        setupLogging()

        // تست لاگ
        Timber.d("برنامه شروع شد! این یه تست لاگه.")

        // Initialize UI elements
        phoneNumberLayout = findViewById(R.id.phoneNumberLayout)
        txvchange_number = findViewById(R.id.tvchange_number)
        txvresendcode = findViewById(R.id.tvresendcode)
        editTextPhoneNumber = findViewById(R.id.editTextPhoneNumber)
        verificationCodeLayout = findViewById(R.id.verificationCodeLayout)
        buttonAction = findViewById(R.id.buttonConfirm)
        digit1 = findViewById(R.id.digit1)
        digit2 = findViewById(R.id.digit2)
        digit3 = findViewById(R.id.digit3)
        digit4 = findViewById(R.id.digit4)

        Timber.d("MainActivity created")

        // Check and request permissions
        requestPermissionsSequentially(permissionsToRequest)

        // Set up button click listener
        buttonAction.setOnClickListener {
            if (verificationCodeLayout.visibility == View.GONE) {
                val phoneNumber = editTextPhoneNumber.text.toString()
                Timber.d("Button clicked: Phone number submission, phoneNumber=%s", phoneNumber)
                if (phoneNumber.isNotEmpty()) {
                    fadeOutView(phoneNumberLayout)
                    fadeInView(verificationCodeLayout)
                    fadeInView(txvchange_number)
                    fadeInView(txvresendcode)
                    startResendTimer()
                    buttonAction.text = "تایید کد"
                    digit1.requestFocus()
                    sendLoginRequest(phoneNumber, 0)
                } else {
                    Timber.w("Phone number is empty")
                    showErrorMessage("لطفاً شماره موبایل را وارد کنید")
                }
            } else {
                val verificationCode = digit1.text.toString() + digit2.text.toString() + digit3.text.toString() + digit4.text.toString()
                Timber.d("Button clicked: Verification code submission, code=%s", verificationCode)
                if (verificationCode.length == 4) {
                    val phoneNumber = editTextPhoneNumber.text.toString()
                    sendLoginRequest(phoneNumber, verificationCode.toInt())
                } else {
                    Timber.w("Verification code length is %d, expected 4", verificationCode.length)
                    showErrorMessage("لطفاً کد 4 رقمی را کامل وارد کنید")
                }
            }
        }

        // Set up TextWatcher for focus management
        setupSupposeTextWatchers()

        // Register SMS Receiver
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "SMS_CODE_RECEIVED") {
                    val code = intent.getStringExtra("code")
                    if (code != null) {
                        Timber.d("SMS code received: %s", code)
                        setVerificationCode(code)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, IntentFilter("SMS_CODE_RECEIVED"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, IntentFilter("SMS_CODE_RECEIVED"))
        }

        // Set up click listener for txvchange_number
        txvchange_number.setOnClickListener {
            Timber.d("Change number clicked")
            fadeOutView(verificationCodeLayout)
            fadeOutView(txvchange_number)
            fadeOutView(txvresendcode)
            fadeInView(phoneNumberLayout)
            buttonAction.text = "تأیید و دریافت کد"
            digit1.setText("")
            digit2.setText("")
            digit3.setText("")
            digit4.setText("")
            editTextPhoneNumber.requestFocus()
        }

        // Set up click listener for txvresendcode
        txvresendcode.setOnClickListener {
            if (!isResendTimerRunning) {
                Timber.d("Resend code clicked")
                val phoneNumber = editTextPhoneNumber.text.toString()
                if (phoneNumber.isNotEmpty()) {
                    sendLoginRequest(phoneNumber, 0)
                    startResendTimer()
                    Toast.makeText(this, "کد تأیید مجدداً ارسال شد", Toast.LENGTH_SHORT).show()
                } else {
                    showErrorMessage("شماره تلفن وارد نشده است")
                }
            }
        }

        // Check for biometric login
        checkBiometricLogin()
    }

    private fun requestPermissionsSequentially(permissions: List<String>, index: Int = 0) {
        if (index >= permissions.size) {
            // همه مجوزها درخواست شدن
            Timber.d("تمامی مجوزها درخواست شدند")
            return
        }
        val permission = permissions[index]
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), index)
        } else {
            // مجوز قبلاً داده شده، به سراغ مجوز بعدی می‌ریم
            Timber.d("$permission قبلاً اعطا شده است")
            requestPermissionsSequentially(permissions, index + 1)
        }
    }

    private fun setupLogging() {
        // حذف لاگ‌های قبلی
        if (Timber.treeCount > 0) Timber.uprootAll()

        try {
            // ایجاد لاگ‌های سیستمی
            startLogcatCapture()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting logcat capture", e)
        }

        try {
            // ایجاد لاگ‌های برنامه
            val logFile = getAppLogFile()
            val outputStream = FileOutputStream(logFile)
            // اضافه کردن خط اول
            outputStream.write("--- App Log Started at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ---\n\n".toByteArray())
            fileLoggingTree = CustomFileLoggingTree(outputStream)
            Timber.plant(fileLoggingTree!!)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating app log file", e)
        }

        // برای نمایش در Logcat
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        try {
            // تنظیم CrashHandler
            val crashLogFile = getCrashLogFile()
            // اضافه کردن خط اول به فایل crash
            FileWriter(crashLogFile).use { writer ->
                writer.write("--- Crash Log Started at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ---\n\n")
            }
            Thread.setDefaultUncaughtExceptionHandler(CustomCrashHandler(crashLogFile))
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up crash handler", e)
        }
    }

    private fun getAppLogFile(): File {
        val logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "abrikchat")
        if (!logDir.exists()) logDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(logDir, "app_log_$timestamp.txt")
    }

    private fun getCrashLogFile(): File {
        val logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "abrikchat")
        if (!logDir.exists()) logDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(logDir, "crash_log_$timestamp.txt")
    }

    private fun getLogcatFile(): File {
        val logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "abrikchat")
        if (!logDir.exists()) logDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(logDir, "system_logcat_$timestamp.txt")
    }

    private fun startResendTimer() {
        isResendTimerRunning = true
        txvresendcode.isEnabled = false
        Timber.d("Starting 120-second timer for resend code")
        object : CountDownTimer(120000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                txvresendcode.text = "ارسال مجدد\n($secondsLeft ثانیه)"
            }

            override fun onFinish() {
                isResendTimerRunning = false
                Timber.d("Timer finished, enabling resend code")
                txvresendcode.text = "ارسال مجدد کد"
                txvresendcode.isEnabled = true
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogcatCapture()
        fileLoggingTree?.close()
        unregisterReceiver(smsReceiver)
    }

    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECEIVE_SMS),
            SMS_PERMISSION_CODE
        )
    }

    private fun checkStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_CODE
        )
    }

    companion object {
        private const val LOCATION_PERMISSION_CODE = 101
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode in 0 until permissionsToRequest.size) {
            val permission = permissionsToRequest[requestCode]
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.d("$permission اعطا شد")
            } else {
                Timber.w("$permission رد شد")
                Toast.makeText(
                    this,
                    when (permission) {
                        Manifest.permission.RECEIVE_SMS -> "برای دریافت کد تأیید، دسترسی به پیامک لازم است"
                        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "برای ذخیره لاگ‌ها، دسترسی به حافظه لازم است"
                        Manifest.permission.READ_EXTERNAL_STORAGE -> "برای خواندن لاگ‌ها، دسترسی به حافظه لازم است"
                        else -> "دسترسی به $permission لازم است"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
            // درخواست مجوز بعدی
            requestPermissionsSequentially(permissionsToRequest, requestCode + 1)
        }
    }

    private fun fetchDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return "No Connection"
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            else -> "Unknown"
        }
    }

    private fun getIpAddress(context: Context, wifiManager: WifiManager): String {
        val networkType = getNetworkType(context)
        return when (networkType) {
            "WiFi" -> {
                val wifiInfo = wifiManager.connectionInfo
                val ipAddress = wifiInfo?.ipAddress ?: 0
                if (ipAddress != 0) {
                    String.format(
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                } else {
                    "0.0.0.0"
                }
            }
            "Cellular" -> {
                try {
                    val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                    while (networkInterfaces.hasMoreElements()) {
                        val networkInterface = networkInterfaces.nextElement()
                        val addresses = networkInterface.inetAddresses
                        while (addresses.hasMoreElements()) {
                            val address = addresses.nextElement()
                            if (!address.isLoopbackAddress && address is Inet4Address) {
                                return address.hostAddress
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error getting IP address")
                }
                "0.0.0.0"
            }
            else -> "0.0.0.0"
        }
    }

    private fun sendLoginRequest(phoneNumber: String, verifyCode: Int) {
        var formattedPhoneNumber = phoneNumber
        if (!phoneNumber.startsWith("0")) {
            formattedPhoneNumber = "0$phoneNumber"
        }
        Timber.d("Formatted phone number: %s", formattedPhoneNumber)

        val deviceInfo = DeviceInfo(
            deviceId = fetchDeviceId(),
            ip = getIpAddress(this@MainActivity, wifiManager)
        )
        val request = LoginRequest(formattedPhoneNumber, deviceInfo, verifyCode)
        val requestJson = Gson().toJson(request)
        Timber.d("Sending login request: %s", requestJson)

        lifecycleScope.launch {
            try {
                val response: Response<LoginResponse> = ApiClient.api.login(request)
                Timber.d("Received response: code=%d, headers=%s", response.code(), response.headers())

                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    Timber.d("Response successful: %s", loginResponse)
                    if (loginResponse?.twoStepVertification == true) {
                        showVerificationCodeLayout()
                        Toast.makeText(this@MainActivity, "کد تأیید ارسال شد", Toast.LENGTH_SHORT).show()
                    } else if (loginResponse?.AccessToken != null) {
                        saveUserData(loginResponse)
                        val domain = loginResponse.domain
                        val homepage = loginResponse.homepage
                        if (domain != null && homepage != null) {
                            val fullUrl = domain + homepage
                            storeCredentials(formattedPhoneNumber, domain, homepage)
                            openNewPageWithLink(fullUrl, loginResponse.AccessToken)
                            sendFcmTokenToServer(loginResponse.AccessToken)
                        } else {
                            showErrorMessage("لینک از سرور دریافت نشد")
                        }
                    } else {
                        showErrorMessage("پاسخ نامعتبر از سرور")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("Response failed: code=%d, errorBody=%s", response.code(), errorBody)
                    val errorResponse = Gson().fromJson(errorBody, ErrorResponse::class.java)
                    if (errorResponse?.success == false) {
                        showErrorMessage(errorResponse.message)
                    } else {
                        showErrorMessage("خطای سرور: کد ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error: %s", e.message)
                showErrorMessage("خطا در ارتباط با سرور: ${e.message}")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error: %s", e.message)
                showErrorMessage("خطای غیرمنتظره: ${e.message}")
            }
        }
    }

    private fun saveUserData(loginResponse: LoginResponse) {
        val sharedPref = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("access_token", loginResponse.AccessToken)
            putString("refresh_token", loginResponse.RefreshToken)
            putInt("user_id", loginResponse.userId ?: 0)
            putString("username", loginResponse.username)
            putString("domain", loginResponse.domain)
            putString("homepage", loginResponse.homepage)
            apply()
        }
        Timber.d("User data saved")
    }

    private fun sendFcmTokenToServer(accessToken: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                Timber.d("FCM token retrieved: %s", fcmToken)
                val deviceId = fetchDeviceId()
                val request = FcmTokenRequest(fcmToken, deviceId)
                val authHeader = "Bearer $accessToken"
                // 🔹 لاگ بدنه JSON مشابه لاگ لاگین
                val requestJson = Gson().toJson(request)
                Timber.d("Sending FCM token request: %s", requestJson)

                lifecycleScope.launch {
                    try {
                        val response = ApiClient.api.addFcmInfo(authHeader, request)
                        if (response.isSuccessful) {
                            val fcmResponse = response.body()
                            if (fcmResponse?.success == true) {
                                Timber.d("FCM token successfully sent to server")
                            } else {
                                Timber.w("FCM token send failed: Invalid server response")
                                Toast.makeText(this@MainActivity, "ارسال توکن ناموفق بود", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Timber.w("FCM token send failed: %d", response.code())
                            Toast.makeText(this@MainActivity, "ارسال توکن ناموفق بود", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error sending FCM token")
                        Toast.makeText(this@MainActivity, "خطا در ارسال توکن", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Timber.w(task.exception, "Error retrieving FCM token")
                Toast.makeText(this, "خطا در دریافت توکن FCM", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setVerificationCode(code: String) {
        if (code.length == 4) {
            digit1.setText(code[0].toString())
            digit2.setText(code[1].toString())
            digit3.setText(code[2].toString())
            digit4.setText(code[3].toString())
            val phoneNumber = editTextPhoneNumber.text.toString()
            sendLoginRequest(phoneNumber, code.toInt())
        }
    }

    private fun fadeOutView(view: View) {
        val fadeOut = AlphaAnimation(1.0f, 0.0f)
        fadeOut.duration = 100
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                Timber.d("Fade out started for view: %d", view.id)
            }
            override fun onAnimationEnd(animation: Animation?) {
                view.visibility = View.GONE
                Timber.d("Fade out completed for view: %d", view.id)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        view.startAnimation(fadeOut)
    }

    private fun fadeInView(view: View) {
        val fadeIn = AlphaAnimation(0.0f, 1.0f)
        fadeIn.duration = 500
        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                view.visibility = View.VISIBLE
                Timber.d("Fade in started for view: %d", view.id)
            }
            override fun onAnimationEnd(animation: Animation?) {
                Timber.d("Fade in completed for view: %d", view.id)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        view.startAnimation(fadeIn)
    }

    private fun setupSupposeTextWatchers() {
        digit1.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 1) {
                    Timber.d("Digit1 changed, moving focus to digit2")
                    digit2.requestFocus()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        digit2.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 1) {
                    Timber.d("Digit2 changed, moving focus to digit3")
                    digit3.requestFocus()
                } else if (s?.length == 0) {
                    Timber.d("Digit2 cleared, moving focus to digit1")
                    digit1.requestFocus()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        digit3.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 1) {
                    Timber.d("Digit3 changed, moving focus to digit4")
                    digit4.requestFocus()
                } else if (s?.length == 0) {
                    Timber.d("Digit3 cleared, moving focus to digit2")
                    digit2.requestFocus()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        digit4.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 0) {
                    Timber.d("Digit4 cleared, moving focus to digit3")
                    digit3.requestFocus()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun showErrorMessage(message: String) {
        Timber.w("Showing error message: %s", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showVerificationCodeLayout() {
        Timber.d("Showing verification code layout")
        fadeOutView(phoneNumberLayout)
        fadeInView(verificationCodeLayout)
        fadeInView(txvchange_number)
        fadeInView(txvresendcode)
        buttonAction.text = "تایید کد"
        digit1.requestFocus()
        startResendTimer()
    }

    private fun openNewPageWithLink(url: String, token: String) {
        Timber.d("Login successful, opening WebViewActivity with url: %s", url)
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra("token", token)
        startActivity(intent)
        Toast.makeText(this, "ورود موفق", Toast.LENGTH_SHORT).show()
    }

    private fun checkBiometricLogin() {
        try {
            if (isBiometricLoginEnabled() && checkBiometricAvailability()) {
                showBiometricPrompt()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in biometric login check")
            showErrorMessage("خطا در بررسی ورود بیومتریک")
        }
    }

    private fun isBiometricLoginEnabled(): Boolean {
        return try {
            getSecureSharedPreferences().getBoolean("biometric_enabled", false)
        } catch (e: Exception) {
            Timber.e(e, "Error in isBiometricLoginEnabled")
            clearSharedPreferences()
            false
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val sharedPreferences = getSecureSharedPreferences()
                val savedPhoneNumber = sharedPreferences.getString("phone_number", null)
                val savedDomain = sharedPreferences.getString("domain", null)
                val savedAccessToken = getSharedPreferences("user_data", Context.MODE_PRIVATE).getString("access_token", null) // دریافت توکن ذخیره‌شده
                if (savedPhoneNumber != null && savedDomain != null) {
                    openNewPageWithLink(savedDomain, savedAccessToken!!)
                } else {
                    showErrorMessage("خطا در ورود خودکار")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                showErrorMessage("خطا در تأیید اثر انگشت")
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود با اثر انگشت")
            .setSubtitle("لطفا اثر انگشت خود را برای ورود تأیید کنید")
            .setNegativeButtonText("انصراف")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun checkBiometricAvailability(): Boolean {
        return BiometricManager.from(this).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun storeCredentials(phoneNumber: String, domain: String, homepage: String) {
        val sharedPreferences = getSecureSharedPreferences()
        sharedPreferences.edit().apply {
            putString("phone_number", phoneNumber)
            putString("domain", domain)
            putString("homepage", homepage)
            putBoolean("biometric_enabled", true)
            apply()
        }
        Timber.d("Stored: phone_number=%s, domain=%s, homepage=%s, biometric_enabled=true", phoneNumber, domain, homepage)
    }

    private fun getSecureSharedPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                this,
                "secret_shared_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            clearSharedPreferences()
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                this,
                "secret_shared_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private fun clearSharedPreferences() {
        getSharedPreferences("secret_shared_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        deleteSharedPreferences("secret_shared_prefs")
    }

    private fun startLogcatCapture() {
        try {
            val logcatFile = getLogcatFile()

            // نوشتن خط اول در فایل logcat
            FileWriter(logcatFile).use { writer ->
                writer.write("--- Logcat Capture Started at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ---\n\n")
            }

            // متوقف کردن فرآیند قبلی اگر وجود دارد
            logcatProcess?.destroy()

            val clearProcess = ProcessBuilder()
                .command("logcat", "-c")
                .redirectErrorStream(true)
                .start()
            clearProcess.waitFor()

            val logcatProcess = ProcessBuilder()
                .command("logcat", "-v", "time", "-f", logcatFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            this.logcatProcess = logcatProcess

            lifecycleScope.launch(Dispatchers.IO) {
                logcatProcess.waitFor()
                Timber.d("Logcat capture stopped")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting logcat capture")
        }
    }

    private fun stopLogcatCapture() {
        logcatProcess?.destroy()
        logcatProcess = null
    }
}

class CustomFileLoggingTree(private val outputStream: OutputStream) : Timber.Tree() {
    private val buffer = BufferedOutputStream(outputStream)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    init {
        try {
            buffer.write("--- Custom Logging Started ---\n\n".toByteArray())
            buffer.flush()
        } catch (e: Exception) {
            Log.e("CustomFileLoggingTree", "Error writing initial log", e)
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val timeStamp = dateFormat.format(Date())
            val priorityString = when (priority) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "?"
            }

            val logMessage = "$timeStamp $priorityString/${tag ?: ""}: $message\n"
            buffer.write(logMessage.toByteArray())

            t?.let {
                buffer.write("Stack trace: ".toByteArray())
                buffer.write(it.stackTraceToString().toByteArray())
                buffer.write("\n".toByteArray())
            }

            buffer.flush()
        } catch (e: Exception) {
            Log.e("CustomFileLoggingTree", "Error writing to log file", e)
        }
    }

    fun close() {
        try {
            buffer.flush()
            buffer.close()
            outputStream.close()
        } catch (e: IOException) {
            Log.e("CustomFileLoggingTree", "Error closing stream", e)
        }
    }
}

@SuppressLint("StaticFieldLeak")
class CustomCrashHandler(private val logFile: File) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            FileWriter(logFile, true).use { writer ->
                writer.append("--- Crash at ${dateFormat.format(Date())} ---\n")
                writer.append(throwable.stackTraceToString())
                writer.append("\n--- End of Crash ---\n\n")
            }
        } catch (e: Exception) {
            Log.e("CustomCrashHandler", "Error writing crash log", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}