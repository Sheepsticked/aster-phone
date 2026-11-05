package app.asterphone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsterServersTest {
    private fun account(host: String, name: String = "", username: String = "501", enabled: Boolean = true, id: String = "$username@$host") =
        SipAccount(id = id, name = name, username = username, password = "secret", host = host, enabled = enabled)

    private fun build(accounts: List<SipAccount>, data: AsterPanelData = AsterPanelData()) = AsterServers.build(accounts, data)

    // The list

    @Test
    fun oneServerPerHostNamedAfterItsAccounts() {
        val servers = build(
            listOf(
                account("192.0.2.10", name = "Office", username = "501"),
                account("192.0.2.20", name = "Cabin", username = "502"),
                account("192.0.2.10", name = "Home", username = "503"),
            ),
        )
        assertEquals(listOf("Office, Home", "Cabin"), servers.map { it.name })
        assertEquals(listOf("192.0.2.10", "192.0.2.20"), servers.map { it.host })
        assertEquals(listOf("host:192.0.2.10", "host:192.0.2.20"), servers.map { it.key })
    }

    @Test
    fun anAccountWithoutANameIsNamedByItsExtension() {
        assertEquals("501, 502", build(listOf(account("192.0.2.10", username = "501"), account("192.0.2.10", username = "502"))).single().name)
    }

    @Test
    fun hostsDifferingInCaseOrBracketsAreOneServer() {
        val servers = build(
            listOf(
                account("Aster.Example.com", username = "501"),
                account("aster.example.com", username = "502"),
                account("2001:db8::10", username = "503"),
                account("[2001:DB8::10]", username = "504"),
            ),
        )
        assertEquals(listOf("host:aster.example.com", "host:2001:db8::10"), servers.map { it.key })
        assertEquals("Aster.Example.com", servers[0].host)
        assertEquals("2001:db8::10", servers[1].host)
        assertEquals("http://[2001:db8::10]/", servers[1].address)
    }

    @Test
    fun accountsThatAreTurnedOffCountToo() {
        val servers = build(listOf(account("192.0.2.10", enabled = false), account("192.0.2.20")))
        assertEquals(listOf("192.0.2.10", "192.0.2.20"), servers.map { it.host })
    }

    @Test
    fun theSameNameIsListedOnce() {
        assertEquals("Office", build(listOf(account("192.0.2.10", name = "Office", username = "501"), account("192.0.2.10", name = "Office", username = "502"))).single().name)
    }

    @Test
    fun aServerStartsWithTheWebPortOfItsHost() {
        val server = build(listOf(account("192.0.2.10"))).single()
        assertEquals("http://192.0.2.10/", server.address)
        assertEquals("http://192.0.2.10/", server.defaultAddress)
        assertFalse(server.custom)
        assertEquals("192.0.2.10", server.detail)
    }

    @Test
    fun anAddressSetByHandReplacesIt() {
        val data = AsterPanelData(addresses = mapOf("192.0.2.10" to "http://192.0.2.10:5099/"))
        val server = build(listOf(account("192.0.2.10")), data).single()
        assertEquals("http://192.0.2.10:5099/", server.address)
        assertEquals("http://192.0.2.10/", server.defaultAddress)
        assertTrue(server.custom)
        assertEquals("192.0.2.10:5099", server.detail)
    }

    @Test
    fun anAddressThatCantBeUsedFallsBackToTheDefault() {
        val data = AsterPanelData(addresses = mapOf("192.0.2.10" to "ftp://nowhere"))
        assertEquals("http://192.0.2.10/", build(listOf(account("192.0.2.10")), data).single().address)
    }

    @Test
    fun addedPanelsComeAfterTheServersOfAccounts() {
        val data = AsterPanelData(panels = listOf(AddedPanel("p1", "http://192.0.2.50:5098/")))
        val servers = build(listOf(account("192.0.2.10")), data)
        assertEquals(listOf("host:192.0.2.10", "panel:p1"), servers.map { it.key })
        val added = servers.last()
        assertTrue(added.added)
        assertEquals("192.0.2.50:5098", added.name)
        assertEquals("Added panel", added.detail)
    }

    @Test
    fun withoutAnythingThereIsNoServer() {
        assertTrue(build(emptyList()).isEmpty())
    }

    @Test
    fun whatTheLastLookFoundIsShownPerAddress() {
        val data = AsterPanelData(
            addresses = mapOf("192.0.2.20" to "http://192.0.2.20:5099/"),
            probes = mapOf("http://192.0.2.10/" to AsterReach.NoAster, "http://192.0.2.20:5099/" to AsterReach.Aster),
        )
        val servers = build(listOf(account("192.0.2.10"), account("192.0.2.20"), account("192.0.2.30")), data)
        assertEquals(listOf(AsterReach.NoAster, AsterReach.Aster, null), servers.map { it.reach })
        assertEquals(listOf(false, true, false), servers.map { it.isAster })
    }

    // The one shown

    private fun server(key: String, reach: AsterReach?) =
        AsterServer(key, key, key, "http://$key/", "http://$key/", custom = false, added = false, reach = reach)

    @Test
    fun theChosenServerIsShownUnlessItIsKnownToHaveNoAster() {
        val servers = listOf(server("a", AsterReach.Aster), server("b", AsterReach.Aster), server("c", AsterReach.NoAster), server("d", AsterReach.Unreachable), server("e", null))
        assertEquals("b", AsterServers.current(servers, "b")?.key)
        assertEquals("a", AsterServers.current(servers, "c")?.key)
        assertEquals("a", AsterServers.current(servers, null)?.key)
        assertEquals("a", AsterServers.current(servers, "gone")?.key)
        // One that can't be reached stays, so the tab can say so and offer Retry.
        assertEquals("d", AsterServers.current(servers, "d")?.key)
        assertEquals("e", AsterServers.current(servers, "e")?.key)
    }

    @Test
    fun withoutAnAsterThereIsNothingToShow() {
        assertNull(AsterServers.current(listOf(server("c", AsterReach.NoAster), server("e", null)), null))
        assertNull(AsterServers.current(emptyList(), "a"))
    }

    // Whose phones to read

    @Test
    fun aSimLineAsksTheAsterOfTheAccountsOwnServer() {
        val accounts = listOf(account("192.0.2.10", username = "501"), account("192.0.2.20", username = "502"))
        val data = AsterPanelData(addresses = mapOf("192.0.2.20" to "http://192.0.2.20:5098/"))
        val servers = build(accounts, data)
        assertEquals(SimQuery("501", AsterOrigin("http", "192.0.2.10", 80)), AsterServers.simQueryFor(accounts[0], servers))
        assertEquals(SimQuery("502", AsterOrigin("http", "192.0.2.20", 5098)), AsterServers.simQueryFor(accounts[1], servers))
    }

    @Test
    fun anotherServersAsterIsNeverAsked() {
        val mine = account("192.0.2.10")
        val data = AsterPanelData(panels = listOf(AddedPanel("p", "http://192.0.2.50:5098/")))
        // The only Aster is an added panel; the account's own server has none.
        val servers = build(listOf(mine), data.copy(probes = mapOf("http://192.0.2.10/" to AsterReach.NoAster, "http://192.0.2.50:5098/" to AsterReach.Aster)))
        assertNull(AsterServers.simQueryFor(mine, servers))
    }

    @Test
    fun aServerNotLookedAtYetIsAsked() {
        val mine = account("192.0.2.10")
        assertEquals(AsterOrigin("http", "192.0.2.10", 80), AsterServers.simQueryFor(mine, build(listOf(mine)))!!.panel)
    }

    @Test
    fun aServerThatDidntAnswerStillGetsAskedOnceItDoes() {
        val mine = account("192.0.2.10")
        val servers = build(listOf(mine), AsterPanelData(probes = mapOf("http://192.0.2.10/" to AsterReach.Unreachable)))
        assertEquals("501", AsterServers.simQueryFor(mine, servers)!!.username)
    }

    @Test
    fun anAccountOfAServerThatIsGoneHasNoLine() {
        assertNull(AsterServers.simQueryFor(account("192.0.2.99"), build(listOf(account("192.0.2.10")))))
    }

    @Test
    fun accountsWithHostsInOtherSpellingsShareTheServer() {
        val upper = account("Aster.Example.com", username = "501")
        val lower = account("aster.example.com", username = "502")
        val servers = build(listOf(upper, lower))
        assertEquals(AsterServers.simQueryFor(upper, servers)!!.panel, AsterServers.simQueryFor(lower, servers)!!.panel)
    }

    // Choosing and saving

    @Test
    fun savingAServersAddressChoosesTheServer() {
        val servers = build(listOf(account("192.0.2.10"), account("192.0.2.20")))
        val data = AsterPanelData().withServerAddress(servers[1], servers, "http://192.0.2.20:5098/")
        assertEquals(mapOf("192.0.2.20" to "http://192.0.2.20:5098/"), data.addresses)
        assertEquals("host:192.0.2.20", data.selected)
    }

    @Test
    fun theDefaultAddressIsNotStoredButFollowsTheHost() {
        val before = AsterPanelData(addresses = mapOf("192.0.2.10" to "http://192.0.2.10:5099/"))
        val servers = build(listOf(account("192.0.2.10")), before)
        val after = before.withServerAddress(servers.single(), servers, "http://192.0.2.10/")
        assertTrue(after.addresses.isEmpty())
        assertEquals("host:192.0.2.10", after.selected)
    }

    @Test
    fun savingAnAddedPanelKeepsItsId() {
        val data = AsterPanelData(panels = listOf(AddedPanel("p", "http://192.0.2.50:5098/")))
        val servers = build(emptyList(), data)
        val next = data.withServerAddress(servers.single(), servers, "http://192.0.2.50:5099/")
        assertEquals(listOf(AddedPanel("p", "http://192.0.2.50:5099/")), next.panels)
        assertEquals("panel:p", next.selected)
    }

    @Test
    fun addingAPanelChoosesIt() {
        val next = AsterPanelData().withServerAddress(null, emptyList(), "http://192.0.2.50:5098/") { "new" }
        assertEquals(listOf(AddedPanel("new", "http://192.0.2.50:5098/")), next.panels)
        assertEquals("panel:new", next.selected)
    }

    @Test
    fun anAddressSomeServerHasAlreadyIsNotAddedAgain() {
        val servers = build(listOf(account("192.0.2.10")))
        val next = AsterPanelData().withServerAddress(null, servers, "http://192.0.2.10/") { "new" }
        assertTrue(next.panels.isEmpty())
        assertEquals("host:192.0.2.10", next.selected)
    }

    @Test
    fun removingAPanelAlsoUnchoosesIt() {
        val data = AsterPanelData(panels = listOf(AddedPanel("p", "http://192.0.2.50:5098/"), AddedPanel("q", "http://192.0.2.51/")), selected = "panel:p")
        val next = data.withoutPanel("p")
        assertEquals(listOf("q"), next.panels.map { it.id })
        assertNull(next.selected)
        assertEquals("panel:p", data.withoutPanel("q").selected)
    }

    @Test
    fun onlyTheNewestLooksAreKept() {
        var data = AsterPanelData()
        for (i in 1..40) data = data.withProbe("http://192.0.2.$i/", AsterReach.Aster)
        assertEquals(32, data.probes.size)
        assertFalse("http://192.0.2.1/" in data.probes)
        assertTrue("http://192.0.2.40/" in data.probes)
        assertTrue("http://192.0.2.40/" !in data.withProbe("http://192.0.2.40/", null).probes)
    }

    // The earlier single address

    @Test
    fun theOldAddressBecomesTheAddressOfTheServerOnItsHost() {
        val accounts = listOf(account("192.0.2.10"), account("192.0.2.20"))
        val next = AsterPanelData().migrated("http://192.0.2.20:5099/", accounts)
        assertEquals(mapOf("192.0.2.20" to "http://192.0.2.20:5099/"), next.addresses)
        assertTrue(next.panels.isEmpty())
        assertEquals("host:192.0.2.20", next.selected)
    }

    @Test
    fun anOldAddressThatIsTheDefaultNeedsNoEntry() {
        val next = AsterPanelData().migrated("http://192.0.2.10/", listOf(account("192.0.2.10")))
        assertTrue(next.addresses.isEmpty())
        assertEquals("host:192.0.2.10", next.selected)
    }

    @Test
    fun theHostIsMatchedWithoutCaseOrBrackets() {
        val next = AsterPanelData().migrated("http://[2001:DB8::10]:8080/", listOf(account("2001:db8::10")))
        assertEquals(setOf("2001:db8::10"), next.addresses.keys)
        assertTrue(next.panels.isEmpty())
    }

    @Test
    fun anOldAddressOfNoAccountBecomesAnAddedPanel() {
        val next = AsterPanelData().migrated("http://192.0.2.50:5098/", listOf(account("192.0.2.10")), newId = { "p" })
        assertTrue(next.addresses.isEmpty())
        assertEquals(listOf(AddedPanel("p", "http://192.0.2.50:5098/")), next.panels)
        assertEquals("panel:p", next.selected)
    }

    @Test
    fun withoutAccountsTheOldAddressIsAnAddedPanel() {
        assertEquals(1, AsterPanelData().migrated("http://192.0.2.10/", emptyList()).panels.size)
    }

    @Test
    fun migratingTwiceAddsNothingMore() {
        val once = AsterPanelData().migrated("http://192.0.2.50:5098/", emptyList(), newId = { "p" })
        assertEquals(once, once.migrated("http://192.0.2.50:5098/", emptyList(), newId = { "other" }))
    }

    @Test
    fun aServerChosenAlreadyStaysChosen() {
        val next = AsterPanelData(selected = "host:192.0.2.10").migrated("http://192.0.2.50:5098/", emptyList(), newId = { "p" })
        assertEquals("host:192.0.2.10", next.selected)
    }

    @Test
    fun anAddressSetForTheHostAlreadyIsKept() {
        val data = AsterPanelData(addresses = mapOf("192.0.2.10" to "http://192.0.2.10:5098/"))
        assertEquals(data.addresses, data.migrated("http://192.0.2.10:5099/", listOf(account("192.0.2.10"))).addresses)
    }

    @Test
    fun anOldAddressThatIsNoAddressChangesNothing() {
        val data = AsterPanelData()
        assertEquals(data, data.migrated("ftp://x", emptyList()))
        assertEquals(data, data.migrated("", emptyList()))
    }

    // Stored form

    @Test
    fun theStoredFormReadsBackTheSame() {
        val data = AsterPanelData(
            addresses = mapOf("192.0.2.10" to "http://192.0.2.10:5099/"),
            panels = listOf(AddedPanel("p", "http://192.0.2.50:5098/")),
            selected = "panel:p",
            probes = mapOf("http://192.0.2.10:5099/" to AsterReach.Aster, "http://192.0.2.50:5098/" to AsterReach.NoAster, "http://192.0.2.60/" to AsterReach.Unreachable),
        )
        assertEquals(data, AsterPanelCodec.decode(AsterPanelCodec.encode(data)))
        assertEquals(AsterPanelData(), AsterPanelCodec.decode(AsterPanelCodec.encode(AsterPanelData())))
    }

    @Test
    fun unusableStoredDataIsLeftOut() {
        assertEquals(AsterPanelData(), AsterPanelCodec.decode(null))
        assertEquals(AsterPanelData(), AsterPanelCodec.decode("not json"))
        val text = """{"addresses":{"a":1,"b":"http://192.0.2.10/"},"panels":[{"id":"","address":"http://192.0.2.50/"},{"id":"p","address":"ftp://x"},5,{"id":"q","address":"http://192.0.2.51/"}],
            "probes":{"http://192.0.2.10/":"aster","http://192.0.2.11/":"maybe"}}"""
        val data = AsterPanelCodec.decode(text)
        assertEquals(mapOf("b" to "http://192.0.2.10/"), data.addresses)
        assertEquals(listOf(AddedPanel("q", "http://192.0.2.51/")), data.panels)
        assertEquals(mapOf("http://192.0.2.10/" to AsterReach.Aster), data.probes)
        assertNull(data.selected)
    }
}
