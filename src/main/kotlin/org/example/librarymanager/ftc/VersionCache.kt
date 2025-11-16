package org.example.librarymanager.ftc

object VersionCache {
    private val map = mutableMapOf<String, List<String>>()

    fun get(key: String): List<String>? = map[key]

    fun put(key: String, versions: List<String>) {
        map[key] = versions
    }
}