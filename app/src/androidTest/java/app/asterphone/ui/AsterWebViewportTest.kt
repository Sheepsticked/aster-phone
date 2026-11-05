package app.asterphone.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.asterphone.data.AddressCheck
import app.asterphone.data.AsterAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The panel's web view inside Compose, on a real web view: the page has to get the height of the space it is given. */
@RunWith(AndroidJUnit4::class)
class AsterWebViewportTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var socket: ServerSocket
    private val page = """<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1">
        <body style="margin:0"><div id="full" style="position:fixed;top:0;left:0;width:10px;height:100vh"></div>
        <div id="dynamic" style="position:fixed;top:0;left:20px;width:10px;height:100dvh"></div></body>"""

    @Before
    fun startServer() {
        socket = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        // Read the request head, then answer with the page whatever was asked for.
                        val input = client.getInputStream()
                        val head = StringBuilder()
                        while (!head.endsWith("\r\n\r\n")) {
                            val c = input.read()
                            if (c < 0) break
                            head.append(c.toChar())
                        }
                        val body = page.toByteArray()
                        client.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (_: Exception) {
                    // The socket is closed when the test ends.
                }
            }
        }
    }

    @After
    fun stopServer() {
        socket.close()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun evaluate(view: WebView, script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        compose.runOnUiThread { view.evaluateJavascript(script) { result = it; latch.countDown() } }
        assertTrue("the script did not finish", latch.await(10, TimeUnit.SECONDS))
        return result.trim('"')
    }

    @Test
    fun theViewportUnitsOfThePageAreAsTallAsTheView() {
        val address = AsterAddress.check("http://127.0.0.1:${socket.localPort}/") as AddressCheck.Valid
        lateinit var host: AsterWebHost
        compose.runOnUiThread { host = AsterWebHost(compose.activity) }
        compose.setContent { AsterWebPanel(host, address, onChangeAddress = {}, modifier = Modifier.fillMaxSize()) }
        compose.waitUntil(timeoutMillis = 30_000) { host.status == PanelStatus.Ready }

        val view = findWebView(compose.activity.window.decorView)!!
        val density = view.resources.displayMetrics.density
        val expected = Math.round(view.height / density)
        assertTrue("the view has no height", expected > 0)
        val measured = evaluate(
            view,
            "[innerHeight, Math.round(document.getElementById('full').getBoundingClientRect().height)," +
                " Math.round(document.getElementById('dynamic').getBoundingClientRect().height)].join(',')",
        ).split(',').map { it.toInt() }
        assertEquals("innerHeight", expected, measured[0])
        assertEquals("height of 100vh", expected, measured[1])
        assertEquals("height of 100dvh", expected, measured[2])
    }
}
