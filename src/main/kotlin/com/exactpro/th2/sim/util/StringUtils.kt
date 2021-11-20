package com.exactpro.th2.sim.util

import java.util.regex.Pattern

fun findSubstring(charSequence: CharSequence, regex: String, fromIndex: Int): String {
    val pattern = regex.toRegex()
    return pattern.find(charSequence)?.value ?: ""
}

fun findSubstring(charSequence: CharSequence, regex: String): String {
    return findSubstring(charSequence, regex, 0)
}