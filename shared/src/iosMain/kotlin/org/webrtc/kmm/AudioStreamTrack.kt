package org.webrtc.kmm

import WebRTC.RTCAudioTrack

actual class AudioStreamTrack internal constructor(ios: RTCAudioTrack) : MediaStreamTrack(ios) {

    override fun onSetEnabled(enabled: Boolean) {
        // ignore
    }

    override fun onStop() {
        // ignore
    }
}
