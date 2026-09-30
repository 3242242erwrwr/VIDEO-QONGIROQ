package com.example.videoqongiroq.webrtc

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.*

class NexusCallEngine(private val context: Context) {

    private val eglBase: EglBase = EglBase.create()
    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var capturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var remoteVideoTrack: VideoTrack? = null

    private var localView: SurfaceViewRenderer? = null
    private var remoteView: SurfaceViewRenderer? = null

    private var isLocalInit = false
    private var isRemoteInit = false

    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var isRemoteSdpSet = false

    var onIceCandidateReady: ((IceCandidate) -> Unit)? = null

    companion object {
        private const val TAG = "NexusCallEngine"
        private const val STREAM_ID = "nexus_stream_v1"
    }

    init {
        initFactory()
    }

    private fun initFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun configureAudio() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            audioManager.isMicrophoneMute = false
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)
            Log.d(TAG, "Audio configured: Speakerphone ON")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring audio", e)
        }
    }

    fun resetAudio() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audio", e)
        }
    }

    fun bindLocalView(renderer: SurfaceViewRenderer) {
        localView = renderer
        if (!isLocalInit) {
            try {
                renderer.init(eglBase.eglBaseContext, null)
                renderer.setEnableHardwareScaler(true)
                renderer.setMirror(true)
                renderer.setZOrderMediaOverlay(true)
                isLocalInit = true
            } catch (e: Exception) {
                Log.e(TAG, "Error init local view", e)
            }
        }
        attachLocalTrackToView()
    }

    fun bindRemoteView(renderer: SurfaceViewRenderer) {
        remoteView = renderer
        if (!isRemoteInit) {
            try {
                renderer.init(eglBase.eglBaseContext, null)
                renderer.setEnableHardwareScaler(true)
                renderer.setMirror(false)
                isRemoteInit = true
            } catch (e: Exception) {
                Log.e(TAG, "Error init remote view", e)
            }
        }
        attachRemoteTrackToView()
    }

    private fun attachLocalTrackToView() {
        val track = localVideoTrack ?: return
        val view = localView ?: return
        mainHandler.post {
            try {
                track.removeSink(view)
                track.addSink(view)
                Log.d(TAG, "Local video track attached to view")
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching local track", e)
            }
        }
    }

    private fun attachRemoteTrackToView() {
        val track = remoteVideoTrack ?: return
        val view = remoteView ?: return
        mainHandler.post {
            try {
                track.removeSink(view)
                track.addSink(view)
                Log.d(TAG, "Remote video track attached to view successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching remote track", e)
            }
        }
    }

    fun startLocalVideoAndAudio() {
        if (factory == null) return
        if (localAudioTrack != null && localVideoTrack != null) return

        configureAudio()

        // Audio Track
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        audioSource = factory?.createAudioSource(audioConstraints)
        localAudioTrack = factory?.createAudioTrack("nexus_a_track", audioSource)
        localAudioTrack?.setEnabled(true)

        // Video Capturer
        val cameraCapturer = createCameraCapturer()
        if (cameraCapturer != null) {
            capturer = cameraCapturer
            val textureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoSource = factory?.createVideoSource(cameraCapturer.isScreencast)
            cameraCapturer.initialize(textureHelper, context, videoSource?.capturerObserver)

            try {
                cameraCapturer.startCapture(1280, 720, 30)
            } catch (e1: Exception) {
                try {
                    cameraCapturer.startCapture(640, 480, 30)
                } catch (e2: Exception) {
                    cameraCapturer.startCapture(320, 240, 30)
                }
            }

            localVideoTrack = factory?.createVideoTrack("nexus_v_track", videoSource)
            localVideoTrack?.setEnabled(true)

            attachLocalTrackToView()
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                val cap = enumerator.createCapturer(name, null)
                if (cap != null) return cap
            }
        }
        for (name in deviceNames) {
            if (enumerator.isBackFacing(name)) {
                val cap = enumerator.createCapturer(name, null)
                if (cap != null) return cap
            }
        }
        return null
    }

    fun initPeerConnection() {
        if (peerConnection != null) return

        startLocalVideoAndAudio()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            iceCandidatePoolSize = 10
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "ICE Candidate generated: ${it.sdpMid}")
                    onIceCandidateReady?.invoke(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: ${stream?.id}")
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    remoteVideoTrack = track
                    track.setEnabled(true)
                    attachRemoteTrackToView()
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                (receiver?.track() as? VideoTrack)?.let { track ->
                    Log.d(TAG, "onAddTrack VideoTrack received")
                    remoteVideoTrack = track
                    track.setEnabled(true)
                    attachRemoteTrackToView()
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                (transceiver?.receiver?.track() as? VideoTrack)?.let { track ->
                    Log.d(TAG, "onTrack VideoTrack received")
                    remoteVideoTrack = track
                    track.setEnabled(true)
                    attachRemoteTrackToView()
                }
            }
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, observer)

        val streamIds = listOf(STREAM_ID)
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, streamIds)
        }
        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, streamIds)
        }
    }

    fun createOffer(onSdpCreated: (SessionDescription) -> Unit) {
        initPeerConnection()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SdpObserverAdapter() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "createOffer setLocalDescription SUCCESS")
                            onSdpCreated(sdp)
                        }
                    }, sdp)
                }
            }
        }, constraints)
    }

    fun createAnswer(onSdpCreated: (SessionDescription) -> Unit) {
        initPeerConnection()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SdpObserverAdapter() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "createAnswer setLocalDescription SUCCESS")
                            onSdpCreated(sdp)
                        }
                    }, sdp)
                }
            }
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription, onSuccess: (() -> Unit)? = null) {
        initPeerConnection()
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                Log.d(TAG, "setRemoteDescription SUCCESS")
                isRemoteSdpSet = true
                synchronized(pendingCandidates) {
                    for (candidate in pendingCandidates) {
                        peerConnection?.addIceCandidate(candidate)
                    }
                    pendingCandidates.clear()
                }
                onSuccess?.invoke()
            }

            override fun onSetFailure(reason: String?) {
                Log.e(TAG, "setRemoteDescription FAILURE: $reason")
            }
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (isRemoteSdpSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            synchronized(pendingCandidates) {
                pendingCandidates.add(candidate)
            }
        }
    }

    fun toggleMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun toggleCamera(isCameraOff: Boolean) {
        localVideoTrack?.setEnabled(!isCameraOff)
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
    }

    fun stopCall() {
        resetAudio()
        isRemoteSdpSet = false
        isLocalInit = false
        isRemoteInit = false

        synchronized(pendingCandidates) {
            pendingCandidates.clear()
        }

        try {
            capturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capturer", e)
        }
        capturer?.dispose()
        capturer = null

        videoSource?.dispose()
        videoSource = null

        audioSource?.dispose()
        audioSource = null

        peerConnection?.close()
        peerConnection = null

        localView?.release()
        remoteView?.release()
        localView = null
        remoteView = null
        remoteVideoTrack = null
    }

    open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(reason: String?) {
            Log.e(TAG, "SdpObserver onCreateFailure: $reason")
        }
        override fun onSetFailure(reason: String?) {
            Log.e(TAG, "SdpObserver onSetFailure: $reason")
        }
    }
}
