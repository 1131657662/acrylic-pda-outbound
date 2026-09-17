package com.acrylic.m71demo.domain

interface AppClock {
    fun wallMs(): Long
    fun elapsedNs(): Long
    fun bootId(): String
}
