package org.webrtc.kmm

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform