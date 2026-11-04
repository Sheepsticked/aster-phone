package app.asterphone.data

import android.app.Application
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections

/** The fetch, against a small local server that answers the way Aster does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AsterApiTest {
    private lateinit var server: HttpServer
    private val requests = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
    private val routes = mutableMapOf<String, (HttpExchange) -> Unit>()
    private val origin get() = AsterOrigin("http", "127.0.0.1", server.address.port)

    private fun fixture(name: String) = javaClass.getResource("/aster/$name")!!.readText()

    private fun HttpExchange.reply(status: Int, body: String, type: String = "application/json; charset=utf-8") {
        val bytes = body.toByteArray()
        responseHeaders.add("content-type", type)
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) } else close()
    }

    /** Answers like Aster: the lists for a request with the session cookie, 401 otherwise. */
    private fun asterRoutes() {
        for ((path, file) in mapOf("/api/phones" to "phones.json", "/api/modems" to "modems.json")) {
            routes[path] = { exchange ->
                if (exchange.requestHeaders.getFirst("Cookie") == "aster_sid=abc") exchange.reply(200, fixture(file))
                else exchange.reply(401, """{"error":"not logged in"}""")
            }
        }
    }

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.path to exchange.requestHeaders.getFirst("Cookie")
            val route = routes[exchange.requestURI.path]
            if (route != null) route(exchange) else exchange.reply(404, "{}")
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun fetch(session: String = "aster_sid=abc", timeoutMillis: Int = 2_000, at: AsterOrigin = origin) =
        runBlocking { AsterStatus.fetch(at, session, timeoutMillis) }

    @Test
    fun readsBothListsWithTheSessionCookie() {
        asterRoutes()
        val snapshot = fetch()!!
        assertEquals(15, snapshot.phones.size)
        assertEquals(listOf("gsm1", "gsm2"), snapshot.modems.map { it.id })
        assertEquals(listOf("/api/phones" to "aster_sid=abc", "/api/modems" to "aster_sid=abc"), requests.toList())
    }

    @Test
    fun aRefusedLoginShowsNothing() {
        asterRoutes()
        assertNull(fetch(session = "aster_sid=expired"))
        // It stops at the first refusal.
        assertEquals(1, requests.size)
    }

    @Test
    fun otherAnswersShowNothing() {
        for (status in listOf(403, 404, 500, 502, 503)) {
            routes["/api/phones"] = { it.reply(status, """{"error":"no"}""") }
            assertNull(status.toString(), fetch())
        }
        routes["/api/phones"] = { it.reply(200, "<html>login</html>", "text/html") }
        assertNull(fetch())
        routes["/api/phones"] = { it.reply(200, fixture("phones.json")) }
        routes["/api/modems"] = { it.reply(200, """{"error":"x"}""") }
        assertNull(fetch())
    }

    @Test
    fun doesNotFollowRedirects() {
        routes["/api/phones"] = { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/elsewhere")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        routes["/elsewhere"] = { it.reply(200, fixture("phones.json")) }
        assertNull(fetch())
        assertEquals(listOf("/api/phones"), requests.map { it.first })
    }

    @Test
    fun giveUpWhenTheServerStaysSilent() {
        routes["/api/phones"] = { exchange ->
            Thread.sleep(3_000)
            exchange.reply(200, fixture("phones.json"))
        }
        val started = System.nanoTime()
        assertNull(fetch(timeoutMillis = 300))
        assertTrue("took ${(System.nanoTime() - started) / 1_000_000} ms", System.nanoTime() - started < 2_500_000_000L)
    }

    @Test
    fun anUnreachableServerShowsNothing() {
        val gone = origin
        server.stop(0)
        assertNull(fetch(at = gone))
        // Starts again so the cleanup has something to stop.
        startServer()
    }

    @Test
    fun anOversizedAnswerShowsNothing() {
        routes["/api/phones"] = { it.reply(200, """{"phones":[],"pad":"${"x".repeat(2 shl 20)}"}""") }
        assertNull(fetch())
    }

    @Test
    fun theSourceAsksTheServerOnlyWhenItHasALogin() {
        asterRoutes()
        val source = AsterStatusSource()
        val query = SimQuery("504", "127.0.0.1", origin)
        assertNull(runBlocking { source.lineFor(query) { null } })
        assertEquals(0, requests.size)

        val line = runBlocking { source.lineFor(query) { "theme=dark; aster_sid=abc" } }
        assertNotNull(line)
        assertEquals("Calls go out through gsm1 ·\u00A0Operator ·\u00A0+1234567890", line!!.text)
        assertEquals(2, requests.size)

        // The next look is answered from memory.
        assertEquals(SimLine.InternalOnly, runBlocking { source.lineFor(query.copy(username = "501")) { "aster_sid=abc" } })
        assertEquals(2, requests.size)
    }
}
