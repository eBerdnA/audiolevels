package com.example.audiolevels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var musicText: TextView
    private lateinit var ringText: TextView
    private lateinit var notificationText: TextView
    private lateinit var alarmText: TextView
    private lateinit var systemText: TextView
    private lateinit var voiceCallText: TextView
    private var receiverRegistered = false

    private val periodicRefresh = object : Runnable {
        override fun run() {
            refreshAllVolumes()
            mainHandler.postDelayed(this, 500)
        }
    }

    private val volumeChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                refreshAllVolumes()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        applySystemBarsInsets(findViewById(R.id.root_scroll))

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        musicText = findViewById(R.id.value_music)
        ringText = findViewById(R.id.value_ring)
        notificationText = findViewById(R.id.value_notification)
        alarmText = findViewById(R.id.value_alarm)
        systemText = findViewById(R.id.value_system)
        voiceCallText = findViewById(R.id.value_voice_call)

        refreshAllVolumes()
    }

    private fun applySystemBarsInsets(root: View) {
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            volumeChangedReceiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        mainHandler.post(periodicRefresh)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(periodicRefresh)
        if (receiverRegistered) {
            unregisterReceiver(volumeChangedReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun refreshAllVolumes() {
        musicText.text = formatVolume(AudioManager.STREAM_MUSIC)
        ringText.text = formatVolume(AudioManager.STREAM_RING)
        notificationText.text = formatVolume(AudioManager.STREAM_NOTIFICATION)
        alarmText.text = formatVolume(AudioManager.STREAM_ALARM)
        systemText.text = formatVolume(AudioManager.STREAM_SYSTEM)
        voiceCallText.text = formatVolume(AudioManager.STREAM_VOICE_CALL)
    }

    private fun formatVolume(streamType: Int): String {
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        val percentage = if (max == 0) 0 else (current * 100f / max)
        return "$current / $max (${String.format(Locale.US, "%.1f", percentage)}%)"
    }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
