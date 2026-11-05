package app.asterphone.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import app.asterphone.data.AddressCheck
import app.asterphone.data.AsterDownloads
import java.io.ByteArrayInputStream

enum class PanelStatus { Loading, Ready, Failed }

/**
 * Owns the web view behind the Aster tab. There is one view at a time, locked to one panel's address. The page keeps a live
 * connection to the server for as long as the view exists, so the view is dropped whenever the app leaves the foreground or another
 * panel is chosen, and made again, on the same page and with the same back history, when its panel is next shown. What each panel
 * left behind is kept in memory by its address.
 */
class AsterWebHost(private val context: Context, resumeUrls: Map<String, String> = emptyMap()) {
    var status by mutableStateOf(PanelStatus.Loading)
        private set
    var retrying by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set

    /** Changes each time the view is dropped, so the screen asks for a new one. */
    var generation by mutableIntStateOf(0)
        private set

    /** What a dropped view had behind it: its saved state, and the page it was on. */
    private class SavedHistory(val state: Bundle, val currentUrl: String)

    /** Where a panel was left: the page to come back to, and what was behind it. */
    private class Memory(var resumeUrl: String? = null, var history: SavedHistory? = null)

    private val memory = HashMap<String, Memory>()
    private var webView: WebView? = null
    private var address: AddressCheck.Valid? = null
    private var failedUrl: String? = null
    private var loadFailed = false
    private var rendererLost = false
    private var attached = false

    init {
        resumeUrls.forEach { (panel, url) -> memory[panel] = Memory(resumeUrl = url) }
    }

    /** The page each panel is to come back to, by panel address: what survives the app being recreated. */
    val resumeUrls: Map<String, String> get() = memory.mapNotNull { (panel, kept) -> kept.resumeUrl?.let { panel to it } }.toMap()

    /** The view for [address]: the one still alive, or a new one that opens the page left earlier. */
    fun obtainView(address: AddressCheck.Valid): WebView {
        attached = true
        webView?.let { view ->
            if (this.address == address) {
                (view.parent as? ViewGroup)?.removeView(view)
                view.onResume()
                return view
            }
            // Another panel was chosen: let go of the old one, remembering where it was.
            release(keepHistory = true, bumpGeneration = false)
        }
        this.address = address
        status = PanelStatus.Loading
        retrying = false
        canGoBack = false
        loadFailed = false
        val kept = memory[address.url]
        val first = kept?.resumeUrl?.takeIf { address.origin.allows(it) } ?: address.url
        val saved = kept?.history
        kept?.history = null
        val view = newView(address)
        webView = view
        if (rendererLost) {
            // Don't reopen a page that just brought the renderer down; wait for Retry.
            rendererLost = false
            failedUrl = first
            status = PanelStatus.Failed
        } else if (saved != null && address.origin.allows(saved.currentUrl) && view.restoreState(saved.state) != null) {
            canGoBack = view.canGoBack()
        } else {
            view.loadUrl(first)
        }
        return view
    }

    /** Called when the screen lets go of [view], which stays alive for the next time the tab is shown. */
    fun detach(view: WebView) {
        if (view !== webView) return
        attached = false
        view.onPause()
    }

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun retry() {
        val view = webView ?: return
        val target = failedUrl ?: address?.let { memory[it.url]?.resumeUrl ?: it.url } ?: return
        retrying = true
        view.loadUrl(target)
    }

    /** Shows [path] of the panel at [address]: in the view if it is the one alive, else as the first page of its next view. */
    fun open(address: AddressCheck.Valid, path: String) {
        val url = address.origin.urlFor(path)
        // A panel whose view was dropped comes back on this page alone: its old history isn't put under it.
        memory.getOrPut(address.url) { Memory() }.apply {
            resumeUrl = url
            history = null
        }
        if (this.address == address) {
            failedUrl = null
            webView?.loadUrl(url)
        }
    }

    /** Forgets the page and history of the panel at [address], and drops its view if that is the one alive. */
    fun reset(address: AddressCheck.Valid) {
        memory.remove(address.url)
        if (this.address == address) {
            failedUrl = null
            destroyView()
        }
    }

    /** Remembers the current page and what is behind it, and destroys the view, which closes the page's connections. */
    fun release() = release(keepHistory = true)

    private fun release(keepHistory: Boolean, bumpGeneration: Boolean = true) {
        val view = webView ?: return
        val panel = address
        view.url?.takeIf { panel?.origin?.allows(it) == true }?.let { url ->
            val kept = memory.getOrPut(panel!!.url) { Memory() }
            kept.resumeUrl = url
            if (keepHistory) kept.history = saveHistory(view)
        }
        destroyView(bumpGeneration)
    }

    private fun saveHistory(view: WebView): SavedHistory? {
        val state = Bundle()
        val current = view.saveState(state)?.currentItem?.url ?: return null
        return SavedHistory(state, current).takeIf { address?.origin?.allows(current) == true }
    }

    private fun destroyView(bumpGeneration: Boolean = true) {
        val view = webView ?: return
        webView = null
        CookieManager.getInstance().flush()
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.webViewClient = WebViewClient()
        view.destroy()
        canGoBack = false
        if (bumpGeneration) generation++
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newView(address: AddressCheck.Valid): WebView {
        val view = WebView(context)
        // Without layout parameters a view added by Compose counts as wrap-content, and the web view then gives the page a layout
        // height of zero: vh, dvh and height media queries all come out as 0.
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.settings.apply {
            // The panel is a script-driven page that keeps its theme in local storage.
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            // Only the saved origin is ever loaded; there is nothing for Safe Browsing to check.
            safeBrowsingEnabled = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, false)
        }
        view.webViewClient = Client(address)
        view.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(address, url, userAgent, contentDisposition, mimeType)
        }
        return view
    }

    private fun fail(url: String) {
        loadFailed = true
        if (address?.origin?.allows(url) == true) failedUrl = url
        retrying = false
        status = PanelStatus.Failed
    }

    private fun openInBrowser(uri: Uri) {
        if (uri.scheme != "http" && uri.scheme != "https") return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can open $uri", e)
        }
    }

    private fun download(address: AddressCheck.Valid, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        // The session cookie goes only to the panel's own origin.
        if (!address.origin.allows(url)) return
        val name = AsterDownloads.fileName(url, contentDisposition)
        try {
            val request = DownloadManager.Request(url.toUri())
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            mimeType?.let(request::setMimeType)
            userAgent?.let { request.addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            context.getSystemService(DownloadManager::class.java).enqueue(request)
            Toast.makeText(context, "Downloading $name", Toast.LENGTH_SHORT).show()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Download of $name failed to start", e)
            Toast.makeText(context, "Couldn't download $name", Toast.LENGTH_LONG).show()
        }
    }

    private inner class Client(private val address: AddressCheck.Valid) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (address.origin.allows(request.url.toString())) return false
            if (request.isForMainFrame) openInBrowser(request.url)
            return true
        }

        // Reached for every request, including form posts and subresources that shouldOverrideUrlLoading never sees.
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val scheme = request.url.scheme
            if ((scheme != "http" && scheme != "https") || address.origin.allows(request.url.toString())) return null
            // A navigation that was dropped leaves the page as it is.
            val status = if (request.isForMainFrame) 204 else 403
            return WebResourceResponse("text/plain", "utf-8", status, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            loadFailed = false
            if (status == PanelStatus.Failed && !retrying) status = PanelStatus.Loading
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (loadFailed) return
            retrying = false
            status = PanelStatus.Ready
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            canGoBack = view.canGoBack()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            Log.w(TAG, "Main page failed: ${error.errorCode} ${error.description}")
            fail(request.url.toString())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!request.isForMainFrame) return
            Log.w(TAG, "Main page answered HTTP ${errorResponse.statusCode}")
            fail(request.url.toString())
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // A certificate the system doesn't trust is never accepted.
            handler.cancel()
            Log.w(TAG, "Certificate refused: primary error ${error.primaryError}")
            // The panel's own certificate failing means the page can't load; another host's is just not loaded.
            if (address.origin.allows(error.url)) fail(view.url?.takeIf { address.origin.allows(it) } ?: address.url)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Without this the whole app would go down with the page.
            Log.w(TAG, "Web renderer gone, crashed=${detail.didCrash()}")
            rendererLost = attached
            // The page that was on may be what brought the renderer down, so nothing of it is reopened by itself.
            release(keepHistory = false)
            return true
        }
    }

    private companion object {
        const val TAG = "AsterPanel"
    }
}
