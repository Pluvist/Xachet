@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val XTU_LOGIN_URL = "https://jw.xtu.edu.cn/jwglxt/xtgl/login_slogin.html"

@Composable
internal fun WebLoginPage(
    vm: SachetViewModel,
    onAuthenticated: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }

    ExpressivePinnedScaffold(
        title = "教务网页登录",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (loadProgress in 1..99) {
                LinearWavyProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            web = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            var submitted = false
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val url = request?.url ?: return false
                                    val scheme = url.scheme?.lowercase()
                                    if (scheme != "http" && scheme != "https") {
                                        if (context is Activity) {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    val uri = Uri.parse(url)
                                    if (!submitted &&
                                        uri.scheme == "https" &&
                                        uri.host == "jw.xtu.edu.cn" &&
                                        uri.path.orEmpty().contains("index_initMenu")
                                    ) {
                                        val cookie = CookieManager.getInstance()
                                            .getCookie("https://jw.xtu.edu.cn")
                                            .orEmpty()
                                        if (cookie.isNotBlank()) {
                                            submitted = true
                                            vm.cookieLogin(cookie, onAuthenticated ?: onBack)
                                        }
                                    }
                                }
                            }
                            loadUrl(XTU_LOGIN_URL)
                        }
                    },
                )
            }
        }
    }
}
