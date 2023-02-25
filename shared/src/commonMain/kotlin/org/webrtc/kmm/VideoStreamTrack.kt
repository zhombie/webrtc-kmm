package org.webrtc.kmm

expect class VideoStreamTrack : MediaStreamTrack {
    suspend fun switchCamera(deviceId: String? = null)
}
