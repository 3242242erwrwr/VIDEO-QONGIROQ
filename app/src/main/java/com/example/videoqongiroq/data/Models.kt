package com.example.videoqongiroq.data

enum class UserStatus(val labelUz: String, val colorHex: Long) {
    AVAILABLE("Onlayn", 0xFF00E676),
    BUSY("Band", 0xFFFF9100),
    IN_CALL("Qo'ng'iroqda", 0xFF00E5FF),
    OFFLINE("Oflayn", 0xFF757575)
}

data class User(
    val phone: String,
    val name: String,
    val status: UserStatus = UserStatus.AVAILABLE,
    val isLocalUser: Boolean = false
) {
    val formattedPhone: String
        get() {
            val clean = phone.filter { it.isDigit() }
            return if (clean.length == 12 && clean.startsWith("998")) {
                "+998 (${clean.substring(3, 5)}) ${clean.substring(5, 8)}-${clean.substring(8, 10)}-${clean.substring(10, 12)}"
            } else if (clean.startsWith("998")) {
                "+$clean"
            } else if (clean.length == 9) {
                "+998 (${clean.substring(0, 2)}) ${clean.substring(2, 5)}-${clean.substring(5, 7)}-${clean.substring(7, 9)}"
            } else {
                "+$phone"
            }
        }
}

sealed class CallState {
    object Idle : CallState()

    data class OutgoingCall(
        val targetUser: User,
        val isVideo: Boolean = true,
        val roomId: String = ""
    ) : CallState()

    data class IncomingCall(
        val callerUser: User,
        val isVideo: Boolean = true,
        val roomId: String = ""
    ) : CallState()

    data class InCall(
        val peerUser: User,
        val isVideo: Boolean = true,
        val isMuted: Boolean = false,
        val isCameraOff: Boolean = false,
        val isFrontCamera: Boolean = true,
        val durationSeconds: Int = 0,
        val roomId: String = "",
        val isCaller: Boolean = false
    ) : CallState()

    data class CallEnded(
        val reason: String
    ) : CallState()
}
