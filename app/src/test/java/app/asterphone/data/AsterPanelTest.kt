package app.asterphone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsterPanelTest {
    private fun account(host: String, enabled: Boolean = true, id: String = host) =
        SipAccount(id = id, name = "", username = "501", password = "secret", host = host, enabled = enabled)

    private fun origin(url: String) = (AsterAddress.check(url) as AddressCheck.Valid).origin

    private fun problem(text: String) = (AsterAddress.check(text) as AddressCheck.Invalid).reason

    @Test
    fun suggestsTheFirstAccountsServer() {
        assertEquals("http://192.0.2.10/", AsterAddress.defaultFor(listOf(account("192.0.2.10"), account("192.0.2.20"))))
        assertEquals("http://aster.example.com/", AsterAddress.defaultFor(listOf(account("aster.example.com"))))
    }

    @Test
    fun bracketsIpv6Servers() {
        assertEquals("http://[2001:db8::10]/", AsterAddress.defaultFor(listOf(account("2001:db8::10"))))
        assertEquals("http://[2001:db8::10]/", AsterAddress.defaultFor(listOf(account("[2001:db8::10]"))))
    }

    @Test
    fun suggestsNothingWithoutAnAccount() {
        assertEquals("", AsterAddress.defaultFor(emptyList()))
    }

    @Test
    fun skipsAccountsThatAreTurnedOff() {
        val accounts = listOf(account("192.0.2.10", enabled = false), account("192.0.2.20"))
        assertEquals("http://192.0.2.20/", AsterAddress.defaultFor(accounts))
        assertEquals("", AsterAddress.defaultFor(listOf(account("192.0.2.10", enabled = false))))
    }

    @Test
    fun suggestedAddressesAreValid() {
        for (host in listOf("192.0.2.10", "aster.example.com", "2001:db8::10")) {
            assertTrue(AsterAddress.check(AsterAddress.defaultFor(listOf(account(host)))) is AddressCheck.Valid)
        }
    }

    @Test
    fun acceptsHttpAndHttpsWithOrWithoutPort() {
        assertEquals(AsterOrigin("http", "192.0.2.10", 80), origin("http://192.0.2.10/"))
        assertEquals(AsterOrigin("http", "192.0.2.10", 5099), origin("http://192.0.2.10:5099/"))
        assertEquals(AsterOrigin("https", "aster.example.com", 443), origin("https://aster.example.com"))
        assertEquals(AsterOrigin("https", "aster.example.com", 8443), origin("HTTPS://Aster.Example.com:8443/panel/"))
        assertEquals(AsterOrigin("http", "[2001:db8::10]", 8080), origin("http://[2001:db8::10]:8080/"))
        assertEquals(AsterOrigin("http", "[::1]", 80), origin("http://[::1]/"))
    }

    @Test
    fun trimsAndAddsTheRootPath() {
        assertEquals("http://192.0.2.10/", (AsterAddress.check("  http://192.0.2.10 ") as AddressCheck.Valid).url)
        assertEquals("http://192.0.2.10:5099/", (AsterAddress.check("http://192.0.2.10:5099") as AddressCheck.Valid).url)
        assertEquals("http://192.0.2.10/panel", (AsterAddress.check("http://192.0.2.10/panel") as AddressCheck.Valid).url)
    }

    @Test
    fun rejectsAddressesWithAnotherScheme() {
        for (text in listOf("ftp://192.0.2.10/", "file:///sdcard/x.html", "javascript:alert(1)", "//192.0.2.10/", "mailto:admin@example.com", "ftp:/192.0.2.10", "http:192.0.2.10")) {
            assertEquals(text, "Start with http:// or https://", problem(text))
        }
    }

    @Test
    fun takesAnAddressWithoutASchemeAsHttp() {
        val cases = mapOf(
            "192.0.2.10" to "http://192.0.2.10/",
            "192.0.2.10:8080" to "http://192.0.2.10:8080/",
            "  192.0.2.10:8080/panel " to "http://192.0.2.10:8080/panel",
            "aster" to "http://aster/",
            "Aster.Example.com:5099" to "http://Aster.Example.com:5099/",
            "localhost:5099/calls?page=2" to "http://localhost:5099/calls?page=2",
            "[2001:db8::10]:8080" to "http://[2001:db8::10]:8080/",
            "[::1]" to "http://[::1]/",
        )
        for ((text, url) in cases) {
            val result = AsterAddress.check(text)
            assertTrue(text, result is AddressCheck.Valid)
            assertEquals(text, url, (result as AddressCheck.Valid).url)
        }
        assertEquals(AsterOrigin("http", "192.0.2.10", 8080), origin("192.0.2.10:8080"))
        assertEquals(AsterOrigin("http", "aster.example.com", 80), origin("Aster.Example.com"))
        assertEquals(AsterOrigin("http", "[2001:db8::10]", 8080), origin("[2001:db8::10]:8080"))
    }

    @Test
    fun keepsHttpsAsTyped() {
        assertEquals("https://192.0.2.10/", (AsterAddress.check("https://192.0.2.10") as AddressCheck.Valid).url)
        assertEquals(AsterOrigin("https", "aster.example.com", 8443), origin("https://aster.example.com:8443/"))
    }

    @Test
    fun stillRejectsBadAddressesWithoutAScheme() {
        assertEquals("Port must be between 1 and 65535", problem("192.0.2.10:99999"))
        assertEquals("Port must be between 1 and 65535", problem("aster:99999"))
        assertEquals("Leave the user name and password out", problem("admin@192.0.2.10"))
        assertEquals("That isn't a valid host name or IP address", problem("aster panel"))
        assertEquals("Enter the address of Aster", problem("  "))
    }

    @Test
    fun anAddressThatCameFromAPageNeedsItsScheme() {
        for (text in listOf("192.0.2.10", "192.0.2.10:5099/x", "aster/calls", "[::1]:80")) {
            assertEquals(text, "Start with http:// or https://", (AsterAddress.checkUrl(text) as AddressCheck.Invalid).reason)
        }
        assertFalse(origin("http://192.0.2.10:5099/").allows("192.0.2.10:5099/calls"))
        assertTrue(AsterAddress.checkUrl("http://192.0.2.10:5099/calls") is AddressCheck.Valid)
    }

    @Test
    fun buildsPagesOnTheOrigin() {
        assertEquals("http://192.0.2.10/phones", origin("http://192.0.2.10/").urlFor("/phones"))
        assertEquals("http://192.0.2.10:5099/modems/gsm1", origin("http://192.0.2.10:5099/").urlFor("/modems/gsm1"))
        assertEquals("https://aster.example.com/phones", origin("https://aster.example.com:443/panel").urlFor("/phones"))
        assertEquals("https://aster.example.com:8443/phones", origin("https://aster.example.com:8443/").urlFor("/phones"))
        assertEquals("http://[2001:db8::10]:8080/phones", origin("http://[2001:db8::10]:8080/").urlFor("/phones"))
        val aster = origin("http://192.0.2.10:5099/")
        assertTrue(aster.allows(aster.urlFor("/modems/gsm1")))
    }

    @Test
    fun rejectsEmptyAddresses() {
        assertEquals("Enter the address of Aster", problem(""))
        assertEquals("Enter the address of Aster", problem("   "))
        assertEquals("Enter a host name or IP address after http://", problem("http://"))
        assertEquals("Enter a host name or IP address after https://", problem("https:///panel"))
    }

    @Test
    fun rejectsBadPorts() {
        for (text in listOf("http://192.0.2.10:0/", "http://192.0.2.10:65536/", "http://192.0.2.10:abc/", "http://192.0.2.10:80:90/", "http://[::1]:99999/")) {
            assertEquals(text, "Port must be between 1 and 65535", problem(text))
        }
        assertTrue(AsterAddress.check("http://192.0.2.10:65535/") is AddressCheck.Valid)
        assertTrue(AsterAddress.check("http://192.0.2.10:1/") is AddressCheck.Valid)
    }

    @Test
    fun rejectsUserInfoSpacesAndOddHosts() {
        assertEquals("Leave the user name and password out", problem("http://admin:secret@192.0.2.10/"))
        assertEquals("Leave the user name and password out", problem("http://admin@192.0.2.10/"))
        assertEquals("This address can't contain backslashes", problem("http://evil.example\\@192.0.2.10/"))
        for (text in listOf("http://192.0.2.10 /", "http://aster panel/", "http://[::1/", "http://[::1]x/", "http://[2001:db8]/", "http://ästers.example/")) {
            assertEquals(text, "That isn't a valid host name or IP address", problem(text))
        }
    }

    @Test
    fun allowsOnlyTheSameOrigin() {
        val aster = origin("http://192.0.2.10:5099/")
        assertTrue(aster.allows("http://192.0.2.10:5099/"))
        assertTrue(aster.allows("http://192.0.2.10:5099/messages?page=2#top"))
        assertTrue(aster.allows("HTTP://192.0.2.10:5099/Calls"))
        assertFalse("other host", aster.allows("http://192.0.2.11:5099/"))
        assertFalse("other port", aster.allows("http://192.0.2.10:5100/"))
        assertFalse("default port", aster.allows("http://192.0.2.10/"))
        assertFalse("other scheme", aster.allows("https://192.0.2.10:5099/"))
        assertFalse("subdomain", origin("http://aster.example.com/").allows("http://www.aster.example.com/"))
        assertFalse("parent domain", origin("http://www.aster.example.com/").allows("http://aster.example.com/"))
    }

    @Test
    fun treatsDefaultPortsAsTheSamePort() {
        assertTrue(origin("http://192.0.2.10/").allows("http://192.0.2.10:80/x"))
        assertTrue(origin("http://192.0.2.10:80/").allows("http://192.0.2.10/x"))
        assertTrue(origin("https://aster.example.com/").allows("https://aster.example.com:443/x"))
        assertFalse(origin("http://aster.example.com/").allows("https://aster.example.com/"))
    }

    @Test
    fun refusesLinksThatHideAnotherHost() {
        val aster = origin("http://192.0.2.10/")
        assertFalse(aster.allows("http://evil.example\\@192.0.2.10/"))
        assertFalse(aster.allows("http://192.0.2.10@evil.example/"))
        assertFalse(aster.allows("http://192.0.2.10:80@evil.example/"))
        assertFalse(aster.allows("http://evil.example/http://192.0.2.10/"))
        assertFalse(aster.allows("http://192.0.2.10.evil.example/"))
        assertFalse(aster.allows("javascript:alert(1)"))
        assertFalse(aster.allows("file:///sdcard/Download/x.html"))
        assertFalse(aster.allows("content://com.example/x"))
        assertFalse(aster.allows("data:text/html,x"))
        assertFalse(aster.allows("about:blank"))
        assertFalse(aster.allows("intent://192.0.2.10/#Intent;scheme=http;end"))
        assertFalse(aster.allows(""))
    }

    @Test
    fun comparesIpv6HostsWithTheirBrackets() {
        val aster = origin("http://[2001:db8::10]:8080/")
        assertTrue(aster.allows("http://[2001:db8::10]:8080/calls"))
        assertTrue(aster.allows("http://[2001:DB8::10]:8080/calls"))
        assertFalse(aster.allows("http://[2001:db8::11]:8080/calls"))
        assertFalse(aster.allows("http://[2001:db8::10]/calls"))
    }

    @Test
    fun namesDownloadsAfterTheServersFileName() {
        val url = "http://192.0.2.10:5099/api/backup"
        assertEquals("aster-20260101T000000Z.tar.gz", AsterDownloads.fileName(url, """attachment; filename="aster-20260101T000000Z.tar.gz""""))
        assertEquals("backup.tar.gz", AsterDownloads.fileName(url, "attachment; filename=backup.tar.gz"))
        assertEquals("backup.tar.gz", AsterDownloads.fileName(url, "attachment; filename=backup.tar.gz; size=10"))
        assertEquals("my backup.tar.gz", AsterDownloads.fileName(url, "attachment; filename*=UTF-8''my%20backup.tar.gz"))
        assertEquals("ünï.tar.gz", AsterDownloads.fileName(url, "attachment; filename*=UTF-8''%C3%BCn%C3%AF.tar.gz"))
    }

    @Test
    fun prefersTheExtendedFileNameAndUnescapesQuotedPairs() {
        val url = "http://192.0.2.10/api/backup"
        assertEquals("real name.tar.gz", AsterDownloads.fileName(url, """attachment; filename="plain.tar.gz"; filename*=UTF-8''real%20name.tar.gz"""))
        assertEquals("ab.txt", AsterDownloads.fileName(url, """attachment; filename="a\"b.txt""""))
    }

    @Test
    fun fileNamesStayInsideDownloads() {
        val url = "http://192.0.2.10/api/backup"
        assertEquals("passwd", AsterDownloads.fileName(url, """attachment; filename="../../etc/passwd""""))
        assertEquals("evil.sh", AsterDownloads.fileName(url, """attachment; filename="..\..\evil.sh""""))
        assertEquals("hidden", AsterDownloads.fileName(url, """attachment; filename=".hidden""""))
        assertEquals("ab.txt", AsterDownloads.fileName(url, "attachment; filename*=UTF-8''a%00b.txt"))
        assertEquals("ab.txt", AsterDownloads.fileName(url, """attachment; filename="a:b*.txt""""))
        assertTrue(AsterDownloads.fileName(url, "attachment; filename=" + "x".repeat(500)).length <= 120)
    }

    @Test
    fun fallsBackToTheUrlThenToAGenericName() {
        assertEquals("backup", AsterDownloads.fileName("http://192.0.2.10/api/backup", null))
        assertEquals("export.csv", AsterDownloads.fileName("http://192.0.2.10/api/export.csv?x=1#y", "attachment"))
        assertEquals("aster-download", AsterDownloads.fileName("http://192.0.2.10/", null))
        assertEquals("aster-download", AsterDownloads.fileName("http://192.0.2.10/api/", """attachment; filename="..""""))
    }
}
