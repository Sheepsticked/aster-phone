package app.asterphone.data

import android.app.Application
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AsterProbeTest {
    private lateinit var server: HttpServer
    private val requests = Collections.synchronizedList(mutableListOf<HttpExchangeInfo>())
    private var respond: (HttpExchange) -> Unit = { it.reply(404, "") }

    private class HttpExchangeInfo(val path: String, val cookie: String?, val accept: String?)

    private fun HttpExchange.reply(status: Int, body: String, type: String = "application/json", vararg headers: Pair<String, String>) {
        val bytes = body.toByteArray()
        responseHeaders.add("content-type", type)
        headers.forEach { (name, value) -> responseHeaders.add(name, value) }
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) } else close()
    }

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            requests += HttpExchangeInfo(exchange.requestURI.path, exchange.requestHeaders.getFirst("Cookie"), exchange.requestHeaders.getFirst("Accept"))
            respond(exchange)
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun address(path: String = "/") = AsterAddress.check("http://127.0.0.1:${server.address.port}$path") as AddressCheck.Valid

    private fun probe(path: String = "/", timeoutMillis: Int = 2_000) = runBlocking { AsterProbe.run(address(path), timeoutMillis) }

    private val health = """{"ok":true,"versions":{"controller":"1.4.2","asterisk":"22.0.0"}}"""

    // What an answer means

    @Test
    fun anAnswerWithAControllerVersionIsAster() {
        assertEquals(AsterReach.Aster, AsterProbe.classify(200, health))
        assertEquals(AsterReach.Aster, AsterProbe.classify(200, """{"versions":{"controller":"x"}}"""))
    }

    @Test
    fun anythingElseThatAnsweredIsNotAster() {
        for (body in listOf("", "not json", "[]", "{}", """{"ok":true}""", """{"versions":{}}""", """{"versions":{"controller":""}}""", """{"versions":{"controller":7}}""", """{"versions":"1"}""", "<html><body>Welcome</body></html>")) {
            assertEquals(body, AsterReach.NoAster, AsterProbe.classify(200, body))
        }
        assertEquals(AsterReach.NoAster, AsterProbe.classify(200, null))
    }

    @Test
    fun onlyStatus200Counts() {
        for (status in listOf(201, 204, 301, 302, 401, 403, 404, 500, 503)) {
            assertEquals(status.toString(), AsterReach.NoAster, AsterProbe.classify(status, health))
        }
    }

    // Asking

    @Test
    fun asksForTheHealthAnswerWithoutCookies() {
        respond = { it.reply(200, health) }
        assertEquals(AsterReach.Aster, probe())
        val request = requests.single()
        assertEquals("/api/health", request.path)
        assertEquals(null, request.cookie)
        assertEquals("application/json", request.accept)
    }

    @Test
    fun asksUnderThePathOfTheAddress() {
        respond = { it.reply(200, health) }
        assertEquals(AsterReach.Aster, probe("/aster/"))
        assertEquals("/aster/api/health", requests.single().path)
    }

    @Test
    fun otherJsonIsNotAster() {
        respond = { it.reply(200, """{"status":"ok"}""") }
        assertEquals(AsterReach.NoAster, probe())
    }

    @Test
    fun aWebPageIsNotAster() {
        respond = { it.reply(200, "<html><body>It works</body></html>", "text/html") }
        assertEquals(AsterReach.NoAster, probe())
    }

    @Test
    fun aMissingPageIsNotAster() {
        assertEquals(AsterReach.NoAster, probe())
    }

    @Test
    fun aRedirectIsNotFollowed() {
        respond = { exchange ->
            if (exchange.requestURI.path == "/api/health") exchange.reply(302, "", "text/plain", "Location" to "/login") else exchange.reply(200, health)
        }
        assertEquals(AsterReach.NoAster, probe())
        assertEquals(listOf("/api/health"), requests.map { it.path })
    }

    @Test
    fun anOversizedAnswerIsNotAster() {
        respond = { it.reply(200, """{"versions":{"controller":"1"},"pad":"${"x".repeat(200_000)}"}""") }
        assertEquals(AsterReach.NoAster, probe())
    }

    @Test
    fun aServerThatNeverAnswersIsNotReachable() {
        respond = { Thread.sleep(1_500) ; it.close() }
        assertEquals(AsterReach.Unreachable, probe(timeoutMillis = 300))
    }

    @Test
    fun nothingListeningIsNotReachable() {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val closed = AsterAddress.check("http://127.0.0.1:$port/") as AddressCheck.Valid
        assertEquals(AsterReach.Unreachable, runBlocking { AsterProbe.run(closed, 1_000) })
    }

    // How often

    private class Clock(var now: Long = 1_000_000L)

    private fun probes(clock: Clock, found: MutableList<Pair<String, AsterReach>>, asked: MutableList<String>, reach: () -> AsterReach = { AsterReach.Aster }) =
        AsterProbes(
            scope = CoroutineScope(Dispatchers.Unconfined),
            record = { address, result -> found += address to result },
            probe = { asked += it.url; reach() },
            now = { clock.now },
        )

    @Test
    fun anAddressIsLookedAtOnceAMinute() {
        val clock = Clock()
        val found = mutableListOf<Pair<String, AsterReach>>()
        val asked = mutableListOf<String>()
        val probes = probes(clock, found, asked)
        probes.refresh(listOf("http://192.0.2.10/"))
        probes.refresh(listOf("http://192.0.2.10/"))
        clock.now += AsterProbes.MIN_INTERVAL_MILLIS - 1
        probes.refresh(listOf("http://192.0.2.10/"))
        assertEquals(1, asked.size)
        clock.now += 1
        probes.refresh(listOf("http://192.0.2.10/"))
        assertEquals(2, asked.size)
        assertEquals(listOf("http://192.0.2.10/" to AsterReach.Aster, "http://192.0.2.10/" to AsterReach.Aster), found)
    }

    @Test
    fun eachAddressHasItsOwnMinute() {
        val asked = mutableListOf<String>()
        val probes = probes(Clock(), mutableListOf(), asked)
        probes.refresh(listOf("http://192.0.2.10/", "http://192.0.2.20:5099/", "http://192.0.2.10/"))
        probes.refresh(listOf("http://192.0.2.30/"))
        assertEquals(listOf("http://192.0.2.10/", "http://192.0.2.20:5099/", "http://192.0.2.30/"), asked)
    }

    @Test
    fun insistingSkipsTheWait() {
        val asked = mutableListOf<String>()
        val probes = probes(Clock(), mutableListOf(), asked)
        probes.refresh(listOf("http://192.0.2.10/"))
        probes.refresh(listOf("http://192.0.2.10/"), force = true)
        assertEquals(2, asked.size)
    }

    @Test
    fun aSlowLookThatEndsAfterANewerOneIsNotRecorded() {
        val answers = ArrayDeque<CompletableDeferred<AsterReach>>()
        val found = mutableListOf<Pair<String, AsterReach>>()
        val probes = AsterProbes(
            scope = CoroutineScope(Dispatchers.Unconfined),
            record = { address, result -> found += address to result },
            probe = { CompletableDeferred<AsterReach>().also { answers += it }.await() },
            now = { 1_000_000L },
        )
        probes.refresh(listOf("http://192.0.2.10/"))
        probes.refresh(listOf("http://192.0.2.10/"), force = true)
        answers[1].complete(AsterReach.Aster)
        answers[0].complete(AsterReach.Unreachable)
        assertEquals(listOf("http://192.0.2.10/" to AsterReach.Aster), found)
    }

    @Test
    fun anAddressThatCantBeUsedIsNotAsked() {
        val asked = mutableListOf<String>()
        probes(Clock(), mutableListOf(), asked).refresh(listOf("ftp://192.0.2.10/", "", "not an address"))
        assertTrue(asked.isEmpty())
    }
}
