package org.webrtc.kmm

import cocoapods.WebRTC.RTCAudioTrack
import cocoapods.WebRTC.RTCDataChannel
import cocoapods.WebRTC.RTCDataChannelConfiguration
import cocoapods.WebRTC.RTCIceCandidate
import cocoapods.WebRTC.RTCIceConnectionState
import cocoapods.WebRTC.RTCIceGatheringState
import cocoapods.WebRTC.RTCMediaConstraints
import cocoapods.WebRTC.RTCMediaStream
import cocoapods.WebRTC.RTCPeerConnection
import cocoapods.WebRTC.RTCPeerConnectionDelegateProtocol
import cocoapods.WebRTC.RTCPeerConnectionState
import cocoapods.WebRTC.RTCRtpReceiver
import cocoapods.WebRTC.RTCRtpSender
import cocoapods.WebRTC.RTCRtpTransceiver
import cocoapods.WebRTC.RTCSessionDescription
import cocoapods.WebRTC.RTCSignalingState
import cocoapods.WebRTC.RTCVideoTrack
import cocoapods.WebRTC.dataChannelForLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.webrtc.kmm.PeerConnectionEvent.ConnectionStateChange
import org.webrtc.kmm.PeerConnectionEvent.IceConnectionStateChange
import org.webrtc.kmm.PeerConnectionEvent.IceGatheringStateChange
import org.webrtc.kmm.PeerConnectionEvent.NegotiationNeeded
import org.webrtc.kmm.PeerConnectionEvent.NewDataChannel
import org.webrtc.kmm.PeerConnectionEvent.NewIceCandidate
import org.webrtc.kmm.PeerConnectionEvent.RemoveTrack
import org.webrtc.kmm.PeerConnectionEvent.RemovedIceCandidates
import org.webrtc.kmm.PeerConnectionEvent.SignalingStateChange
import org.webrtc.kmm.PeerConnectionEvent.StandardizedIceConnectionChange
import org.webrtc.kmm.PeerConnectionEvent.Track
import platform.darwin.NSObject

actual class PeerConnection actual constructor(
    rtcConfiguration: RtcConfiguration
) : NSObject(), RTCPeerConnectionDelegateProtocol {

    val ios: RTCPeerConnection = checkNotNull(
        WebRtc.peerConnectionFactory.peerConnectionWithConfiguration(
            configuration = rtcConfiguration.native,
            constraints = RTCMediaConstraints(),
            delegate = this
        )
    ) { "Failed to create peer connection" }

    actual val localDescription: SessionDescription? get() = ios.localDescription?.asCommon()

    actual val remoteDescription: SessionDescription? get() = ios.remoteDescription?.asCommon()

    actual val signalingState: SignalingState
        get() = rtcSignalingStateAsCommon(ios.signalingState())

    actual val iceConnectionState: IceConnectionState
        get() = rtcIceConnectionStateAsCommon(ios.iceConnectionState())

    actual val connectionState: PeerConnectionState
        get() = rtcPeerConnectionStateAsCommon(ios.connectionState())

    actual val iceGatheringState: IceGatheringState
        get() = rtcIceGatheringStateAsCommon(ios.iceGatheringState())

    private val _peerConnectionEvent =
        MutableSharedFlow<PeerConnectionEvent>(extraBufferCapacity = FLOW_BUFFER_CAPACITY)
    internal actual val peerConnectionEvent: Flow<PeerConnectionEvent> = _peerConnectionEvent.asSharedFlow()

    private val localTracks = mutableMapOf<String, MediaStreamTrack>()
    private val remoteTracks = mutableMapOf<String, MediaStreamTrack>()

    actual fun createDataChannel(
        label: String,
        id: Int,
        ordered: Boolean,
        maxRetransmitTimeMs: Int,
        maxRetransmits: Int,
        protocol: String,
        negotiated: Boolean
    ): DataChannel? {
        val config = RTCDataChannelConfiguration().also {
            it.channelId = id
            it.isOrdered = ordered
            it.maxRetransmitTimeMs = maxRetransmitTimeMs.toLong()
            it.maxRetransmits = maxRetransmits
            it.protocol = protocol
            it.isNegotiated = negotiated
        }
        return ios.dataChannelForLabel(label, config)?.let { DataChannel(it) }
    }

    actual suspend fun createOffer(options: OfferAnswerOptions): SessionDescription {
        val constraints = options.toRTCMediaConstraints()
        val sessionDescription: RTCSessionDescription = ios.awaitResult {
            offerForConstraints(constraints, it)
        }
        return sessionDescription.asCommon()
    }

    actual suspend fun createAnswer(options: OfferAnswerOptions): SessionDescription {
        val constraints = options.toRTCMediaConstraints()
        val sessionDescription: RTCSessionDescription = ios.awaitResult {
            answerForConstraints(constraints, it)
        }
        return sessionDescription.asCommon()
    }

    private fun OfferAnswerOptions.toRTCMediaConstraints(): RTCMediaConstraints {
        val mandatory = mutableMapOf<Any?, String?>().apply {
            iceRestart?.let { this += "IceRestart" to "$it" }
            offerToReceiveAudio?.let { this += "OfferToReceiveAudio" to "$it" }
            offerToReceiveVideo?.let { this += "OfferToReceiveVideo" to "$it" }
            voiceActivityDetection?.let { this += "VoiceActivityDetection" to "$it" }
        }
        return RTCMediaConstraints(mandatory, null)
    }

    actual suspend fun setLocalDescription(description: SessionDescription) {
        ios.await { setLocalDescription(description.asIos(), it) }
    }

    actual suspend fun setRemoteDescription(description: SessionDescription) {
        ios.await { setRemoteDescription(description.asIos(), it) }
    }

    actual fun setConfiguration(configuration: RtcConfiguration): Boolean {
        return ios.setConfiguration(configuration.native)
    }

    actual fun addIceCandidate(candidate: IceCandidate): Boolean {
        ios.addIceCandidate(candidate.native)
        return true
    }

    actual fun removeIceCandidates(candidates: List<IceCandidate>): Boolean {
        ios.removeIceCandidates(candidates.map { it.native })
        return true
    }

    actual fun getSenders(): List<RtpSender> = ios.senders.map {
        val iosSender = it as RTCRtpSender
        RtpSender(iosSender, localTracks[iosSender.track?.trackId])
    }

    actual fun getReceivers(): List<RtpReceiver> = ios.receivers.map {
        val iosReceiver = it as RTCRtpReceiver
        RtpReceiver(iosReceiver, remoteTracks[iosReceiver.track?.trackId])
    }

    actual fun getTransceivers(): List<RtpTransceiver> = ios.transceivers.map {
        val iosTransceiver = it as RTCRtpTransceiver
        val senderTrack = localTracks[iosTransceiver.sender.track?.trackId]
        val receiverTrack = remoteTracks[iosTransceiver.receiver.track?.trackId]
        RtpTransceiver(iosTransceiver, senderTrack, receiverTrack)
    }

    actual fun addTrack(track: MediaStreamTrack, vararg streams: MediaStream): RtpSender {
        val streamIds = streams.map { it.id }
        val iosSender = checkNotNull(ios.addTrack(track.ios, streamIds)) { "Failed to add track" }
        localTracks[track.id] = track
        return RtpSender(iosSender, track)
    }

    actual fun removeTrack(sender: RtpSender): Boolean {
        localTracks.remove(sender.track?.id)
        return ios.removeTrack(sender.native)
    }

    actual suspend fun getStats(): RtcStatsReport? {
        // TODO not implemented yet
        return null
    }

    actual fun close() {
        remoteTracks.values.forEach(MediaStreamTrack::stop)
        remoteTracks.clear()
        ios.close()
    }

    override fun peerConnection(peerConnection: RTCPeerConnection, didChangeSignalingState: RTCSignalingState) {
        val event = SignalingStateChange(rtcSignalingStateAsCommon(didChangeSignalingState))
        _peerConnectionEvent.tryEmit(event)
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun peerConnection(peerConnection: RTCPeerConnection, didAddStream: RTCMediaStream) {
        // this deprecated API should not longer be used
        // https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/onaddstream
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun peerConnection(peerConnection: RTCPeerConnection, didRemoveStream: RTCMediaStream) {
        // The removestream event has been removed from the WebRTC specification in favor of
        // the existing removetrack event on the remote MediaStream and the corresponding
        // MediaStream.onremovetrack event handler property of the remote MediaStream.
        // The RTCPeerConnection API is now track-based, so having zero tracks in the remote
        // stream is equivalent to the remote stream being removed and the old removestream event.
        // https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/onremovestream
    }

    override fun peerConnectionShouldNegotiate(peerConnection: RTCPeerConnection) {
        _peerConnectionEvent.tryEmit(NegotiationNeeded)
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun peerConnection(peerConnection: RTCPeerConnection, didChangeIceConnectionState: RTCIceConnectionState) {
        val event = IceConnectionStateChange(rtcIceConnectionStateAsCommon(didChangeIceConnectionState))
        _peerConnectionEvent.tryEmit(event)
    }

    override fun peerConnection(peerConnection: RTCPeerConnection, didChangeIceGatheringState: RTCIceGatheringState) {
        val event = IceGatheringStateChange(rtcIceGatheringStateAsCommon(didChangeIceGatheringState))
        _peerConnectionEvent.tryEmit(event)
    }

    override fun peerConnection(peerConnection: RTCPeerConnection, didGenerateIceCandidate: RTCIceCandidate) {
        val event = NewIceCandidate(IceCandidate(didGenerateIceCandidate))
        _peerConnectionEvent.tryEmit(event)
    }

    override fun peerConnection(peerConnection: RTCPeerConnection, didRemoveIceCandidates: List<*>) {
        val candidates = didRemoveIceCandidates.map { IceCandidate(it as RTCIceCandidate) }
        val event = RemovedIceCandidates(candidates)
        _peerConnectionEvent.tryEmit(event)
    }

    override fun peerConnection(peerConnection: RTCPeerConnection, didOpenDataChannel: RTCDataChannel) {
        val event = NewDataChannel(DataChannel(didOpenDataChannel))
        _peerConnectionEvent.tryEmit(event)
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeStandardizedIceConnectionState: RTCIceConnectionState
    ) {
        val event = StandardizedIceConnectionChange(
            rtcIceConnectionStateAsCommon(didChangeStandardizedIceConnectionState)
        )
        _peerConnectionEvent.tryEmit(event)
    }

    override fun peerConnection(peerConnection: RTCPeerConnection, didChangeConnectionState: RTCPeerConnectionState) {
        val event = ConnectionStateChange(
            rtcPeerConnectionStateAsCommon(didChangeConnectionState)
        )
        _peerConnectionEvent.tryEmit(event)
    }

    override fun peerConnection(peerConnection: RTCPeerConnection, didAddReceiver: RTCRtpReceiver, streams: List<*>) {
        val transceiver = ios.transceivers
            .map { it as RTCRtpTransceiver }
            .find { it.receiver.receiverId == didAddReceiver.receiverId }
            ?: return

        val iosStreams = streams.map { it as RTCMediaStream }

        val audioTracks = iosStreams
            .flatMap { it.audioTracks }
            .map { it as RTCAudioTrack }
            .map { remoteTracks.getOrPut(it.trackId) { AudioStreamTrack(it) } }

        val videoTracks = iosStreams
            .flatMap { it.videoTracks }
            .map { it as RTCVideoTrack }
            .map { remoteTracks.getOrPut(it.trackId) { VideoStreamTrack(it) } }

        val commonStreams = iosStreams.map { iosStream ->
            MediaStream(ios = iosStream, id = iosStream.streamId).also { stream ->
                audioTracks.forEach(stream::addTrack)
                videoTracks.forEach(stream::addTrack)
            }
        }

        val receiverTrack = remoteTracks[didAddReceiver.track?.trackId]
        val senderTrack = localTracks[transceiver.sender.track?.trackId]

        val trackEvent = TrackEvent(
            receiver = RtpReceiver(didAddReceiver, receiverTrack),
            streams = commonStreams,
            track = receiverTrack,
            transceiver = RtpTransceiver(transceiver, senderTrack, receiverTrack)
        )

        val event = Track(trackEvent)
        _peerConnectionEvent.tryEmit(event)
    }

    override fun peerConnection(peerConnection: RTCPeerConnection, didRemoveReceiver: RTCRtpReceiver) {
        val track = remoteTracks.remove(didRemoveReceiver.track?.trackId)
        val event = RemoveTrack(RtpReceiver(didRemoveReceiver, track))
        _peerConnectionEvent.tryEmit(event)
        track?.stop()
    }
}
