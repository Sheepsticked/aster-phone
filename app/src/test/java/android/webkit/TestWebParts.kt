package android.webkit

// In the framework's package because these classes can't be built from outside it.

class TestWebResourceError(private val code: Int, private val text: String) : WebResourceError() {
    override fun getErrorCode() = code

    override fun getDescription(): CharSequence = text
}

class TestSslErrorHandler : SslErrorHandler() {
    var cancelled = false
    var proceeded = false

    override fun cancel() {
        cancelled = true
    }

    override fun proceed() {
        proceeded = true
    }
}
