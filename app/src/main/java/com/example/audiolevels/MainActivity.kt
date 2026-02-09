package com.example.audiolevels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private data class StreamSpec(val label: String, val streamType: Int)
    private data class StreamBounds(val min: Int, val max: Int)

    private data class VolumePreset(
        val name: String,
        val music: Int,
        val ring: Int,
        val notification: Int,
        val alarm: Int,
        val system: Int,
        val voiceCall: Int
    )

    private val streamSpecs = listOf(
        StreamSpec("Music", AudioManager.STREAM_MUSIC),
        StreamSpec("Ring", AudioManager.STREAM_RING),
        StreamSpec("Notification", AudioManager.STREAM_NOTIFICATION),
        StreamSpec("Alarm", AudioManager.STREAM_ALARM),
        StreamSpec("System", AudioManager.STREAM_SYSTEM),
        StreamSpec("Voice Call", AudioManager.STREAM_VOICE_CALL)
    )

    private lateinit var audioManager: AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var musicText: TextView
    private lateinit var ringText: TextView
    private lateinit var notificationText: TextView
    private lateinit var alarmText: TextView
    private lateinit var systemText: TextView
    private lateinit var voiceCallText: TextView
    private lateinit var presetsContainer: LinearLayout
    private lateinit var noPresetsText: TextView
    private val presets = mutableListOf<VolumePreset>()
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
        presetsContainer = findViewById(R.id.presets_container)
        noPresetsText = findViewById(R.id.text_no_presets)

        findViewById<View>(R.id.row_music).setOnClickListener { openVolumePanel(AudioManager.STREAM_MUSIC) }
        findViewById<View>(R.id.row_ring).setOnClickListener { openVolumePanel(AudioManager.STREAM_RING) }
        findViewById<View>(R.id.row_notification).setOnClickListener { openVolumePanel(AudioManager.STREAM_NOTIFICATION) }
        findViewById<View>(R.id.row_alarm).setOnClickListener { openVolumePanel(AudioManager.STREAM_ALARM) }
        findViewById<View>(R.id.row_system).setOnClickListener { openVolumePanel(AudioManager.STREAM_SYSTEM) }
        findViewById<View>(R.id.row_voice_call).setOnClickListener { openVolumePanel(AudioManager.STREAM_VOICE_CALL) }

        findViewById<Button>(R.id.button_add_preset).setOnClickListener {
            val defaultName = "Preset ${presets.size + 1}"
            showPresetEditorDialog(
                title = "Save Preset",
                initialPreset = captureCurrentLevels(defaultName),
                positiveButton = "Save"
            ) { preset ->
                presets.add(preset)
                persistPresets()
                renderPresets()
                Toast.makeText(this, "Preset saved", Toast.LENGTH_SHORT).show()
            }
        }

        loadPresets()
        renderPresets()
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

    private fun openVolumePanel(streamType: Int) {
        audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }

    private fun formatVolume(streamType: Int): String {
        val bounds = getStreamBounds(streamType)
        val current = audioManager.getStreamVolume(streamType)
        val percentage = if (bounds.max == 0) 0 else (current * 100f / bounds.max)
        return "$current / ${bounds.max} (min ${bounds.min}, ${String.format(Locale.US, "%.1f", percentage)}%)"
    }

    private fun captureCurrentLevels(name: String): VolumePreset {
        return VolumePreset(
            name = name,
            music = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            ring = audioManager.getStreamVolume(AudioManager.STREAM_RING),
            notification = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
            alarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
            system = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM),
            voiceCall = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        )
    }

    private fun renderPresets() {
        presetsContainer.removeAllViews()
        noPresetsText.visibility = if (presets.isEmpty()) View.VISIBLE else View.GONE

        presets.forEachIndexed { index, preset ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.volume_row_bg)
                setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 10.dp() }
            }

            val title = TextView(this).apply {
                text = preset.name
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val summary = TextView(this).apply {
                text = labeledPresetDetails(preset)
                setPadding(0, 6.dp(), 0, 10.dp())
            }

            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val applyButton = Button(this).apply {
                text = "Apply"
                setOnClickListener {
                    showApplyConfirmation(preset)
                }
            }

            val editButton = Button(this).apply {
                text = "Edit"
                setOnClickListener {
                    showPresetEditorDialog(
                        title = "Edit Preset",
                        initialPreset = preset,
                        positiveButton = "Save"
                    ) { updatedPreset ->
                        presets[index] = updatedPreset
                        persistPresets()
                        renderPresets()
                        Toast.makeText(this@MainActivity, "Preset updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val deleteButton = Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete Preset")
                        .setMessage("Delete '${preset.name}'?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            presets.removeAt(index)
                            persistPresets()
                            renderPresets()
                            Toast.makeText(this@MainActivity, "Preset deleted", Toast.LENGTH_SHORT).show()
                        }
                        .show()
                }
            }

            val weightParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val middleWeightParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = 8.dp()
                it.marginEnd = 8.dp()
            }

            buttonRow.addView(applyButton, weightParams)
            buttonRow.addView(editButton, middleWeightParams)
            buttonRow.addView(deleteButton, weightParams)

            card.addView(title)
            card.addView(summary)
            card.addView(buttonRow)

            presetsContainer.addView(card)
        }
    }

    private fun showApplyConfirmation(preset: VolumePreset) {
        AlertDialog.Builder(this)
            .setTitle("Apply Preset")
            .setMessage("Apply '${preset.name}' to system volumes?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                applyPreset(preset)
                Toast.makeText(this, "Preset applied", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun applyPreset(preset: VolumePreset) {
        setStreamVolume(AudioManager.STREAM_MUSIC, preset.music)
        setStreamVolume(AudioManager.STREAM_RING, preset.ring)
        setStreamVolume(AudioManager.STREAM_NOTIFICATION, preset.notification)
        setStreamVolume(AudioManager.STREAM_ALARM, preset.alarm)
        setStreamVolume(AudioManager.STREAM_SYSTEM, preset.system)
        setStreamVolume(AudioManager.STREAM_VOICE_CALL, preset.voiceCall)
        refreshAllVolumes()
    }

    private fun setStreamVolume(streamType: Int, value: Int) {
        val bounds = getStreamBounds(streamType)
        val clamped = value.coerceIn(bounds.min, bounds.max)
        audioManager.setStreamVolume(streamType, clamped, 0)
    }

    private fun showPresetEditorDialog(
        title: String,
        initialPreset: VolumePreset,
        positiveButton: String,
        onSave: (VolumePreset) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 16.dp(), 24.dp(), 0)
        }

        val nameInput = EditText(this).apply {
            hint = "For example: Night mode"
            setText(initialPreset.name)
        }
        val nameLabel = TextView(this).apply {
            text = "Preset Name"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 4.dp())
        }
        container.addView(nameLabel)
        container.addView(nameInput)

        val streamInputs = linkedMapOf<Int, EditText>()

        streamSpecs.forEach { spec ->
            val bounds = getStreamBounds(spec.streamType)
            val label = TextView(this).apply {
                text = "${spec.label} (${bounds.min}-${bounds.max})"
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 10.dp(), 0, 4.dp())
            }
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "Enter volume level"
                setText(initialPreset.getStreamValue(spec.streamType).toString())
            }
            container.addView(label)
            container.addView(input)
            streamInputs[spec.streamType] = input
        }

        val scrollView = ScrollView(this).apply {
            addView(container)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positiveButton, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Preset name is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val parsedValues = mutableMapOf<Int, Int>()
                streamSpecs.forEach { spec ->
                    val raw = streamInputs[spec.streamType]?.text?.toString()?.trim().orEmpty()
                    val parsed = raw.toIntOrNull()
                    if (parsed == null) {
                        Toast.makeText(this, "${spec.label} must be a number", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val bounds = getStreamBounds(spec.streamType)
                    if (parsed !in bounds.min..bounds.max) {
                        Toast.makeText(
                            this,
                            "${spec.label} must be between ${bounds.min} and ${bounds.max}",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    parsedValues[spec.streamType] = parsed
                }

                val newPreset = VolumePreset(
                    name = name,
                    music = parsedValues[AudioManager.STREAM_MUSIC] ?: 0,
                    ring = parsedValues[AudioManager.STREAM_RING] ?: 0,
                    notification = parsedValues[AudioManager.STREAM_NOTIFICATION] ?: 0,
                    alarm = parsedValues[AudioManager.STREAM_ALARM] ?: 0,
                    system = parsedValues[AudioManager.STREAM_SYSTEM] ?: 0,
                    voiceCall = parsedValues[AudioManager.STREAM_VOICE_CALL] ?: 0
                )
                onSave(newPreset)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun persistPresets() {
        val jsonArray = JSONArray()
        presets.forEach { preset ->
            val obj = JSONObject().apply {
                put("name", preset.name)
                put("music", preset.music)
                put("ring", preset.ring)
                put("notification", preset.notification)
                put("alarm", preset.alarm)
                put("system", preset.system)
                put("voiceCall", preset.voiceCall)
            }
            jsonArray.put(obj)
        }

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESETS_JSON, jsonArray.toString())
            .apply()
    }

    private fun loadPresets() {
        presets.clear()

        val raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRESETS_JSON, null)
            ?: return

        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val preset = VolumePreset(
                    name = obj.optString("name", "Preset ${i + 1}"),
                    music = obj.optInt("music", 0),
                    ring = obj.optInt("ring", 0),
                    notification = obj.optInt("notification", 0),
                    alarm = obj.optInt("alarm", 0),
                    system = obj.optInt("system", 0),
                    voiceCall = obj.optInt("voiceCall", 0)
                )
                presets.add(sanitizePreset(preset))
            }
        } catch (_: JSONException) {
            Toast.makeText(this, "Could not read saved presets", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sanitizePreset(preset: VolumePreset): VolumePreset {
        val musicBounds = getStreamBounds(AudioManager.STREAM_MUSIC)
        val ringBounds = getStreamBounds(AudioManager.STREAM_RING)
        val notificationBounds = getStreamBounds(AudioManager.STREAM_NOTIFICATION)
        val alarmBounds = getStreamBounds(AudioManager.STREAM_ALARM)
        val systemBounds = getStreamBounds(AudioManager.STREAM_SYSTEM)
        val voiceCallBounds = getStreamBounds(AudioManager.STREAM_VOICE_CALL)

        return VolumePreset(
            name = preset.name,
            music = preset.music.coerceIn(musicBounds.min, musicBounds.max),
            ring = preset.ring.coerceIn(ringBounds.min, ringBounds.max),
            notification = preset.notification.coerceIn(notificationBounds.min, notificationBounds.max),
            alarm = preset.alarm.coerceIn(alarmBounds.min, alarmBounds.max),
            system = preset.system.coerceIn(systemBounds.min, systemBounds.max),
            voiceCall = preset.voiceCall.coerceIn(voiceCallBounds.min, voiceCallBounds.max)
        )
    }

    private fun labeledPresetDetails(preset: VolumePreset): String {
        return "Music: ${preset.music}\n" +
            "Ring: ${preset.ring}\n" +
            "Notification: ${preset.notification}\n" +
            "Alarm: ${preset.alarm}\n" +
            "System: ${preset.system}\n" +
            "Voice Call: ${preset.voiceCall}"
    }

    private fun VolumePreset.getStreamValue(streamType: Int): Int {
        return when (streamType) {
            AudioManager.STREAM_MUSIC -> music
            AudioManager.STREAM_RING -> ring
            AudioManager.STREAM_NOTIFICATION -> notification
            AudioManager.STREAM_ALARM -> alarm
            AudioManager.STREAM_SYSTEM -> system
            AudioManager.STREAM_VOICE_CALL -> voiceCall
            else -> 0
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun getStreamBounds(streamType: Int): StreamBounds {
        val max = audioManager.getStreamMaxVolume(streamType)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(streamType)
        } else {
            0
        }
        return StreamBounds(min.coerceAtMost(max), max)
    }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val PREFS_NAME = "audio_levels_prefs"
        private const val KEY_PRESETS_JSON = "presets_json"
    }
}
