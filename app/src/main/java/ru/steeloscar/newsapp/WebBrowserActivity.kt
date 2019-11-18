package ru.steeloscar.newsapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_web_browser.*
import kotlinx.android.synthetic.main.browser_layout.*
import kotlinx.android.synthetic.main.connection_type_layout.*

class WebBrowserActivity : AppCompatActivity() {

    private var urlSite: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_browser)

        setSupportActionBar(web_toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        web_toolbar.setBackgroundColor(Color.DKGRAY)
        web_toolbar.setNavigationIcon(R.drawable.ic_close_browser_24dp)
        web_toolbar.setNavigationOnClickListener {
            finish()
        }
        initWebView()

        webView.loadUrl(intent.getStringExtra("url"))

        relativeLayout.setOnLongClickListener {
            val clipboard = this@WebBrowserActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("",urlSite)
            clipboard.setPrimaryClip(clipData)
            Toast.makeText(this@WebBrowserActivity, "URL скопирован.", Toast.LENGTH_SHORT).show()
            true
        }

        toolbarImageConnection.setOnClickListener {
            val spanText = SpannableStringBuilder(urlSite)
            var end = 5
            var color = Color.rgb(0,128,0)

            if (!urlSite?.contains("https://")!!) {
                end = 4
                color = Color.rgb(128,0,0)
                spanText.setSpan(StrikethroughSpan(),0,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                type_connection.text = getString(R.string.http_connection)
                description_connection.text = getString(R.string.http_description)
            }

            val spanStyle1 = ForegroundColorSpan(color)
            val spanStyle2 = StyleSpan(Typeface.BOLD)
            spanText.setSpan(spanStyle1,0,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spanText.setSpan(spanStyle2,0,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            tv_url.text = spanText
            cardView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.translation_from_top))
            Handler().postDelayed({
                    connection_layout.visibility = View.VISIBLE
            },200)
        }

        empty_linear.setOnClickListener {
            cardView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.translation_to_top))
            Handler().postDelayed({
                connection_layout.visibility = View.GONE
            },300)

        }
    }

    private fun initWebView() {
        webView.webChromeClient = object: WebChromeClient() {

            override fun getDefaultVideoPoster(): Bitmap? {
                if (super.getDefaultVideoPoster() == null) {
                    return Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
                }
                return super.getDefaultVideoPoster()
            }

        }
        webView.webViewClient = object: WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                web_progress_bar.visibility = View.VISIBLE
                super.onPageStarted(view, url, favicon)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false

            override fun onPageFinished(view: WebView?, url: String?) {
                web_progress_bar.visibility = View.GONE
                toolbarImageConnection.visibility = View.VISIBLE

                toolbarImageConnection.setImageResource(
                    if (url?.contains("https://")!!) R.drawable.ic_lock_white_15dp
                    else R.drawable.ic_error_outline_white_20dp
                )

                toolbarTextTitle.text = view?.title

                toolbarTextUrl.text = url

                urlSite = url
                super.onPageFinished(view, url)
            }

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

//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        menuInflater.inflate(R.menu.web_browser_menu, menu)
//        return true
//    }
}
