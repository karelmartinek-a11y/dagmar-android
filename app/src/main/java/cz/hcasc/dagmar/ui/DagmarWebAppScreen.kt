package cz.hcasc.dagmar

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DagmarWebAppScreen(
    modifier: Modifier = Modifier,
    baseUrl: String,
    url: String,
    instanceId: String?,
    instanceToken: String?,
    displayName: String?
) {
    var loading by remember { mutableStateOf(true) }
    var fileCb by remember { mutableStateOf<ValueCallback<Array<android.net.Uri>>?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.userAgentString = settings.userAgentString + " DagmarAndroid/2.0"

                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.mediaPlaybackRequiresUserGesture = true

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<android.net.Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            filePathCallback?.onReceiveValue(null)
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url?.toString() ?: return false
                            return !u.startsWith(baseUrl)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            val js = """
                                (function(){
                                  try {
                                    window.__DAGMAR_ANDROID__ = {
                                      instance_id: ${instanceId?.let { "\\\"$it\\\"" } ?: "null"},
                                      instance_token: ${instanceToken?.let { "\\\"$it\\\"" } ?: "null"},
                                      display_name: ${displayName?.let { "\\\"$it\\\"" } ?: "null"}
                                    };
                                    window.dispatchEvent(new Event('dagmar-android-ready'));
                                  } catch(e) {}
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                        }
                    }

                    loadUrl(url)
                }
            },
            update = { wv ->
                if (wv.url != url) {
                    loading = true
                    wv.loadUrl(url)
                }
            }
        )

        if (loading) {
            CircularProgressIndicator()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fileCb?.onReceiveValue(null)
            fileCb = null
        }
    }
}
