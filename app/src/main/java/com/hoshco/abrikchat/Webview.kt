package com.hoshco.abrikchat

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var loadCount = 0
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ابتدا setContentView را فراخوانی کنید
        setContentView(R.layout.activity_webview)

        // سپس تنظیمات تمام صفحه را اعمال کنید
        setupFullscreenWindow()

        webView = findViewById(R.id.wv_homepage)
        webView.visibility = View.INVISIBLE

        val webUrl = intent.getStringExtra("url")
        val token = intent.getStringExtra("token")

        if (!webUrl.isNullOrEmpty() && !token.isNullOrEmpty()) {
            setupWebView(webView, webUrl, token)
        } else {
            Toast.makeText(this, "آدرس یا توکن معتبر نیست", Toast.LENGTH_SHORT).show()
            finish()
        }

        // مدیریت منطقه بریدگی
        handleCutoutArea()
    }

    private fun setupFullscreenWindow() {
        // تنظیم پرچم‌های ضروری قبل از ایجاد ویو
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // مخفی کردن action bar
        supportActionBar?.hide()

        // تنظیم رنگ‌ها به شفاف
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // مدیریت منطقه بریدگی
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // حذف هر گونه محدودیت سیستم در لبه‌های پنجره
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // استفاده از WindowInsetsControllerCompat برای سازگاری بهتر
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // برای نسخه‌های قدیمی‌تر
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LOW_PROFILE
                    )
        }
    }

    private fun handleCutoutArea() {
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            // بدون هیچ padding اضافه
            view.setPadding(0, 0, 0, 0)
            insets
        }
    }

    private fun setupWebView(webView: WebView, url: String, token: String) {
        val webSettings: WebSettings = webView.settings
        webSettings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowContentAccess = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            displayZoomControls = false
            builtInZoomControls = false
            databaseEnabled = true
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            blockNetworkLoads = false
            blockNetworkImage = false
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    // تزریق CSS برای پوشش کامل صفحه
                    injectFullscreenCSS()
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                // فعال‌سازی حالت تمام صفحه برای ویدئوها
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                super.onShowCustomView(view, callback)
            }

            override fun onHideCustomView() {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                super.onHideCustomView()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@WebViewActivity.filePathCallback?.onReceiveValue(null)
                this@WebViewActivity.filePathCallback = filePathCallback
                val mimeTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                filePickerLauncher.launch(mimeTypes.joinToString(","))
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebView", "Page started: $url, loadCount: $loadCount")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("WebView", "Page finished: $url, loadCount: $loadCount")

                when (loadCount) {
                    1 -> {
                        // لود اول: تنظیم توکن و لود دوباره صفحه پس از اطمینان از اجرای JS
                        val jsCode = """
                            localStorage.clear();
                            localStorage.setItem('token', '$token');
                            localStorage.setItem('isAuthenticated', 'true');
                            'done'; // بازگشت برای callback
                        """.trimIndent()
                        webView.evaluateJavascript(jsCode, object : ValueCallback<String> {
                            override fun onReceiveValue(value: String?) {
                                Log.d("WebView", "JS evaluation completed: $value")
                                // لود دوم
                                loadCount = 2
                                webView.loadUrl(url!!)
                            }
                        })
                    }
                    2 -> {
                        // لود دوم: تاخیر 2 ثانیه و سپس لود سوم
                        webView.postDelayed({
                            loadCount = 3
                            webView.loadUrl(url!!)
                            if (view != null) {
                                //Toast.makeText(this@WebViewActivity, "view.reload()", Toast.LENGTH_SHORT).show()
                                view.reload()
                            }
                        }, 2000)
                    }
                    3 -> {
                        // لود سوم: تزریق CSS و نمایش با تاخیر 5 ثانیه
                        injectFullscreenCSS()
                        webView.postDelayed({
                            webView.visibility = View.VISIBLE
                            
                        }, 5000)
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.e("WebViewError", "SSL Error: ${error?.primaryError}")
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.let { view?.loadUrl(it) }
                return true
            }
        }

        // Pre-initialize the web content
        webView.loadUrl("about:blank")

        // بارگذاری URL اولیه (لود اول)
        loadCount = 1
        webView.loadUrl(url)

        // تنظیم اولیه ویو برای حالت تمام صفحه
        webView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LOW_PROFILE
                )
    }

    private fun injectFullscreenCSS() {
        val css = """
            body {
                margin: 0 !important;
                padding: 0 !important;
                background-color: black !important;
            }
            header, .status-bar, .notification-bar {
                display: none !important;
            }
        """.trimIndent()

        val js = """
            var style = document.createElement('style');
            style.innerHTML = `${css}`;
            document.head.appendChild(style);
            
            // حذف هرگونه عنصر ثابت در بالای صفحه
            document.querySelectorAll('header, .fixed-top, .status-bar').forEach(el => {
                el.style.display = 'none';
            });
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    override fun onResume() {
        super.onResume()
        // اطمینان از حفظ حالت تمام صفحه
        setupFullscreenWindow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreenWindow()
            webView?.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LOW_PROFILE
                    )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // هنگام چرخش دستگاه تنظیمات را اعمال کنید
        setupFullscreenWindow()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_REQUEST_CODE && filePathCallback != null) {
            val result = if (resultCode == RESULT_OK && data != null) {
                arrayOf(data.data ?: Uri.EMPTY)
            } else {
                null
            }
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        }
    }

    companion object {
        private const val FILE_REQUEST_CODE = 1
    }
}