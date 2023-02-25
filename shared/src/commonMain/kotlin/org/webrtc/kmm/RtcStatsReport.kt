package org.webrtc.kmm

expect class RtcStatsReport {
    val timestampUs: Long
    val stats: Map<String, RtcStats>

    override fun toString(): String
}
