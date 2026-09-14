package com.ampgames.vidsaver.core.text

/**
 * A forgiving Base64 decoder for the small encoded blobs sites tuck into URLs.
 *
 * `java.util.Base64` needs API 26 and the app supports 24, and `android.util`
 * cannot run in a plain JVM test. This handles the standard and URL-safe
 * alphabets, missing padding, and stray whitespace, and returns null rather
 * than throwing on garbage: callers are parsing untrusted query strings.
 */
object Base64Lite {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val VALUES = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { i, c -> table[c.code] = i }
        table['-'.code] = 62 // URL-safe alphabet
        table['_'.code] = 63
    }

    fun decode(text: String): ByteArray? {
        val out = ArrayList<Byte>(text.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c == '=' || c.isWhitespace()) continue
            val value = if (c.code < 128) VALUES[c.code] else -1
            if (value < 0) return null
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    fun decodeToString(text: String): String? = decode(text)?.toString(Charsets.UTF_8)
}
