package com.example.videoqongiroq.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.PeerConnection.*

class WebRTCManager(private val context: Context) {

    private val eglBase: EglBase = EglBase.create()
    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var localSurfaceView: SurfaceViewRenderer? = null
    private var remoteSurfaceView: SurfaceViewRenderer? = null

    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onRemoteStreamAdded: ((MediaStream) -> Unit)? = null
    var onRemoteVideoTrackReceived: ((VideoTrack) -> Unit)? = null

    private var isFrontCamera = true

    companion object {
        private const val TAG = "WebRTCManager"
        private const val LOCAL_TRACK_ID = "local_track_v1"
        private const val LOCAL_AUDIO_TRACK_ID = "local_audio_track_v1"
        private const val LOCAL_STREAM_ID = "local_stream_v1"
    }

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun initLocalSurfaceView(renderer: SurfaceViewRenderer) {
        localSurfaceView = renderer
        localSurfaceView?.init(eglBase.eglBaseContext, null)
        localSurfaceView?.setEnableHardwareScaler(true)
        localSurfaceView?.setMirror(true)
    }

    fun initRemoteSurfaceView(renderer: SurfaceViewRenderer) {
        remoteSurfaceView = renderer
        remoteSurfaceView?.init(eglBase.eglBaseContext, null)
        remoteSurfaceView?.setEnableHardwareScaler(true)
        remoteSurfaceView?.setMirror(false)
    }

    fun startLocalVideo() {
        if (peerConnectionFactory == null) return

        // Create Audio track
        audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(true)

        // Create Video track
        val capturer = createCameraCapturer()
        if (capturer != null) {
            videoCapturer = capturer
            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            capturer.startCapture(1280, 720, 30)

            localVideoTrack = peerConnectionFactory?.createVideoTrack(LOCAL_TRACK_ID, videoSource)
            localVideoTrack?.setEnabled(true)

            localSurfaceView?.let { view ->
                localVideoTrack?.addSink(view)
            }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Try front camera first
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                isFrontCamera = true
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        // Fallback to back camera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                isFrontCamera = false
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        return null
    }

    fun createPeerConnection() {
        val iceServers = listOf(
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )

        val rtcConfig = RTCConfiguration(iceServers).apply {
            sdpSemantics = SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: SignalingState?) {}
            override fun onIceConnectionChange(state: IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    onIceCandidateGenerated?.invoke(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: ${stream?.id}")
                stream?.let {
                    onRemoteStreamAdded?.invoke(it)
                    if (it.videoTracks.isNotEmpty()) {
                        val track = it.videoTracks[0]
                        remoteSurfaceView?.let { view ->
                            track.addSink(view)
                        }
                    }
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                receiver?.track()?.let { track ->
                    if (track is VideoTrack) {
                        onRemoteVideoTrackReceived?.invoke(track)
                        remoteSurfaceView?.let { view ->
                            track.addSink(view)
                        }
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    onRemoteVideoTrackReceived?.invoke(track)
                    remoteSurfaceView?.let { view ->
                        track.addSink(view)
                    }
                }
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        // Add local tracks
        val mediaStream = peerConnectionFactory?.createLocalMediaStream(LOCAL_STREAM_ID)
        localAudioTrack?.let {
            mediaStream?.addTrack(it)
            peerConnection?.addTrack(it, listOf(LOCAL_STREAM_ID))
        }
        localVideoTrack?.let {
            mediaStream?.addTrack(it)
            peerConnection?.addTrack(it, listOf(LOCAL_STREAM_ID))
        }
    }

    fun createOffer(onSdpCreated: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SdpObserverAdapter() {}, sdp)
                    onSdpCreated(sdp)
                }
            }
        }, constraints)
    }

    fun createAnswer(onSdpCreated: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SdpObserverAdapter() {}, sdp)
                    onSdpCreated(sdp)
                }
            }
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpObserverAdapter(), sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun toggleMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun toggleCamera(isCameraOff: Boolean) {
        localVideoTrack?.setEnabled(!isCameraOff)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontCamera = isFront
                localSurfaceView?.setMirror(isFront)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.e(TAG, "Camera switch error: $errorDescription")
            }
        })
    }

    fun close() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capturer", e)
        }
        videoCapturer?.dispose()
        videoCapturer = null

        videoSource?.dispose()
        videoSource = null

        audioSource?.dispose()
        audioSource = null

        peerConnection?.close()
        peerConnection = null

        localSurfaceView?.release()
        remoteSurfaceView?.release()
    }

    open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(reason: String?) {
            Log.e("WebRTCManager", "SdpObserver onCreateFailure: $reason")
        }
        override fun onSetFailure(reason: String?) {
            Log.e("WebRTCManager", "SdpObserver onSetFailure: $reason")
        }
    }
}
