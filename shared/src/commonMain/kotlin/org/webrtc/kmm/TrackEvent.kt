package org.webrtc.kmm

class TrackEvent internal constructor(
    val receiver: RtpReceiver,
    val streams: List<MediaStream>,
    val track: MediaStreamTrack?,
    val transceiver: RtpTransceiver
)
