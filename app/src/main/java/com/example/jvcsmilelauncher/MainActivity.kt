package com.example.jvcsmilelauncher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.lifecycleScope
import com.example.jvcsmilelauncher.theme.ThemeEngine
import com.example.jvcsmilelauncher.util.ShizukuExecutor
import com.example.jvcsmilelauncher.util.ShizukuPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var rootLayout: ViewGroup
    private lateinit var ivJoker: ImageView
    private lateinit var overlayGradient: View
    private lateinit var clockText: TextView
    private lateinit var phraseText: TextView
    private lateinit var iconGrid: GridLayout
    private lateinit var dock: View
    private lateinit var controlsPanel: LinearLayout
    private lateinit var btnModeNormal: Button
    private lateinit var btnModeGaming: Button
    private lateinit var btnTogglePanel: Button

    // Controls
    private lateinit var shizukuStatusText: TextView
    private lateinit var shizukuActionBtn: Button
    private lateinit var switchDns: Switch
    private lateinit var btn60: Button
    private lateinit var btn90: Button
    private lateinit var btn120: Button
    private lateinit var btn144: Button
    private lateinit var inputWidth: EditText
    private lateinit var inputHeight: EditText
    private lateinit var inputDpi: EditText
    private lateinit var switchDisableAnimations: Switch
    private lateinit var inputWakePackage: EditText
    private lateinit var inputWakeActivity: EditText
    private lateinit var btnEnter: Button
    private lateinit var progressBar: ProgressBar

    private var currentHz = 60
    private var bgAnimator: ValueAnimator? = null
    private var currentMode: ThemeEngine.Mode = ThemeEngine.Mode.NORMAL
    private lateinit var prefs: SharedPreferences

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            clockText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 10_000L)
        }
    }

    private val openShizukuManagerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateShizukuUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("jvcsmile", Context.MODE_PRIVATE)

        // find views
        rootLayout = findViewById(R.id.rootLayout)
        ivJoker = findViewById(R.id.ivJoker)
        overlayGradient = findViewById(R.id.overlayGradient)
        clockText = findViewById(R.id.clockText)
        phraseText = findViewById(R.id.phraseText)
        iconGrid = findViewById(R.id.iconGrid)
        dock = findViewById(R.id.dock)
        controlsPanel = findViewById(R.id.controlsPanel)
        btnModeNormal = findViewById(R.id.btnModeNormal)
        btnModeGaming = findViewById(R.id.btnModeGaming)
        btnTogglePanel = findViewById(R.id.btnTogglePanel)

        attachShizukuStatusWidgets()

        switchDns = findViewById(R.id.switchDns)
        btn60 = findViewById(R.id.btn60hz)
        btn90 = findViewById(R.id.btn90hz)
        btn120 = findViewById(R.id.btn120hz)
        btn144 = findViewById(R.id.btn144hz)
        inputWidth = findViewById(R.id.inputWidth)
        inputHeight = findViewById(R.id.inputHeight)
        inputDpi = findViewById(R.id.inputDpi)
        switchDisableAnimations = findViewById(R.id.switchDisableAnimations)
        inputWakePackage = findViewById(R.id.inputWakePackage)
        inputWakeActivity = findViewById(R.id.inputWakeActivity)
        btnEnter = findViewById(R.id.btnEnter)
        progressBar = findViewById(R.id.progressBar)

        setupVisuals()
        setupControls()

        btnModeNormal.setOnClickListener { switchMode(ThemeEngine.Mode.NORMAL) }
        btnModeGaming.setOnClickListener { switchMode(ThemeEngine.Mode.GAMING) }
        btnTogglePanel.setOnClickListener {
            controlsPanel.visibility =
                if (controlsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val savedModeStr = prefs.getString("mode", "normal")
        currentMode = if (savedModeStr == "gaming") ThemeEngine.Mode.GAMING else ThemeEngine.Mode.NORMAL

        highlightMode(currentMode)
        startJokerAnimationForMode(currentMode)
        loadApps()
        clockRunnable.run()
        updateShizukuUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        bgAnimator?.cancel()
    }

    private fun attachShizukuStatusWidgets() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            gravity = Gravity.CENTER_VERTICAL
        }

        shizukuStatusText = TextView(this).apply {
            text = "Shizuku: comprobando..."
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        shizukuActionBtn = Button(this).apply {
            text = "Acción"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onShizukuActionClicked() }
        }

        val retryBtn = android.widget.ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_popup_sync)
            background = null
            setOnClickListener { updateShizukuUi() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 0, 0) }
        }

        container.addView(shizukuStatusText)
        container.addView(shizukuActionBtn)
        container.addView(retryBtn)

        controlsPanel.addView(container, 0)
    }

    private fun setupVisuals() {
        val dockDrawable = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                ContextCompat.getColor(this, R.color.dock_dark),
                ContextCompat.getColor(this, R.color.dock_highlight)
            )
        )
        dockDrawable.cornerRadius = 8f
        dock.background = dockDrawable
        overlayGradient.setBackgroundColor(ContextCompat.getColor(this, R.color.overlay_tint))
    }

    private fun setupControls() {
        highlightHzButton(btn60)
        btn60.setOnClickListener { onHzSelected(60, btn60) }
        btn90.setOnClickListener { onHzSelected(90, btn90) }
        btn120.setOnClickListener { onHzSelected(120, btn120) }
        btn144.setOnClickListener { onHzSelected(144, btn144) }
        
        btnEnter.setOnClickListener {
            if (!ShizukuPermission.permissionGranted()) {
                AlertDialog.Builder(this)
                    .setTitle("Permiso Shizuku requerido")
                    .setMessage("La app necesita permiso Shizuku para aplicar ajustes del sistema. ¿Solicitar ahora?")
                    .setPositiveButton("Solicitar") { _, _ -> requestShizukuPermissionFlow() }
                    .setNeutralButton("Abrir Manager") { _, _ -> openShizukuManager() }
                    .setNegativeButton("Cancelar", null)
                    .show()
                return@setOnClickListener
            }
            applyAllSettingsViaShizuku()
        }
    }

    private fun onHzSelected(hz: Int, btn: Button) {
        currentHz = hz
        highlightHzButton(btn)
    }

    private fun highlightHzButton(active: Button) {
        val normal = ContextCompat.getColor(this, R.color.hz_button_normal)
        val activeColor = ContextCompat.getColor(this, R.color.hz_button_active)
        btn60.setBackgroundColor(normal)
        btn90.setBackgroundColor(normal)
        btn120.setBackgroundColor(normal)
        btn144.setBackgroundColor(normal)
        active.setBackgroundColor(activeColor)
    }

    private fun switchMode(mode: ThemeEngine.Mode) {
        if (currentMode == mode) return
        currentMode = mode

        prefs.edit().putString("mode", if (mode == ThemeEngine.Mode.GAMING) "gaming" else "normal").apply()
        
        highlightMode(mode)
        startJokerAnimationForMode(mode)
        loadApps()
    }

    private fun highlightMode(mode: ThemeEngine.Mode) {
        if (mode == ThemeEngine.Mode.GAMING) {
