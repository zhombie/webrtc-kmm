package org.webrtc.kmm

import cocoapods.WebRTC.RTCCleanupSSL
import cocoapods.WebRTC.RTCDefaultVideoDecoderFactory
import cocoapods.WebRTC.RTCDefaultVideoEncoderFactory
import cocoapods.WebRTC.RTCInitFieldTrialDictionary
import cocoapods.WebRTC.RTCInitializeSSL
import cocoapods.WebRTC.RTCLoggingSeverity
import cocoapods.WebRTC.RTCPeerConnectionFactory
import cocoapods.WebRTC.RTCPeerConnectionFactoryOptions
import cocoapods.WebRTC.RTCSetMinDebugLogLevel
import cocoapods.WebRTC.RTCSetupInternalTracer
import cocoapods.WebRTC.RTCShutdownInternalTracer

@ThreadLocal
actual object WebRtc {

    private var _peerConnectionFactory: RTCPeerConnectionFactory? = null
    internal val peerConnectionFactory: RTCPeerConnectionFactory
        get() {
            if (_peerConnectionFactory == null) initialize()
            return checkNotNull(_peerConnectionFactory)
        }

    private var builder = WebRtcBuilder()

    fun configureBuilder(block: WebRtcBuilder.() -> Unit) {
        block(builder)
    }

    actual fun initialize() {
        initializePeerConnectionFactory()
        _peerConnectionFactory = buildPeerConnectionFactory(builder.peerConnectionFactoryOptions)
    }

    @Suppress("UNCHECKED_CAST")
    private fun initializePeerConnectionFactory() {
        with(builder) {
            RTCInitFieldTrialDictionary(fieldTrials as Map<Any?, *>)
            RTCInitializeSSL()
            loggingSeverity?.also { RTCSetMinDebugLogLevel(it) }
            if (enableInternalTracer) {
                RTCSetupInternalTracer()
            }
        }
    }

    private fun buildPeerConnectionFactory(options: RTCPeerConnectionFactoryOptions?): RTCPeerConnectionFactory {
        val factory = RTCPeerConnectionFactory(
            RTCDefaultVideoEncoderFactory(),
            RTCDefaultVideoDecoderFactory()
        )
        options?.also { factory.setOptions(options) }
        return factory
    }

    actual fun dispose() {
        RTCShutdownInternalTracer()
        RTCCleanupSSL()
    }
}

@Deprecated(
    "Use WebRtc.initialize()",
    replaceWith = ReplaceWith("WebRtc.initialize()")
)
fun initializeWebRtc(build: WebRtcBuilder.() -> Unit = {}) {
    WebRtc.configureBuilder(build)
    WebRtc.initialize()
}

class WebRtcBuilder(
    var peerConnectionFactoryOptions: RTCPeerConnectionFactoryOptions? = null,
    var fieldTrials: Map<String, String> = emptyMap(),
    var enableInternalTracer: Boolean = false,
    var loggingSeverity: RTCLoggingSeverity? = null
)
