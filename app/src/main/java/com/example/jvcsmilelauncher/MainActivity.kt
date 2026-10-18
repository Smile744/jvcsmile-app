package com.example.jvcsmilelauncher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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

        highlightMode(
            if (prefs.getString("mode", "normal") == "gaming") {
                ThemeEngine.Mode.GAMING
            } else {
                ThemeEngine.Mode.NORMAL
            }
        )
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

    // ---------------- DUALIDAD JOKER: modo azul (normal) / rojo (gaming) ----------------

    private fun switchMode(mode: ThemeEngine.Mode) {
        currentMode = mode
        prefs.edit()
            .putString("mode", if (mode == ThemeEngine.Mode.GAMING) "gaming" else "normal")
            .apply()
        highlightMode(mode)
        startJokerAnimationForMode(mode)

        btnModeNormal.isEnabled = false
        btnModeGaming.isEnabled = false
        lifecycleScope.launch {
            val hz = if (mode == ThemeEngine.Mode.GAMING) {
                if (currentHz >= 90) currentHz else 120
            } else {
                60
            }
            val dns = switchDns.isChecked
            val res = withContext(Dispatchers.IO) {
                ThemeEngine.applyMode(this@MainActivity, mode, hz, dns)
            }
            btnModeNormal.isEnabled = true
            btnModeGaming.isEnabled = true
            val ok = res.results.count { it.success }
            val fail = res.results.size - ok
            val msg = buildString {
                append(if (res.wallpaperOk) "Fondo Joker aplicado" else "Fondo falló")
                append(" · $ok ajustes OK")
                if (fail > 0) append(" · $fail fallos (¿Shizuku activo?)")
            }
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            res.results.forEach { Log.i(TAG, it.toString()) }
        }
    }

    private fun highlightMode(mode: ThemeEngine.Mode) {
        currentMode = mode
        if (mode == ThemeEngine.Mode.GAMING) {
            phraseText.text = "Modo Gaming — Que empiece el espectáculo"
            clockText.setTextColor(0xFFFF1744.toInt())
            btnModeGaming.alpha = 1f
            btnModeNormal.alpha = 0.5f
            val redId = resources.getIdentifier("ic_joker_red", "drawable", packageName)
            if (redId != 0) ivJoker.setImageResource(redId)
        } else {
            phraseText.text = "Modo Normal — Relájate"
            clockText.setTextColor(0xFF00E5FF.toInt())
            btnModeNormal.alpha = 1f
            btnModeGaming.alpha = 0.5f
            val blueId = resources.getIdentifier("ic_joker_blue", "drawable", packageName)
            if (blueId != 0) ivJoker.setImageResource(blueId)
        }
        applyNeonHalo(mode)
    }

    private fun applyNeonHalo(mode: ThemeEngine.Mode) {
        val haloColor = if (mode == ThemeEngine.Mode.GAMING) {
            ContextCompat.getColor(this, R.color.joker_red)
        } else {
            ContextCompat.getColor(this, R.color.joker_blue)
        }
        val gd = dock.background as? GradientDrawable ?: GradientDrawable().apply { cornerRadius = 8f }
        gd.setStroke(6, haloColor and 0x66FFFFFF.toInt())
        dock.background = gd
    }

    private fun startJokerAnimationForMode(mode: ThemeEngine.Mode) {
        bgAnimator?.cancel()
        val from = if (mode == ThemeEngine.Mode.NORMAL) {
            ContextCompat.getColor(this, R.color.joker_blue_deep)
        } else {
            ContextCompat.getColor(this, R.color.joker_red_deep)
        }
        val to = if (mode == ThemeEngine.Mode.NORMAL) {
            ContextCompat.getColor(this, R.color.joker_blue)
        } else {
            ContextCompat.getColor(this, R.color.joker_red)
        }
        bgAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val c = anim.animatedValue as Int
                ivJoker.setColorFilter(c)
                overlayGradient.alpha = if (mode == ThemeEngine.Mode.GAMING) 0.78f else 0.47f
            }
            start()
        }
    }

    // ---------------- REJILLA DE APPS ----------------

    private fun loadApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .filter { it.activityInfo.packageName != packageName }
                    .distinctBy { it.activityInfo.packageName }
                    .sortedBy { it.loadLabel(pm).toString().lowercase(Locale.getDefault()) }
            }
            iconGrid.removeAllViews()
            apps.forEachIndexed { index, info -> addAppCell(info, index) }
        }
    }

    private fun addAppCell(info: ResolveInfo, index: Int) {
        val density = resources.displayMetrics.density
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (10 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(0x66202030)
            }
            elevation = 4f
            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(this).apply {
            val s = (52 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s)
            setImageDrawable(info.loadIcon(packageManager))
        }

        val label = TextView(this).apply {
            text = info.loadLabel(packageManager).toString()
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * density).toInt() }
        }

        cell.addView(icon)
        cell.addView(label)
        cell.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        val lp = GridLayout.LayoutParams(
            GridLayout.spec(index / 4),
            GridLayout.spec(index % 4)
        )
        lp.width = GridLayout.LayoutParams.WRAP_CONTENT
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT
        iconGrid.addView(cell, lp)
    }

    // ---------------- Ajustes avanzados (Hz manual) ----------------

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
            .setMessage("Shizuku está activo y concediste permiso. Los botones AZUL/ROJO cambiarán el tema completo del celular.")
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
