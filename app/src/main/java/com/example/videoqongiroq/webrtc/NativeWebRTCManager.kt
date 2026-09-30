package com.example.videoqongiroq.webrtc

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.*

class NativeWebRTCManager(private val context: Context) {

    private val eglBase: EglBase = EglBase.create()
    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var remoteVideoTrack: VideoTrack? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var isLocalInit = false
    private var isRemoteInit = false

    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var isRemoteSdpSet = false

    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null

    companion object {
        private const val TAG = "NativeWebRTCManager"
        private const val STREAM_ID = "v_stream_1"
        private const val AUDIO_TRACK_ID = "a_track_1"
        private const val VIDEO_TRACK_ID = "v_track_1"
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

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun setupAudio() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            audioManager.isMicrophoneMute = false
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)
            Log.d(TAG, "Audio configured: Speakerphone ON, Communication Mode")
        } catch (e: Exception) {
            Log.e(TAG, "Audio config error", e)
        }
    }

    fun resetAudio() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Audio reset error", e)
        }
    }

    fun initLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderer = renderer
        if (!isLocalInit) {
            try {
                renderer.init(eglBase.eglBaseContext, null)
                renderer.setEnableHardwareScaler(true)
                renderer.setMirror(true)
                renderer.setZOrderMediaOverlay(true)
                isLocalInit = true
            } catch (e: Exception) {
                Log.e(TAG, "Error init local renderer", e)
            }
        }
        bindLocalTrack()
    }

    fun initRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderer = renderer
        if (!isRemoteInit) {
            try {
                renderer.init(eglBase.eglBaseContext, null)
                renderer.setEnableHardwareScaler(true)
                renderer.setMirror(false)
                isRemoteInit = true
            } catch (e: Exception) {
                Log.e(TAG, "Error init remote renderer", e)
            }
        }
        bindRemoteTrack()
    }

    private fun bindLocalTrack() {
        val track = localVideoTrack ?: return
        val view = localRenderer ?: return
        mainHandler.post {
            try {
                track.removeSink(view)
                track.addSink(view)
                Log.d(TAG, "Local video track bound to view")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding local track", e)
            }
        }
    }

    private fun bindRemoteTrack() {
        val track = remoteVideoTrack ?: return
        val view = remoteRenderer ?: return
        mainHandler.post {
            try {
                track.removeSink(view)
                track.addSink(view)
                Log.d(TAG, "Remote video track bound to view successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding remote track", e)
            }
        }
    }

    fun startLocalVideo() {
        if (peerConnectionFactory == null) return
        if (localAudioTrack != null && localVideoTrack != null) return

        setupAudio()

        // Audio Constraints
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(true)

        // Camera Video Capturer
        val capturer = createCameraCapturer()
        if (capturer != null) {
            videoCapturer = capturer
            val textureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast)
            capturer.initialize(textureHelper, context, videoSource?.capturerObserver)

            try {
                capturer.startCapture(1280, 720, 30)
            } catch (e: Exception) {
                try {
                    capturer.startCapture(640, 480, 30)
                } catch (e2: Exception) {
                    capturer.startCapture(320, 240, 30)
                }
            }

            localVideoTrack = peerConnectionFactory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            localVideoTrack?.setEnabled(true)

            bindLocalTrack()
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

    fun createPeerConnection() {
        if (peerConnection != null) return

        startLocalVideo()

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
                    Log.d(TAG, "Generated ICE Candidate: ${it.sdpMid}")
                    onIceCandidateGenerated?.invoke(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: ${stream?.id}")
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    remoteVideoTrack = track
                    track.setEnabled(true)
                    bindRemoteTrack()
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
                    bindRemoteTrack()
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                (transceiver?.receiver?.track() as? VideoTrack)?.let { track ->
                    Log.d(TAG, "onTrack VideoTrack received")
                    remoteVideoTrack = track
                    track.setEnabled(true)
                    bindRemoteTrack()
                }
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        // Add local tracks with stream ID
        val streamIds = listOf(STREAM_ID)
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, streamIds)
        }
        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, streamIds)
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
        videoCapturer?.switchCamera(null)
    }

    fun close() {
        resetAudio()
        isRemoteSdpSet = false
        isLocalInit = false
        isRemoteInit = false

        synchronized(pendingCandidates) {
            pendingCandidates.clear()
        }

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

        localRenderer?.release()
        remoteRenderer?.release()
        localRenderer = null
        remoteRenderer = null
        remoteVideoTrack = null
    }

    open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(reason: String?) {
            Log.e("NativeWebRTCManager", "SdpObserver onCreateFailure: $reason")
        }
        override fun onSetFailure(reason: String?) {
            Log.e("NativeWebRTCManager", "SdpObserver onSetFailure: $reason")
        }
    }
}
