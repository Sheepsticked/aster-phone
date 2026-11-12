package app.asterphone.data

/** A phone number from the phone's contacts. [type] is its label, such as "Mobile". */
data class PhoneContact(val name: String, val number: String, val type: String = "") {
    internal val digits: String = number.filter { it.isDigit() }
}

/** A contact matching the typed digits. [numberRange] marks the matching characters of its number. */
data class ContactMatch(val contact: PhoneContact, val numberRange: IntRange? = null)

/** The phone's contacts, searchable by the digits of their number and by caller number. */
class ContactDirectory(val contacts: List<PhoneContact>) {
    // Contacts by the last digits of their number, to find a caller without scanning every contact.
    private val byTail: Map<String, List<PhoneContact>> = contacts.groupBy { it.digits.takeLast(MIN_MATCH) }

    /** The contact name for a caller's [number], or null if no contact has it. */
    fun nameFor(number: String): String? {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return byTail[digits.takeLast(MIN_MATCH)]?.firstOrNull { sameNumber(it.number, number) }?.name
    }

    /**
     * Contacts whose number contains the digits of [typed]: an exact number first, then numbers that start
     * with the digits, then the rest.
     */
    fun search(typed: String): List<ContactMatch> {
        val query = typed.filter { it.isDigit() }
        if (query.length < MIN_QUERY) return emptyList()
        return contacts.mapNotNull { contact -> rank(contact, query) }.sortedBy { it.first }.map { it.second }
    }

    private fun rank(contact: PhoneContact, query: String): Pair<Int, ContactMatch>? {
        val number = numberMatch(contact, query) ?: return null
        val rank = when {
            contact.digits == query -> 0
            number.atStart -> 1
            else -> 2
        }
        return rank to ContactMatch(contact, contact.numberChars(number.from, number.length))
    }

    private class NumberHit(val from: Int, val length: Int, val atStart: Boolean)

    private fun numberMatch(contact: PhoneContact, query: String): NumberHit? {
        val at = contact.digits.indexOf(query)
        if (at >= 0) return NumberHit(at, query.length, atStart = at == 0)
        // A number dialled at home starts with a trunk prefix where the stored international form has a
        // country code of one to three digits, as in 020… for +44 20….
        if (query.length > MIN_QUERY && query[0] in TRUNK_PREFIXES && contact.number.trimStart().startsWith("+")) {
            val national = query.substring(1)
            val from = contact.digits.indexOf(national)
            if (from in 1..3) return NumberHit(from, national.length, atStart = true)
        }
        return null
    }

    companion object {
        private const val MIN_QUERY = 2
        // Caller numbers match a contact when at least this many trailing digits agree, as Android's own caller ID does.
        private const val MIN_MATCH = 7
        // 0 in most countries, 8 in a few.
        private const val TRUNK_PREFIXES = "08"

        /**
         * Whether two numbers reach the same phone, as in +44 20 7946 0018 and 020 7946 0018.
         * Short numbers such as extensions must match exactly.
         */
        fun sameNumber(a: String, b: String): Boolean {
            val x = national(a)
            val y = national(b)
            if (x.length < MIN_MATCH || y.length < MIN_MATCH) return a.filter { it.isDigit() } == b.filter { it.isDigit() }
            return if (x.length >= y.length) x.endsWith(y) else y.endsWith(x)
        }

        // The digits without a leading trunk prefix; international numbers keep their country code.
        private fun national(number: String): String {
            val digits = number.filter { it.isDigit() }
            val international = number.trimStart().startsWith("+")
            return if (!international && digits.length > MIN_MATCH && digits[0] in TRUNK_PREFIXES) digits.substring(1) else digits
        }
    }
}

/** The characters of [number] spanned by [length] digits from digit [from], with any formatting between them. */
private fun PhoneContact.numberChars(from: Int, length: Int): IntRange {
    val positions = number.indices.filter { number[it].isDigit() }
    return positions[from]..positions[from + length - 1]
}
