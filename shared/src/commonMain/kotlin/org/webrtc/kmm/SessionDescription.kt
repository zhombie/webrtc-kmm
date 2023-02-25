package org.webrtc.kmm

data class SessionDescription(val type: SessionDescriptionType, val sdp: String)

enum class SessionDescriptionType { Offer, Pranswer, Answer, Rollback }
