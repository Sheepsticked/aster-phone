package app.asterphone.ui

import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.TestSslErrorHandler
import android.webkit.TestWebResourceError
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import app.asterphone.data.AddressCheck
import app.asterphone.data.AsterAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The web host's rules, checked against Robolectric's stand-in for the web view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AsterWebHostTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val address = AsterAddress.check("http://192.0.2.10:5099/") as AddressCheck.Valid
    private lateinit var host: AsterWebHost

    @Before
    fun setUp() {
        host = AsterWebHost(activity)
    }

    private fun client(view: WebView) = shadowOf(view).webViewClient

    private fun request(url: String, mainFrame: Boolean = true) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun failure() = TestWebResourceError(-6, "net::ERR_CONNECTION_REFUSED")

    private fun httpError(status: Int) = WebResourceResponse("text/plain", "utf-8", status, "Error", emptyMap(), null)

    private fun startedIntent(): Intent? = shadowOf(activity).nextStartedActivity

    @Test
    fun opensTheSavedAddressWithTheLockedDownSettings() {
        val view = host.obtainView(address)
        assertEquals("http://192.0.2.10:5099/", shadowOf(view).lastLoadedUrl)
        assertTrue(view.settings.javaScriptEnabled)
        assertTrue(view.settings.domStorageEnabled)
        assertFalse(view.settings.allowFileAccess)
        assertFalse(view.settings.allowContentAccess)
        assertEquals(PanelStatus.Loading, host.status)
    }

    @Test
    fun keepsTheSameViewWhileItIsAlive() {
        val first = host.obtainView(address)
        host.detach(first)
        assertSame(first, host.obtainView(address))
    }

    @Test
    fun fillsTheSpaceItIsGiven() {
        // A web view that counts as wrap-content gives its page a layout height of zero, so vh units come out as 0.
        val params = host.obtainView(address).layoutParams
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height)
    }

    @Test
    fun staysOnItsOwnOrigin() {
        val view = host.obtainView(address)
        assertFalse(client(view).shouldOverrideUrlLoading(view, request("http://192.0.2.10:5099/calls?page=2")))
        assertNull(startedIntent())
    }

    @Test
    fun sendsOtherOriginsToTheBrowser() {
        val view = host.obtainView(address)
        for (url in listOf("http://192.0.2.11:5099/", "http://192.0.2.10:5100/", "https://192.0.2.10:5099/", "http://example.com/")) {
            assertTrue(url, client(view).shouldOverrideUrlLoading(view, request(url)))
            val intent = startedIntent()
            assertNotNull(url, intent)
            assertEquals(Intent.ACTION_VIEW, intent!!.action)
            assertEquals(url, intent.dataString)
        }
    }

    @Test
    fun neverLoadsOrOpensOtherSchemes() {
        val view = host.obtainView(address)
        for (url in listOf("file:///sdcard/x.html", "javascript:alert(1)", "intent://x#Intent;end", "content://x/y", "tel:+1234567890")) {
            assertTrue(url, client(view).shouldOverrideUrlLoading(view, request(url)))
            assertNull(url, startedIntent())
        }
    }

    @Test
    fun blocksOtherOriginsInFramesWithoutOpeningThem() {
        val view = host.obtainView(address)
        assertTrue(client(view).shouldOverrideUrlLoading(view, request("http://example.com/", mainFrame = false)))
        assertNull(startedIntent())
    }

    @Test
    fun aFailedMainPageShowsTheErrorScreen() {
        val view = host.obtainView(address)
        client(view).onReceivedError(view, request("http://192.0.2.10:5099/"), failure())
        assertEquals(PanelStatus.Failed, host.status)
        client(view).onPageFinished(view, "http://192.0.2.10:5099/")
        assertEquals(PanelStatus.Failed, host.status)
    }

    @Test
    fun anHttpErrorOnTheMainPageShowsTheErrorScreen() {
        val view = host.obtainView(address)
        client(view).onReceivedHttpError(view, request("http://192.0.2.10:5099/"), httpError(502))
        assertEquals(PanelStatus.Failed, host.status)
    }

    @Test
    fun aRefusedCertificateShowsTheErrorScreen() {
        val secure = AsterAddress.check("https://192.0.2.10:8443/") as AddressCheck.Valid
        val view = host.obtainView(secure)
        val handler = TestSslErrorHandler()
        val error = SslError(SslError.SSL_UNTRUSTED, SslCertificate("CN=aster", "CN=aster", java.util.Date(0), java.util.Date(1)), "https://192.0.2.10:8443/")
        client(view).onReceivedSslError(view, handler, error)
        assertTrue(handler.cancelled)
        assertFalse(handler.proceeded)
        assertEquals(PanelStatus.Failed, host.status)
    }

    @Test
    fun aRefusedCertificateOfAnotherHostIsIgnored() {
        val view = host.obtainView(address)
        val handler = TestSslErrorHandler()
        client(view).onReceivedSslError(view, handler, SslError(SslError.SSL_UNTRUSTED, SslCertificate("CN=other", "CN=other", java.util.Date(0), java.util.Date(1)), "https://example.com/x.js"))
        assertTrue(handler.cancelled)
        assertEquals(PanelStatus.Loading, host.status)
    }

    @Test
    fun retryAfterACertificateErrorReopensThePageNotTheResource() {
        val view = host.obtainView(address)
        shadowOf(view).pushEntryToHistory("http://192.0.2.10:5099/calls")
        val error = SslError(SslError.SSL_UNTRUSTED, SslCertificate("CN=aster", "CN=aster", java.util.Date(0), java.util.Date(1)), "http://192.0.2.10:5099/assets/app.js")
        client(view).onReceivedSslError(view, TestSslErrorHandler(), error)
        assertEquals(PanelStatus.Failed, host.status)
        host.retry()
        assertEquals("http://192.0.2.10:5099/calls", shadowOf(view).lastLoadedUrl)
    }

    @Test
    fun dropsRequestsToOtherOriginsEvenWhenTheyAreNotLinkClicks() {
        val view = host.obtainView(address)
        val page = client(view).shouldInterceptRequest(view, request("http://example.com/steal", mainFrame = true))
        assertEquals(204, page!!.statusCode)
        val script = client(view).shouldInterceptRequest(view, request("https://example.com/x.js", mainFrame = false))
        assertEquals(403, script!!.statusCode)
        val otherPort = client(view).shouldInterceptRequest(view, request("http://192.0.2.10:5100/x", mainFrame = false))
        assertEquals(403, otherPort!!.statusCode)
    }

    @Test
    fun letsTheSavedOriginAndInPageUrlsThrough() {
        val view = host.obtainView(address)
        assertNull(client(view).shouldInterceptRequest(view, request("http://192.0.2.10:5099/api/me", mainFrame = false)))
        assertNull(client(view).shouldInterceptRequest(view, request("blob:http://192.0.2.10:5099/1234", mainFrame = false)))
        assertNull(client(view).shouldInterceptRequest(view, request("data:image/png;base64,AAAA", mainFrame = false)))
    }

    @Test
    fun errorsOnSubResourcesDoNotShowTheErrorScreen() {
        val view = host.obtainView(address)
        client(view).onReceivedError(view, request("http://192.0.2.10:5099/logo.png", mainFrame = false), failure())
        client(view).onReceivedHttpError(view, request("http://192.0.2.10:5099/api/me", mainFrame = false), httpError(401))
        client(view).onPageFinished(view, "http://192.0.2.10:5099/")
        assertEquals(PanelStatus.Ready, host.status)
    }

    @Test
    fun retryStaysOnTheErrorScreenUntilThePageLoads() {
        val view = host.obtainView(address)
        client(view).onReceivedError(view, request("http://192.0.2.10:5099/calls"), failure())
        client(view).onPageFinished(view, "http://192.0.2.10:5099/calls")

        host.retry()
        assertTrue(host.retrying)
        assertEquals("http://192.0.2.10:5099/calls", shadowOf(view).lastLoadedUrl)
        client(view).onPageStarted(view, "http://192.0.2.10:5099/calls", null)
        assertEquals(PanelStatus.Failed, host.status)

        client(view).onPageFinished(view, "http://192.0.2.10:5099/calls")
        assertEquals(PanelStatus.Ready, host.status)
        assertFalse(host.retrying)
    }

    @Test
    fun aFailedRetryEndsTheRetrying() {
        val view = host.obtainView(address)
        client(view).onReceivedError(view, request("http://192.0.2.10:5099/"), failure())
        host.retry()
        client(view).onPageStarted(view, "http://192.0.2.10:5099/", null)
        client(view).onReceivedError(view, request("http://192.0.2.10:5099/"), failure())
        client(view).onPageFinished(view, "http://192.0.2.10:5099/")
        assertEquals(PanelStatus.Failed, host.status)
        assertFalse(host.retrying)
    }

    @Test
    fun aNewPageAfterAnErrorHidesTheErrorScreen() {
        val view = host.obtainView(address)
        client(view).onReceivedError(view, request("http://192.0.2.10:5099/calls"), failure())
        client(view).onPageStarted(view, "http://192.0.2.10:5099/", null)
        assertEquals(PanelStatus.Loading, host.status)
        client(view).onPageFinished(view, "http://192.0.2.10:5099/")
        assertEquals(PanelStatus.Ready, host.status)
    }

    @Test
    fun releaseDestroysTheViewAndTheNextOneOpensTheSamePage() {
        val view = host.obtainView(address)
        shadowOf(view).pushEntryToHistory("http://192.0.2.10:5099/messages")
        val generation = host.generation
        host.release()
        assertTrue(shadowOf(view).wasDestroyCalled())
        assertEquals(generation + 1, host.generation)
        assertEquals("http://192.0.2.10:5099/messages", host.resumeUrls[address.url])

        val next = host.obtainView(address)
        assertNotSame(view, next)
        assertEquals("http://192.0.2.10:5099/messages", shadowOf(next).lastLoadedUrl)
    }

    @Test
    fun releaseWithoutAViewDoesNothing() {
        val generation = host.generation
        host.release()
        assertEquals(generation, host.generation)
    }

    @Test
    fun resetForgetsThePageAndStartsFromTheAddress() {
        val view = host.obtainView(address)
        shadowOf(view).pushEntryToHistory("http://192.0.2.10:5099/messages")
        host.reset(address)
        assertTrue(shadowOf(view).wasDestroyCalled())
        assertNull(host.resumeUrls[address.url])
        assertEquals("http://192.0.2.10:5099/", shadowOf(host.obtainView(address)).lastLoadedUrl)
    }

    @Test
    fun ignoresASavedPageOnAnotherOrigin() {
        val resuming = AsterWebHost(activity, mapOf(address.url to "http://example.com/phish"))
        assertEquals("http://192.0.2.10:5099/", shadowOf(resuming.obtainView(address)).lastLoadedUrl)
    }

    @Test
    fun aLostRendererDestroysTheViewAndWaitsForRetry() {
        val view = host.obtainView(address)
        val detail = object : android.webkit.RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        }
        assertTrue(client(view).onRenderProcessGone(view, detail))
        assertTrue(shadowOf(view).wasDestroyCalled())

        val next = host.obtainView(address)
        assertNotSame(view, next)
        assertNull("the page that brought the renderer down isn't reopened by itself", shadowOf(next).lastLoadedUrl)
        assertEquals(PanelStatus.Failed, host.status)
        host.retry()
        assertEquals("http://192.0.2.10:5099/", shadowOf(next).lastLoadedUrl)
    }

    @Test
    fun downloadsGoToTheDownloadManagerWithTheServersFileName() {
        val view = host.obtainView(address)
        val listener = shadowOf(view).downloadListener
        listener.onDownloadStart(
            "http://192.0.2.10:5099/api/backup", "TestAgent", """attachment; filename="aster-20260101T000000Z.tar.gz"""", "application/gzip", -1,
        )
        val manager = shadowOf(application.getSystemService(DownloadManager::class.java))
        assertEquals(1, manager.requestCount)
        val request = shadowOf(manager.getRequest(0))
        assertEquals("http://192.0.2.10:5099/api/backup", request.uri.toString())
        assertEquals("aster-20260101T000000Z.tar.gz", request.title.toString())
        assertEquals("application/gzip", request.mimeType.toString())
        assertTrue(request.destination.toString().endsWith("/Download/aster-20260101T000000Z.tar.gz"))
        assertTrue(request.requestHeaders.contains(android.util.Pair("User-Agent", "TestAgent")))
    }

    @Test
    fun neverDownloadsFromAnotherOrigin() {
        val view = host.obtainView(address)
        shadowOf(view).downloadListener.onDownloadStart("http://example.com/file.zip", "TestAgent", null, "application/zip", -1)
        assertEquals(0, shadowOf(application.getSystemService(DownloadManager::class.java)).requestCount)
    }

    private fun browse(view: WebView, vararg paths: String) = paths.forEach { shadowOf(view).pushEntryToHistory("http://192.0.2.10:5099$it") }

    @Test
    fun releaseKeepsTheBackHistoryForTheNextView() {
        val view = host.obtainView(address)
        browse(view, "/calls", "/calls/12")
        host.release()

        val next = host.obtainView(address)
        assertNotSame(view, next)
        assertEquals("http://192.0.2.10:5099/calls/12", next.url)
        assertTrue(next.canGoBack())
        assertTrue(host.canGoBack)
        host.goBack()
        assertEquals("http://192.0.2.10:5099/calls", next.url)
        assertFalse(next.canGoBack())
    }

    @Test
    fun withNothingToRestoreItOpensTheCurrentPage() {
        host.obtainView(address)
        host.release()
        val next = host.obtainView(address)
        assertEquals("http://192.0.2.10:5099/", shadowOf(next).lastLoadedUrl)
        assertFalse(next.canGoBack())
    }

    @Test
    fun aHistoryOfAnotherAddressIsNotRestored() {
        val view = host.obtainView(address)
        browse(view, "/calls", "/calls/12")
        host.release()

        val moved = AsterAddress.check("http://192.0.2.20:5099/") as AddressCheck.Valid
        val next = host.obtainView(moved)
        assertEquals("http://192.0.2.20:5099/", shadowOf(next).lastLoadedUrl)
        assertFalse(next.canGoBack())
    }

    @Test
    fun resetForgetsTheKeptHistory() {
        val view = host.obtainView(address)
        browse(view, "/calls", "/calls/12")
        host.release()
        host.reset(address)

        val next = host.obtainView(address)
        assertEquals("http://192.0.2.10:5099/", shadowOf(next).lastLoadedUrl)
        assertFalse(next.canGoBack())
    }

    @Test
    fun aLostRendererKeepsNoBackHistory() {
        val view = host.obtainView(address)
        browse(view, "/calls", "/calls/12")
        val detail = object : android.webkit.RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        }
        client(view).onRenderProcessGone(view, detail)

        val next = host.obtainView(address)
        assertNull(shadowOf(next).lastLoadedUrl)
        host.retry()
        assertEquals("http://192.0.2.10:5099/calls/12", shadowOf(next).lastLoadedUrl)
        assertFalse(next.canGoBack())
    }

    @Test
    fun opensAPageOfThePanelInTheViewThatIsAlive() {
        val view = host.obtainView(address)
        host.detach(view)
        host.open(address, "/modems/gsm1")
        assertEquals("http://192.0.2.10:5099/modems/gsm1", shadowOf(view).lastLoadedUrl)
        assertSame(view, host.obtainView(address))
    }

    @Test
    fun opensAPageOfThePanelAsTheFirstPageOfTheNextView() {
        host.open(address, "/phones")
        assertEquals("http://192.0.2.10:5099/phones", shadowOf(host.obtainView(address)).lastLoadedUrl)
    }

    @Test
    fun aPageOpenedAfterTheViewWasDroppedComesWithoutTheOldHistory() {
        val view = host.obtainView(address)
        browse(view, "/calls", "/calls/12")
        host.release()
        host.open(address, "/modems/gsm1")

        val next = host.obtainView(address)
        assertEquals("http://192.0.2.10:5099/modems/gsm1", shadowOf(next).lastLoadedUrl)
        assertFalse(next.canGoBack())
    }

    @Test
    fun aPageOpenedOnTheErrorScreenIsLoadedAgain() {
        val view = host.obtainView(address)
        client(view).onReceivedError(view, request("http://192.0.2.10:5099/"), failure())
        assertEquals(PanelStatus.Failed, host.status)
        host.open(address, "/phones")
        client(view).onPageStarted(view, "http://192.0.2.10:5099/phones", null)
        assertEquals(PanelStatus.Loading, host.status)
        assertEquals("http://192.0.2.10:5099/phones", shadowOf(view).lastLoadedUrl)
    }

    private val other = AsterAddress.check("http://192.0.2.20:5098/") as AddressCheck.Valid

    @Test
    fun anotherPanelReplacesTheViewAndOpensItsOwnPage() {
        val first = host.obtainView(address)
        val second = host.obtainView(other)
        assertNotSame(first, second)
        assertTrue(shadowOf(first).wasDestroyCalled())
        assertEquals("http://192.0.2.20:5098/", shadowOf(second).lastLoadedUrl)
        assertEquals(PanelStatus.Loading, host.status)
        assertFalse(host.canGoBack)
    }

    @Test
    fun eachPanelComesBackOnItsOwnPageWithItsOwnHistory() {
        val first = host.obtainView(address)
        browse(first, "/calls", "/calls/12")
        val second = host.obtainView(other)
        shadowOf(second).pushEntryToHistory("http://192.0.2.20:5098/settings")

        val back = host.obtainView(address)
        assertEquals("http://192.0.2.10:5099/calls/12", back.url)
        assertTrue(back.canGoBack())
        host.goBack()
        assertEquals("http://192.0.2.10:5099/calls", back.url)

        val again = host.obtainView(other)
        assertEquals("http://192.0.2.20:5098/settings", again.url)
        assertFalse("the other panel's pages aren't in this history", again.canGoBack())
    }

    @Test
    fun openShowsAPageOfAPanelThatIsNotOnScreen() {
        host.obtainView(address)
        host.open(other, "/modems/gsm1")
        val view = host.obtainView(other)
        assertEquals("http://192.0.2.20:5098/modems/gsm1", shadowOf(view).lastLoadedUrl)
        assertEquals("http://192.0.2.20:5098/modems/gsm1", host.resumeUrls[other.url])
    }

    @Test
    fun openLoadsInTheViewThatIsAlive() {
        val view = host.obtainView(address)
        browse(view, "/calls")
        host.open(address, "/modems/gsm1")
        assertEquals("http://192.0.2.10:5099/modems/gsm1", shadowOf(view).lastLoadedUrl)
    }

    @Test
    fun resettingOnePanelLeavesTheOthersAlone() {
        host.obtainView(address)
        host.open(other, "/phones")
        host.reset(address)
        assertNull(host.resumeUrls[address.url])
        assertEquals("http://192.0.2.20:5098/phones", host.resumeUrls[other.url])
    }

    @Test
    fun theKeptPagesSurviveARecreatedHost() {
        val view = host.obtainView(address)
        browse(view, "/calls")
        host.release()
        val restored = AsterWebHost(activity, host.resumeUrls)
        assertEquals("http://192.0.2.10:5099/calls", shadowOf(restored.obtainView(address)).lastLoadedUrl)
    }
}
