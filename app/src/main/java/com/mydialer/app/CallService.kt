package com.mydialer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow

object CallManager {
    val call = MutableStateFlow<Call?>(null)
    val state = MutableStateFlow(Call.STATE_DISCONNECTED)
    val muted = MutableStateFlow(false)
    val speaker = MutableStateFlow(false)
    var service: InCallService? = null

    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, newState: Int) {
            state.value = newState
        }
    }

    fun add(c: Call) {
        call.value?.unregisterCallback(cb)
        call.value = c
        state.value = c.state
        c.registerCallback(cb)
    }

    fun remove(c: Call) {
        c.unregisterCallback(cb)
        if (call.value == c) {
            call.value = null
            state.value = Call.STATE_DISCONNECTED
        }
    }

    fun answer() { call.value?.answer(VideoProfile.STATE_AUDIO_ONLY) }

    fun hangup() {
        val c = call.value ?: return
        if (c.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }

    fun toggleMute() { service?.setMuted(!muted.value) }

    fun toggleSpeaker() {
        service?.setAudioRoute(
            if (speaker.value) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        )
    }
}

class CallService : InCallService() {
    private var ringtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        stopRing()
        CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state != Call.STATE_RINGING) {
                    stopRing()
                    getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
                }
            }
        })
        if (call.state == Call.STATE_RINGING) {
            startRing()
            showIncomingNotification(call.details.handle?.schemeSpecificPart ?: "Unknown")
        }
        try { startActivity(uiIntent()) } catch (_: Exception) { }
    }

    override fun onCallRemoved(call: Call) {
        stopRing()
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        CallManager.remove(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.muted.value = audioState.isMuted
        CallManager.speaker.value = audioState.route == CallAudioState.ROUTE_SPEAKER
    }

    private fun uiIntent() = Intent(this, InCallActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun showIncomingNotification(number: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) }
        )
        val pi = PendingIntent.getActivity(
            this, 0, uiIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming call")
            .setContentText(number)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    private fun startRing() {
        val am = getSystemService(AudioManager::class.java)
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        ringtone = RingtoneManager.getRingtone(
            this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
    }

    private fun stopRing() {
        ringtone?.stop()
        ringtone = null
    }

    companion object {
        const val CHANNEL = "calls"
        const val NOTIF_ID = 1
    }
}
