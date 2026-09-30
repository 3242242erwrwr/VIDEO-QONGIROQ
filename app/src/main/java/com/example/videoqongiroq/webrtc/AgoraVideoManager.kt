package com.example.videoqongiroq.webrtc

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import android.widget.FrameLayout
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas

class AgoraVideoManager(private val context: Context) {

    private var rtcEngine: RtcEngine? = null
    
    // Default free testing Agora App ID (No token required for testing channels)
    var appId: String = "64c8c74384d142d294863bc0d98413a1"
        private set

    var remoteUid: Int? = null
        private set

    var onRemoteUserJoined: ((Int) -> Unit)? = null
    var onRemoteUserOffline: ((Int) -> Unit)? = null

    companion object {
        private const val TAG = "AgoraVideoManager"
    }

    init {
        initEngine()
    }

    fun setAppId(newAppId: String) {
        if (newAppId.isNotBlank() && newAppId != appId) {
            appId = newAppId.trim()
            initEngine()
        }
    }

    private fun initEngine() {
        try {
            RtcEngine.destroy()
            val config = RtcEngineConfig().apply {
                mContext = context
                mAppId = appId
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.d(TAG, "Agora remote user joined: $uid")
                        remoteUid = uid
                        onRemoteUserJoined?.invoke(uid)
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        Log.d(TAG, "Agora remote user offline: $uid")
                        remoteUid = null
                        onRemoteUserOffline?.invoke(uid)
                    }

                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        Log.d(TAG, "Agora join channel success: $channel, uid: $uid")
                    }

                    override fun onError(err: Int) {
                        Log.e(TAG, "Agora RTC Error: $err")
                    }
                }
            }

            rtcEngine = RtcEngine.create(config)
            rtcEngine?.enableVideo()
            rtcEngine?.enableAudio()
            rtcEngine?.startPreview()
            rtcEngine?.setEnableSpeakerphone(true)

            Log.d(TAG, "Agora Engine initialized successfully with App ID: $appId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Agora RTC Engine", e)
        }
    }

    fun setupLocalVideo(container: FrameLayout) {
        try {
            if (container.childCount == 0) {
                val surfaceView = SurfaceView(context)
                surfaceView.setZOrderMediaOverlay(true)
                container.addView(surfaceView)
                rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
            }
            rtcEngine?.startPreview()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up local video", e)
        }
    }

    fun setupRemoteVideo(container: FrameLayout, uid: Int) {
        try {
            if (container.childCount == 0) {
                val surfaceView = SurfaceView(context)
                container.addView(surfaceView)
                rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up remote video", e)
        }
    }

    fun joinChannel(channelName: String) {
        try {
            rtcEngine?.enableVideo()
            rtcEngine?.enableAudio()
            rtcEngine?.startPreview()
            rtcEngine?.setEnableSpeakerphone(true)

            val options = ChannelMediaOptions().apply {
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            }

            // Join channel without token (testing mode)
            val result = rtcEngine?.joinChannel(null, channelName, 0, options)
            Log.d(TAG, "Joining Agora channel: $channelName result: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error joining channel $channelName", e)
        }
    }

    fun leaveChannel() {
        try {
            rtcEngine?.leaveChannel()
            rtcEngine?.stopPreview()
            remoteUid = null
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving channel", e)
        }
    }

    fun toggleMute(isMuted: Boolean) {
        rtcEngine?.muteLocalAudioStream(isMuted)
    }

    fun toggleCamera(isCameraOff: Boolean) {
        rtcEngine?.muteLocalVideoStream(isCameraOff)
    }

    fun switchCamera() {
        rtcEngine?.switchCamera()
    }

    fun destroy() {
        try {
            leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying Agora Engine", e)
        }
    }
}
