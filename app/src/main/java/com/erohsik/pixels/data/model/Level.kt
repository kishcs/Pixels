package com.erohsik.pixels.data.model

object Level {
    const val MIN = 1
    const val MAX = 5
    const val COUNT = MAX - MIN + 1

    /** Grid value for a day with no entry. */
    const val EMPTY = 0

    val all: IntRange = MIN..MAX

    fun isValid(level: Int): Boolean = level in all
}
