package app.asterphone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {
    // Numbers from ranges reserved for fiction and documentation.
    private val alice = PhoneContact("Alice Smith", "+44 20 7946 0018", "Mobile")
    private val bob = PhoneContact("Bob Jones", "+1 202-555-0143", "Work")
    private val carol = PhoneContact("Carol", "020 7946 0999")
    private val reception = PhoneContact("Reception", "600")
    private val directory = ContactDirectory(listOf(alice, bob, carol, reception))

    private fun names(typed: String) = directory.search(typed).map { it.contact.name }

    @Test
    fun findsNumbersContainingTheDigitsAndMarksThem() {
        val match = directory.search("7946").first()
        assertEquals("Alice Smith", match.contact.name)
        // Characters 7 to 10 of "+44 20 7946 0018".
        assertEquals(7..10, match.numberRange)
        assertEquals(listOf("Alice Smith", "Carol"), names("7946"))
    }

    @Test
    fun numbersStartingWithTheDigitsComeFirst() {
        val contacts = ContactDirectory(listOf(PhoneContact("Middle", "+1234567890"), PhoneContact("Start", "3456 789")))
        assertEquals(listOf("Start", "Middle"), contacts.search("3456").map { it.contact.name })
    }

    @Test
    fun aTrunkPrefixFindsTheInternationalForm() {
        val match = directory.search("020794600").map { it.contact.name }
        assertEquals(listOf("Alice Smith"), match)
        assertEquals(4..13, directory.search("020794600").first().numberRange)
    }

    @Test
    fun digitsSpellingANameOnTheKeypadFindNothing() {
        // J-O-N-E = 5-6-6-3, A-L-I-C-E = 2-5-4-2-3; neither number contains these digits.
        assertTrue(directory.search("5663").isEmpty())
        assertTrue(directory.search("25423").isEmpty())
    }

    @Test
    fun anExactNumberComesFirstThenTheStartThenTheMiddle() {
        val middle = PhoneContact("Middle", "+1 555 0100")
        val start = PhoneContact("Start", "0100 2345")
        val exact = PhoneContact("Exact", "01 00")
        val contacts = ContactDirectory(listOf(middle, start, exact))
        assertEquals(listOf("Exact", "Start", "Middle"), contacts.search("0100").map { it.contact.name })
    }

    @Test
    fun oneDigitIsNotEnoughToSuggest() {
        assertTrue(directory.search("7").isEmpty())
        assertTrue(directory.search("").isEmpty())
        assertTrue(directory.search("*#").isEmpty())
    }

    @Test
    fun aFullNumberMatchComesFirst() {
        val contacts = ContactDirectory(listOf(PhoneContact("Long", "6001"), reception))
        assertEquals(listOf("Reception", "Long"), contacts.search("600").map { it.contact.name })
    }

    @Test
    fun callersAreNamedWhicheverWayTheNumberIsWritten() {
        assertEquals("Alice Smith", directory.nameFor("+442079460018"))
        assertEquals("Alice Smith", directory.nameFor("02079460018"))
        assertEquals("Carol", directory.nameFor("+44 20 7946 0999"))
        assertEquals("Bob Jones", directory.nameFor("2025550143"))
        assertEquals("Reception", directory.nameFor("600"))
    }

    @Test
    fun callersWithOtherNumbersStayUnnamed() {
        assertNull(directory.nameFor("+12079460018"))
        assertNull(directory.nameFor("6000"))
        assertNull(directory.nameFor("00"))
        assertNull(directory.nameFor("anonymous"))
    }

    @Test
    fun shortNumbersMustMatchExactly() {
        assertTrue(ContactDirectory.sameNumber("600", "600"))
        assertFalse(ContactDirectory.sameNumber("600", "1600"))
        assertTrue(ContactDirectory.sameNumber("+44 20 7946 0018", "020 7946 0018"))
        assertFalse(ContactDirectory.sameNumber("+44 20 7946 0018", "+1 20 7946 0018"))
    }
}
