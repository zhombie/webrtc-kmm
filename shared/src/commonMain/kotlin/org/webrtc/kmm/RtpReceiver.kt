package org.webrtc.kmm

expect class RtpReceiver {
    val id: String
    val track: MediaStreamTrack?
    val parameters: RtpParameters
}
