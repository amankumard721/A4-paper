package com.a4sizepaper.typing.texttopdf.editor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.a4sizepaper.typing.texttopdf.BuildConfig
import com.a4sizepaper.typing.texttopdf.data.AssetStore
import com.a4sizepaper.typing.texttopdf.domain.Document
import java.io.ByteArrayInputStream
import org.json.JSONObject

/** Only a local HTTPS origin receives the message port; no legacy Java interface. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class EditorHost(context: Context, private val onMessage: (JSONObject) -> Unit) : WebView(context) {
    @Volatile private var payload: (() -> JSONObject)? = null
    @Volatile private var payloadToken = java.util.UUID.randomUUID().toString()
    private var booted = false
    private var disposed = false
    private var restoreInputAfterWindowFocus = false
    private var screenPaused = false
    private var startTypingRequested = false

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus && !screenPaused) restoreInputAfterWindowFocus = hasFocus()
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && (restoreInputAfterWindowFocus || startTypingRequested)) {
            restoreInputAfterWindowFocus = false
            startTyping()
        }
    }

    /** Called only by the visible editor after its document is ready, never by PDF workers. */
    fun startTyping() {
        startTypingRequested = true
        post {
            if (disposed || screenPaused || !isAttachedToWindow || !hasWindowFocus()) return@post
            startTypingRequested = false
            requestFocus()
            // Wait for DOM focus before asking Android to show its keyboard.
            evaluateJavascript("window.Lekhak?.restoreInputFocus() === true") { focused ->
                if (focused == "true")
                    postOnAnimation {
                        if (
                            !disposed &&
                                !screenPaused &&
                                isAttachedToWindow &&
                                hasWindowFocus() &&
                                hasFocus()
                        ) {
                            val imm =
                                context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                    as? android.view.inputmethod.InputMethodManager
                            imm?.showSoftInput(
                                this,
                                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT,
                            )
                        }
                    }
            }
        }
    }

    override fun onResume() {
        screenPaused = false
        super.onResume()
        if (startTypingRequested) startTyping()
    }

    override fun onPause() {
        screenPaused = true
        restoreInputAfterWindowFocus = false
        super.onPause()
    }

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.textZoom = 100
        // Native pinch changes the viewport only, not document typography or PDF layout.
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        setBackgroundColor(android.graphics.Color.WHITE)
        isFocusableInTouchMode = true
        if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
        val loader =
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/document-assets/") { path ->
                    AssetStore(context).file(path)?.let { file ->
                        runCatching {
                                WebResourceResponse(
                                    if (file.extension == "jpg") "image/jpeg" else "image/png",
                                    null,
                                    file.inputStream(),
                                )
                            }
                            .getOrNull()
                    } ?: denied()
                }
                .addPathHandler("/boot-payload/") { token ->
                    val provider = payload
                    if (token != payloadToken || provider == null) denied()
                    else
                        runCatching {
                                WebResourceResponse(
                                    "application/json",
                                    "utf-8",
                                    200,
                                    "OK",
                                    mapOf("Cache-Control" to "no-store"),
                                    ByteArrayInputStream(
                                        provider().toString().toByteArray(Charsets.UTF_8)
                                    ),
                                )
                            }
                            .getOrElse { denied() }
                }
                .build()
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    true

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse =
                    if (request.url.scheme == "https" && request.url.host == HOST)
                        loader.shouldInterceptRequest(request.url) ?: denied()
                    else denied()

                override fun onPageFinished(view: WebView, url: String) {
                    if (url == URL && !booted) boot()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame)
                        onMessage(
                            JSONObject()
                                .put("type", "error")
                                .put("message", "The local editor could not load.")
                        )
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    onMessage(
                        JSONObject()
                            .put("type", "error")
                            .put(
                                "message",
                                "The editor stopped. Reopen this document to recover the last saved draft.",
                            )
                    )
                    (parent as? android.view.ViewGroup)?.removeView(this@EditorHost)
                    dispose()
                    return true
                }
            }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            WebViewCompat.addWebMessageListener(this, "LekhakBridge", setOf(ORIGIN)) {
                _,
                message,
                origin,
                isMainFrame,
                _ ->
                if (isMainFrame && origin.toString().trimEnd('/') == ORIGIN) {
                    val data = message.data ?: return@addWebMessageListener
                    if (data.length <= 8_100_000) {
                        runCatching { JSONObject(data) }
                            .onSuccess(onMessage)
                            .onFailure {
                                onMessage(
                                    JSONObject()
                                        .put("type", "error")
                                        .put(
                                            "message",
                                            "Invalid editor message. Reopen the saved document.",
                                        )
                                )
                            }
                    } else {
                        onMessage(
                            JSONObject()
                                .put("type", "error")
                                .put(
                                    "message",
                                    "Document is too large to save. Reduce its size before leaving the editor.",
                                )
                        )
                    }
                }
            }
        else
            post {
                onMessage(
                    JSONObject()
                        .put("type", "error")
                        .put("message", "Update Android System WebView to use the secure editor.")
                )
            }
    }

    fun load(
        document: Document,
        language: String = "en",
        dark: Boolean = false,
        renderOnly: Boolean = false,
    ) {
        payloadToken = java.util.UUID.randomUUID().toString()
        payload = {
            document
                .toJson()
                .put("language", language)
                .put("dark", dark)
                .put("renderOnly", renderOnly)
        }
        booted = false
        loadUrl(URL)
    }

    private fun boot() {
        if (payload == null || disposed) return
        booted = true
        val message =
            JSONObject()
                .put("type", "boot-url")
                .put("url", "$ORIGIN/boot-payload/$payloadToken")
                .toString()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)) {
            WebViewCompat.postWebMessage(
                this,
                androidx.webkit.WebMessageCompat(message),
                android.net.Uri.parse(ORIGIN),
            )
        } else {
            onMessage(
                JSONObject()
                    .put("type", "error")
                    .put("message", "Update Android System WebView to open documents.")
            )
        }
    }

    fun command(name: String, value: String? = null) =
        evaluateJavascript(
            "window.Lekhak.command(${JSONObject.quote(name)},${value?.let(JSONObject::quote) ?: "null"})",
            null,
        )

    fun setReview(review: Boolean) {
        if (review) {
            startTypingRequested = false
            restoreInputAfterWindowFocus = false
            (context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(windowToken, 0)
        }
        evaluateJavascript("typeof window.Lekhak?.review === 'function' ? window.Lekhak.review($review) : false") { if (!review) startTyping() }
    }

    fun setMode(pages: Boolean) =
        evaluateJavascript("window.Lekhak.mode('${if(pages) "pages" else "write"}')", null)

    fun setPageSettings(settings: JSONObject) =
        evaluateJavascript("window.Lekhak.settings($settings)", null)

    fun setDark(dark: Boolean) = evaluateJavascript("window.Lekhak.theme($dark)", null)

    fun zoom(value: Float) = evaluateJavascript("window.Lekhak.zoom($value)", null)

    fun snapshot(callback: (String?) -> Unit) {
        evaluateJavascript("JSON.stringify(window.Lekhak.getJSON())") { raw ->
            callback(runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull())
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        payload = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            WebViewCompat.removeWebMessageListener(this, "LekhakBridge")
        stopLoading()
        destroy()
    }

    companion object {
        const val HOST = "appassets.androidplatform.net"
        const val ORIGIN = "https://$HOST"
        const val URL = "$ORIGIN/assets/editor/index.html"

        private fun denied() =
            WebResourceResponse(
                "text/plain",
                "utf-8",
                403,
                "Blocked",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
    }
}
