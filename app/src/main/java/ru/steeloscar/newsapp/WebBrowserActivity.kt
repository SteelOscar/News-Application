package ru.steeloscar.newsapp

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_web_browser.*
import kotlinx.android.synthetic.main.browser_layout.*

class WebBrowserActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_browser)

        setSupportActionBar(web_toolbar)
        title = "Web view"
        web_toolbar.setBackgroundColor(Color.DKGRAY)

        initWebView()

        webView.loadUrl(intent.getStringExtra("url"))
    }

    private fun initWebView() {
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient =object: WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                web_progress_bar.visibility = View.VISIBLE
                super.onPageStarted(view, url, favicon)
            }
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

//            override fun onPageFinished(view: WebView?, url: String?) {
//                web_progress_bar.visibility = View.GONE
//                super.onPageFinished(view, url)
//            }
//
//            override fun onReceivedError(
//                view: WebView?,
//                request: WebResourceRequest?,
//                error: WebResourceError?
//            ) {
//                web_progress_bar.visibility = View.GONE
//                super.onReceivedError(view, request, error)
//            }
        }

        webView.clearCache(true)
        webView.clearHistory()
        webView.settings.javaScriptEnabled = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
