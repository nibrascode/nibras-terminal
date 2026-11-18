package com.nibras.terminal

data class Component(
    val id: String,
    val group: String,
    val name: String,
    val summary: String,
    val version: String,
    val abi: String,
    val filename: String,
    val sizeBytes: Long,
    val sha256: String,
    val checksumOrigin: String,
    val sourceUrl: String,
    val sourceNote: String,
    val license: String,
    val kind: String,
    val url: String,
)

data class InstallRecord(
    val id: String,
    val filename: String,
    val sha256: String,
    val bytes: Long,
    val savedAt: Long,
    val extracted: Boolean,
)

enum class Tab { Home, Downloads, Session, Files }
