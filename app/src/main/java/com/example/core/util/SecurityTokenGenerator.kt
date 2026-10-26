package com.example.core.util

import java.security.SecureRandom

object SecurityTokenGenerator {
    private const val ALLOWED_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val secureRandom = SecureRandom()

    fun generateInviteCode(prefix: String, length: Int = 6): String {
        require(length > 0) { "Code length must be greater than 0" }
        val suffix = (1..length)
            .map { ALLOWED_CHARS[secureRandom.nextInt(ALLOWED_CHARS.length)] }
            .joinToString("")
        return "$prefix-$suffix"
    }

    fun isHighEntropyAlphanumeric(code: String): Boolean {
        // Must contain prefix-suffix and only unambiguous characters
        val parts = code.split("-")
        if (parts.size != 2) return false
        val suffix = parts[1]
        return suffix.length >= 6 && suffix.all { it in ALLOWED_CHARS }
    }
}
