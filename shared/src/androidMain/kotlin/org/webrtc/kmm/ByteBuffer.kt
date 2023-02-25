package org.webrtc.kmm

import java.nio.ByteBuffer

fun ByteBuffer.toByteArray(): ByteArray {
    val bytes = ByteArray(remaining())
    get(bytes)
    rewind()
    return bytes
}
