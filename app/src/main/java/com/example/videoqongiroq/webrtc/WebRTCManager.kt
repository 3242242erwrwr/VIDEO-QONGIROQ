package com.example.videoqongiroq.webrtc

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.*
import org.webrtc.PeerConnection.*

class WebRTCManager(private val context: Context) {

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

    private var localSurfaceView: SurfaceViewRenderer? = null
    private var remoteSurfaceView: SurfaceViewRenderer? = null

    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

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

    fun setupAudioForCall() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            audioManager.isMicrophoneMute = false
            Log.d(TAG, "Audio configured: Speakerphone ON, Mode IN_COMMUNICATION")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up AudioManager", e)
        }
    }

    fun resetAudio() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting AudioManager", e)
        }
    }

    fun initLocalSurfaceView(renderer: SurfaceViewRenderer) {
        localSurfaceView = renderer
        try {
            localSurfaceView?.init(eglBase.eglBaseContext, null)
            localSurfaceView?.setEnableHardwareScaler(true)
            localSurfaceView?.setMirror(true)
            localSurfaceView?.setZOrderMediaOverlay(true)

            localVideoTrack?.addSink(localSurfaceView)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing local SurfaceView", e)
        }
    }

    fun initRemoteSurfaceView(renderer: SurfaceViewRenderer) {
        remoteSurfaceView = renderer
        try {
            remoteSurfaceView?.init(eglBase.eglBaseContext, null)
            remoteSurfaceView?.setEnableHardwareScaler(true)
            remoteSurfaceView?.setMirror(false)

            remoteVideoTrack?.let { track ->
                mainHandler.post {
                    try {
                        track.addSink(remoteSurfaceView)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding sink to remoteSurfaceView", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing remote SurfaceView", e)
        }
    }

    fun startLocalVideo() {
        if (peerConnectionFactory == null) return
        if (localAudioTrack != null && localVideoTrack != null) return

        setupAudioForCall()

        // Create Audio track
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
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

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                isFrontCamera = true
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

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
        if (peerConnection != null) return

        startLocalVideo()

        // STUN and TURN Servers for NAT Traversal (Cellular 4G/5G / Home Wi-Fi)
        val iceServers = listOf(
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
            IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
            IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

        val rtcConfig = RTCConfiguration(iceServers).apply {
            sdpSemantics = SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: SignalingState?) {
                Log.d(TAG, "onSignalingChange: $state")
            }

            override fun onIceConnectionChange(state: IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: IceGatheringState?) {
                Log.d(TAG, "onIceGatheringChange: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Local IceCandidate generated: ${it.sdpMid}")
                    onIceCandidateGenerated?.invoke(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: ${stream?.id}")
                stream?.let {
                    onRemoteStreamAdded?.invoke(it)
                    if (it.videoTracks.isNotEmpty()) {
                        attachRemoteVideoTrack(it.videoTracks[0])
                    }
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                receiver?.track()?.let { track ->
                    if (track is VideoTrack) {
                        Log.d(TAG, "onAddTrack VideoTrack received")
                        attachRemoteVideoTrack(track)
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    Log.d(TAG, "onTrack VideoTrack received")
                    attachRemoteVideoTrack(track)
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

    private fun attachRemoteVideoTrack(track: VideoTrack) {
        remoteVideoTrack = track
        onRemoteVideoTrackReceived?.invoke(track)
        mainHandler.post {
            remoteSurfaceView?.let { view ->
                try {
                    track.addSink(view)
                    Log.d(TAG, "Remote video track successfully attached to view!")
                } catch (e: Exception) {
                    Log.e(TAG, "Error attaching remote track to view", e)
                }
            }
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

    fun setRemoteDescription(sdp: SessionDescription, onSuccess: (() -> Unit)? = null) {
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                Log.d(TAG, "setRemoteDescription SUCCESS")
                isRemoteDescriptionSet = true
                synchronized(pendingIceCandidates) {
                    for (candidate in pendingIceCandidates) {
                        peerConnection?.addIceCandidate(candidate)
                    }
                    pendingIceCandidates.clear()
                }
                onSuccess?.invoke()
            }

            override fun onSetFailure(reason: String?) {
                Log.e(TAG, "setRemoteDescription FAILURE: $reason")
            }
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (isRemoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
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
        resetAudio()
        isRemoteDescriptionSet = false
        synchronized(pendingIceCandidates) {
            pendingIceCandidates.clear()
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

        localSurfaceView?.release()
        remoteSurfaceView?.release()
        remoteVideoTrack = null
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
