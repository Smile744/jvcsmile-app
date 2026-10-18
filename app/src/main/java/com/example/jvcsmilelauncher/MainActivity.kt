package com.example.jvcsmilelauncher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.lifecycleScope
import com.example.jvcsmilelauncher.util.ShizukuExecutor
import com.example.jvcsmilelauncher.util.ShizukuPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var currentMode: Mode = Mode.CALM

    private val openShizukuManagerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateShizukuUi()
        }

    enum class Mode { CALM, PRO }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // find views
        rootLayout = findViewById(R.id.rootLayout)
        ivJoker = findViewById(R.id.ivJoker)
        overlayGradient = findViewById(R.id.overlayGradient)
        clockText = findViewById(R.id.clockText)
        phraseText = findViewById(R.id.phraseText)
        iconGrid = findViewById(R.id.iconGrid)
        dock = findViewById(R.id.dock)
        controlsPanel = findViewById(R.id.controlsPanel)

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
        setupIconHexClip()
        setupControls()

        startJokerAnimationForMode(currentMode)
        updateShizukuUi()
    }

    override fun onResume() {
        super.onResume()
        updateShizukuUi()
    }

    override fun onDestroy() {
        super.onDestroy()
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

        val retryBtn = ImageButton(this).apply {
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
        val blueId = resources.getIdentifier("ic_joker_blue", "drawable", packageName)
        if (blueId != 0) ivJoker.setImageResource(blueId)
    }

    private fun setupIconHexClip() {
        val childCount = iconGrid.childCount
        for (i in 0 until childCount) {
            val v = iconGrid.getChildAt(i)
            v.clipToOutline = true
            v.outlineProvider = hexagonOutlineProvider()
            v.elevation = 6f
        }
    }

    private fun hexagonOutlineProvider(): ViewOutlineProvider {
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width.toFloat()
                val h = view.height.toFloat()
                if (w == 0f || h == 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, 6f)
                    return
                }
                val cx = w / 2f
                val cy = h / 2f
                val r = Math.min(w, h) * 0.48f
                val path = android.graphics.Path()
                for (i in 0 until 6) {
                    val theta = Math.toRadians((60 * i - 30).toDouble())
                    val x = (cx + r * Math.cos(theta)).toFloat()
                    val y = (cy + r * Math.sin(theta)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                try {
                    outline.setConvexPath(path)
                } catch (t: Throwable) {
                    outline.setRoundRect(0, 0, view.width, view.height, 6f)
                }
            }
        }
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
            setMode(Mode.PRO)
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

    private fun setMode(mode: Mode) {
        if (mode == currentMode) return
        currentMode = mode
        if (mode == Mode.PRO) {
            phraseText.text = "Modo Pro — ¡A jugar!"
            animateControlsAccent("#FF1744")
            applyNeonHalo(ColorMode.RED)
            val redId = resources.getIdentifier("ic_joker_red", "drawable", packageName)
            if (redId != 0) ivJoker.setImageResource(redId) else startJokerAnimationForMode(mode)
        } else {
            phraseText.text = "Modo Tranquilo — Relájate"
            animateControlsAccent("#00E5FF")
            applyNeonHalo(ColorMode.BLUE)
            val blueId = resources.getIdentifier("ic_joker_blue", "drawable", packageName)
            if (blueId != 0) ivJoker.setImageResource(blueId) else startJokerAnimationForMode(mode)
        }
    }

    private enum class ColorMode { BLUE, RED }

    private fun applyNeonHalo(colorMode: ColorMode) {
        val haloColor = if (colorMode == ColorMode.BLUE) ContextCompat.getColor(this, R.color.joker_blue) else ContextCompat.getColor(this, R.color.joker_red)
        val gd = dock.background as? GradientDrawable ?: GradientDrawable().apply { cornerRadius = 8f }
        gd.setStroke(6, haloColor and 0x66FFFFFF.toInt())
        dock.background = gd
    }

    private fun animateControlsAccent(hexColor: String) {
        try {
            val color = android.graphics.Color.parseColor(hexColor)
            val gd = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color, darken(color)))
            gd.cornerRadius = 12f
            controlsPanel.background = gd
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun darken(color: Int): Int {
        val factor = 0.7f
        val r = (android.graphics.Color.red(color) * factor).toInt()
        val g = (android.graphics.Color.green(color) * factor).toInt()
        val b = (android.graphics.Color.blue(color) * factor).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun startJokerAnimationForMode(mode: Mode) {
        bgAnimator?.cancel()
        val from = if (mode == Mode.CALM) ContextCompat.getColor(this, R.color.joker_blue_deep) else ContextCompat.getColor(this, R.color.joker_red_deep)
        val to = if (mode == Mode.CALM) ContextCompat.getColor(this, R.color.joker_blue) else ContextCompat.getColor(this, R.color.joker_red)
        bgAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val c = anim.animatedValue as Int
                ivJoker.setColorFilter(c)
                overlayGradient.alpha = if (mode == Mode.PRO) 200f else 120f
            }
            start()
        }
    }
        // ---------------- Shizuku permission UI / flow ----------------

    private fun updateShizukuUi() {
        try {
            val available = ShizukuExecutor.isAvailable()
            val granted = ShizukuPermission.permissionGranted()

            when {
                available && granted -> {
                    shizukuStatusText.text = "Shizuku: listo (permiso concedido)"
                    shizukuActionBtn.text = "Info"
                    shizukuActionBtn.setOnClickListener { showShizukuInfoDialog() }
                }
                available && !granted -> {
                    shizukuStatusText.text = "Shizuku: disponible, permiso pendiente"
                    shizukuActionBtn.text = "Conceder permiso"
                    shizukuActionBtn.setOnClickListener { requestShizukuPermissionFlow() }
                }
                else -> {
                    shizukuStatusText.text = "Shizuku: no activo"
                    shizukuActionBtn.text = "Abrir Manager"
                    shizukuActionBtn.setOnClickListener { openShizukuManager() }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error updating Shizuku UI: ${t.message}", t)
            shizukuStatusText.text = "Shizuku: estado desconocido"
            shizukuActionBtn.text = "Reintentar"
            shizukuActionBtn.setOnClickListener { updateShizukuUi() }
        }
    }

    private fun showShizukuInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Shizuku listo")
            .setMessage("Shizuku está activo y concediste permiso. Puedes aplicar ajustes del sistema ahora.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun requestShizukuPermissionFlow() {
        try {
            ShizukuPermission.ensureShizukuAvailable(this)
            AlertDialog.Builder(this)
                .setTitle("Conceder permiso")
                .setMessage("Se solicitará permiso en Shizuku. Sigue las instrucciones en Shizuku Manager. ¿Abrir Manager si no aparece la petición?")
                .setPositiveButton("Abrir Manager") { _, _ -> openShizukuManager() }
                .setNegativeButton("Cancelar", null)
                .show()
        } catch (t: Throwable) {
            Log.w(TAG, "requestShizukuPermissionFlow exception: ${t.message}", t)
            openShizukuManager()
        }
    }

    private fun openShizukuManager() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
        if (intent != null) {
            try {
                openShizukuManagerLauncher.launch(intent)
            } catch (t: Throwable) {
                Toast.makeText(this, "No se pudo abrir Shizuku Manager", Toast.LENGTH_SHORT).show()
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle("Shizuku Manager no encontrado")
                .setMessage("Instala/activa Shizuku Manager en el dispositivo y concede permiso a esta app.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun onShizukuActionClicked() {
        updateShizukuUi()
    }

    // ---------------- Apply settings via Shizuku ----------------

    private fun applyAllSettingsViaShizuku() {
        val dnsEnabled = switchDns.isChecked
        val hz = currentHz
        val width = inputWidth.text.toString().toIntOrNull()
        val height = inputHeight.text.toString().toIntOrNull()
        val dpi = inputDpi.text.toString().toIntOrNull()
        val disableAnim = switchDisableAnimations.isChecked
        val wakePkg = inputWakePackage.text.toString().takeIf { it.isNotBlank() }
        val wakeActivity = inputWakeActivity.text.toString().takeIf { it.isNotBlank() }

        btnEnter.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                val out = mutableListOf<ShizukuExecutor.CommandResult>()
                if (!ShizukuPermission.permissionGranted()) {
                    out += ShizukuExecutor.CommandResult(false, "Shizuku permiso no concedido")
                    return@withContext out
                }

                // DNS (private DNS hostname)
                try {
                    if (dnsEnabled) {
                        out += ShizukuExecutor.exec("settings put global private_dns_mode hostname")
                        out += ShizukuExecutor.exec("settings put global private_dns_specifier ://cloudflare-dns.com")
                    } else {
                        out += ShizukuExecutor.exec("settings put global private_dns_mode off")
                    }
                } catch (t: Throwable) {
                    out += ShizukuExecutor.CommandResult(false, "DNS error: ${t.message}")
                }

                // Refresh rate (Hz)
                try {
                    out += ShizukuExecutor.exec("settings put system peak_refresh_rate $hz")
                    out += ShizukuExecutor.exec("settings put system min_refresh_rate $hz")
                } catch (t: Throwable) {
                    out += ShizukuExecutor.CommandResult(false, "Refresh error: ${t.message}")
                }

                // Resolution / DPI
                try {
                    if (width != null && height != null) {
                        out += ShizukuExecutor.exec("wm size ${width}x${height}")
                    }
                    if (dpi != null) {
                        out += ShizukuExecutor.exec("wm density $dpi")
                    }
                } catch (t: Throwable) {
                    out += ShizukuExecutor.CommandResult(false, "WM error: ${t.message}")
                }

                // Animations
                try {
                    if (disableAnim) {
                        out += ShizukuExecutor.exec("settings put global window_animation_scale 0")
                        out += ShizukuExecutor.exec("settings put global transition_animation_scale 0")
                        out += ShizukuExecutor.exec("settings put global animator_duration_scale 0")
                    } else {
                        out += ShizukuExecutor.exec("settings put global window_animation_scale 1")
                        out += ShizukuExecutor.exec("settings put global transition_animation_scale 1")
                        out += ShizukuExecutor.exec("settings put global animator_duration_scale 1")
                    }
                } catch (t: Throwable) {
                    out += ShizukuExecutor.CommandResult(false, "Animations error: ${t.message}")
                }

                // Wake / start game
                try {
                    if (!wakePkg.isNullOrEmpty()) {
                        out += ShizukuExecutor.exec("cmd deviceidle whitelist +$wakePkg")
                        if (!wakeActivity.isNullOrEmpty()) {
                            out += ShizukuExecutor.exec("am start -n $wakePkg/$wakeActivity")
                        } else {
                            out += ShizukuExecutor.exec("monkey -p $wakePkg -c android.intent.category.LAUNCHER 1")
                        }
                        out += ShizukuExecutor.exec("input keyevent KEYCODE_WAKEUP")
                    }
                } catch (t: Throwable) {
                    out += ShizukuExecutor.CommandResult(false, "Wake error: ${t.message}")
                }

                out
            }

            progressBar.visibility = View.GONE
            btnEnter.isEnabled = true

            val ok = results.count { it.success }
            val fail = results.size - ok
            Toast.makeText(this@MainActivity, "Aplicados: $ok OK / $fail fallos", Toast.LENGTH_LONG).show()
            results.forEach { Log.i(TAG, it.toString()) }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
