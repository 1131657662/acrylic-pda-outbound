package com.acrylic.m71demo.domain

interface DemoSigner {
    fun sign(claim: String): String
}
