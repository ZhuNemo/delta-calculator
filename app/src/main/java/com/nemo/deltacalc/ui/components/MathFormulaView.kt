package com.nemo.deltacalc.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.nemo.deltacalc.core.LatexPretty
import org.json.JSONObject
import java.util.Locale

/**
 * MathLive 的宿主 WebView 控制器。
 * 页面未加载完成时先缓存，onPageFinished 后再推送，避免首帧公式丢失。
 */
private class MathWebViewHolder {
    private var webView: WebView? = null
    private var pageReady = false
    private var pendingLatex: String? = null
    private var pendingSize = 30f
    private var pendingColor = "#FFFFFF"

    fun attach(view: WebView) {
        webView = view
    }

    fun onPageReady(view: WebView?) {
        if (view == null) return
        webView = view
        pageReady = true
        pendingLatex?.let { push(it, pendingSize, pendingColor) }
    }

    fun render(latex: String, sizeSp: Float, colorArgb: Int) {
        pendingLatex = latex
        pendingSize = sizeSp
        pendingColor = String.format(Locale.US, "#%06X", 0xFFFFFF and colorArgb)
        if (pageReady) push(latex, sizeSp, pendingColor)
    }

    private fun push(latex: String, sizeSp: Float, colorHex: String) {
        val view = webView ?: return
        val quoted = JSONObject.quote(latex)
        val js = "window.deltaRender($quoted, ${sizeSp}, '$colorHex')"
        runCatching { view.evaluateJavascript(js, null) }
    }

    fun destroy() {
        pageReady = false
        webView?.let { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            runCatching { view.destroy() }
        }
        webView = null
    }
}

/** assets 里的 MathLive 是否已就位（CI 会先跑 fetch 脚本）。 */
fun mathLiveBundled(context: android.content.Context): Boolean =
    runCatching {
        context.assets.open("mathlive/vendor/mathlive.min.js").close()
        true
    }.getOrDefault(false)

/**
 * 只读公式显示。没有光标、不弹系统输入法，输入完全交给 Compose 键盘。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathFormulaView(
    latex: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 30f,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val bundled = remember { mathLiveBundled(context) }
    val holder = remember { MathWebViewHolder() }
    val colorArgb = textColor.toArgb()

    if (!bundled) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = LatexPretty.toPlain(latex).ifBlank { "MathLive 资源缺失\n运行 node tools/fetch-mathlive.mjs" },
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                // 顶层没有 INTERNET 权限，这里再显式断网，确保页面不会发起任何请求
                settings.blockNetworkLoads = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        holder.onPageReady(view)
                    }
                }
                loadUrl("file:///android_asset/mathlive/index.html")
                holder.attach(this)
            }
        },
    )

    LaunchedEffect(latex, fontSizeSp, colorArgb) {
        holder.render(latex, fontSizeSp, colorArgb)
    }

    DisposableEffect(Unit) {
        onDispose { holder.destroy() }
    }
}
