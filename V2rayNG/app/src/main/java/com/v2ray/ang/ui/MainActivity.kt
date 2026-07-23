package com.v2ray.ang.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity() {

    companion object {
        const val EXTRA_UPDATE_URL = "extra_update_url"
    }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private var trafficJob: Job? = null
    private var autoSwitchJob: Job? = null
    private var updateCheckJob: Job? = null
    private var tunnelFailCount = 0
    private var totalUpload = 0L
    private var totalDownload = 0L
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L

    val mainViewModel: MainViewModel by viewModels()

    private val requestVpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startV2Ray()
    }

    private val requestActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        loadServerList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnPower.setOnClickListener { handlePowerClick() }
        binding.btnSettings.setOnClickListener { showSettingsMenu(it) }
        binding.btnAdd.setOnClickListener { showAddMenu(it) }

        setupViewModel()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()
        initRussianBypassIfNeeded()

        UpdateUiHelper.initChannel(this)
        handleUpdateIntent(intent)
        lifecycleScope.launch {
            delay(5_000L)
            UpdateUiHelper.checkAndShow(this@MainActivity, lifecycleScope)
        }
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(false) }
        })
    }

    override fun onResume() {
        super.onResume()
        loadServerList()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_UPDATE_URL) ?: return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(UpdateUiHelper.NOTIF_UPDATE_ID)
        UpdateUiHelper.downloadAndInstall(this, url)
    }

    private fun setupViewModel() {
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(isLoading = false, isRunning = isRunning)
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun handlePowerClick() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
        } else {
            startV2Ray()
        }
    }

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_settings_popup, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.settings_profiles -> { requestActivityLauncher.launch(Intent(this, ProfilesActivity::class.java)); true }
                R.id.settings_config -> { requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java)); true }
                R.id.settings_per_app -> { requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java)); true }
                R.id.settings_language -> { startActivity(Intent(this, LanguageActivity::class.java)); true }
                R.id.settings_not_working -> { startActivity(Intent(this, NotWorkingActivity::class.java)); true }
                R.id.settings_telegram -> {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=SAQANet_bot"))) }
                    catch (e: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SAQANet_bot"))) }
                    true
                }
                R.id.settings_check_update -> { UpdateUiHelper.checkAndShowManual(this, lifecycleScope); true }
                R.id.settings_about -> { startActivity(Intent(this, AboutActivity::class.java)); true }
                R.id.settings_subscriptions -> { startActivity(Intent(this, SubscriptionActivity::class.java)); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showAddMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        popup.setOnMenuItemClickListener { item -> onImportMenuSelected(item) }
        popup.show()
    }

    private fun onImportMenuSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.import_qrcode -> { importQRcode(); true }
        R.id.import_clipboard -> { importClipboard(); true }
        R.id.import_manually_vless -> { importManually(EConfigType.VLESS.value); true }
        R.id.sub_update -> { importConfigViaSub(); true }
        else -> false
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        CoreServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        if (isLoading) return

        if (isRunning) {
            binding.shieldBar.setBackgroundResource(R.drawable.bg_shield_active)
            binding.shieldDot.setBackgroundResource(R.drawable.bg_dot_green)
            binding.tvShieldStatus.text = getString(R.string.saqanet_vpn_active)
            binding.tvShieldStatus.setTextColor(0xFF10B981.toInt())

            binding.powerGlow.visibility = View.VISIBLE
            binding.powerBtnCircle.setBackgroundResource(R.drawable.bg_power_btn_active)
            binding.ivPowerIcon.setImageResource(R.drawable.ic_power_active)
            binding.tvPowerHint.visibility = View.GONE
            binding.tvConnectionState.text = getString(R.string.saqanet_connected)
            binding.tvConnectionState.setTextColor(0xFF4F6EF7.toInt())
            startTrafficPolling()
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SELECT)) startAutoSwitching()
            // Delayed update check — runs 30s after VPN connects, doesn't interfere with tunnel startup
            updateCheckJob?.cancel()
            updateCheckJob = lifecycleScope.launch {
                delay(30_000L)
                UpdateUiHelper.checkAndShow(this@MainActivity, lifecycleScope)
            }
        } else {
            stopAutoSwitching()
            stopTrafficPolling()
            updateCheckJob?.cancel()
            binding.shieldBar.setBackgroundResource(R.drawable.bg_shield_inactive)
            binding.shieldDot.setBackgroundResource(R.drawable.bg_dot_red)
            binding.tvShieldStatus.text = getString(R.string.saqanet_vpn_inactive)
            binding.tvShieldStatus.setTextColor(0xFFEF4444.toInt())

            binding.powerGlow.visibility = View.INVISIBLE
            binding.powerBtnCircle.setBackgroundResource(R.drawable.bg_power_btn)
            binding.ivPowerIcon.setImageResource(R.drawable.ic_power)
            binding.tvPowerHint.visibility = View.VISIBLE
            binding.tvPowerHint.text = getString(R.string.saqanet_tap_to_connect)
            binding.tvPowerHint.setTextColor(0xFF6B7280.toInt())
            binding.tvConnectionState.text = getString(R.string.saqanet_disconnected)
            binding.tvConnectionState.setTextColor(0xFF4B5563.toInt())

            binding.tvTrafficUpload.text = "—"
            binding.tvTrafficDownload.text = "—"
            if (!UpdateUiHelper.isDownloading()) binding.tvUpdateBanner.visibility = View.GONE
        }
        loadServerList()
    }

    // ── Server list ────────────────────────────────────────────────────────────

    private fun loadServerList() {
        val container = binding.serverListContainer
        container.removeAllViews()

        val guids = MmkvManager.decodeAllServerList()
        if (guids.isEmpty()) return

        val currentGuid = MmkvManager.getSelectServer()
        val autoEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SELECT)

        var autoFlag = ""; var autoCity = ""
        if (autoEnabled && currentGuid != null) {
            val cfg = MmkvManager.decodeServerConfig(currentGuid)
            if (cfg != null) { val (f, c) = getServerMeta(cfg.remarks, cfg.server ?: ""); autoFlag = f; autoCity = c }
        }
        container.addView(buildAutoCard(autoEnabled, autoFlag, autoCity))

        val sortedGuids = guids.sortedWith(compareBy({ guid ->
            val cfg = MmkvManager.decodeServerConfig(guid)
            val remarks = cfg?.remarks ?: ""
            val isMobile = remarks.contains("Mobile", ignoreCase = true)
            val isReality = cfg?.security == "reality" || !cfg?.publicKey.isNullOrBlank()
            val group = if (isMobile) 0 else 1  // Mobile first, WiFi second
            val proto = when {
                isReality -> 2  // Reality last (WiFi only)
                cfg?.network == "ws" -> 0  // WS first
                else -> 1  // Hysteria2 middle
            }
            group * 10 + proto
        }, { guid ->
            MmkvManager.decodeServerConfig(guid)?.remarks ?: ""
        }))
        sortedGuids.forEach { guid ->
            val config = MmkvManager.decodeServerConfig(guid) ?: return@forEach
            val (flag, city) = getServerMeta(config.remarks, config.server ?: "")
            val isActive = guid == currentGuid
            val proto = when {
                config.configType == EConfigType.HYSTERIA2 -> "Hysteria2"
                config.security == "reality" || !config.publicKey.isNullOrBlank() -> "VLESS · Reality"
                config.network == "ws" -> "VLESS · WS TLS"
                else -> "VLESS"
            }
            container.addView(buildServerCard(flag, city, proto, isActive) {
                MmkvManager.encodeSettings(AppConfig.PREF_AUTO_SELECT, false)
                selectServer(guid)
            })
        }
    }

    private fun selectServer(guid: String) {
        MmkvManager.setSelectServer(guid)
        if (mainViewModel.isRunning.value == true) restartV2Ray()
        loadServerList()
    }

    private fun enableAutoMode() {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_SELECT, true)
        val guids = MmkvManager.decodeAllServerList()
        if (guids.isEmpty()) { loadServerList(); return }
        MmkvManager.setSelectServer(guids[0])
        if (mainViewModel.isRunning.value == true) restartV2Ray()
        loadServerList()
    }

    private fun refreshSubscriptions() {
        lifecycleScope.launch(Dispatchers.IO) {
            AngConfigManager.updateConfigViaSubAll()
            withContext(Dispatchers.Main) {
                loadServerList()
                toast(getString(R.string.saqanet_subscription_updated))
            }
        }
    }

    private fun startAutoSwitching() {
        tunnelFailCount = 0
        autoSwitchJob?.cancel()
        autoSwitchJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(15_000L) // Wait 15s before first check to let connection stabilise
            while (true) {
                delay(15_000L)
                if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SELECT)) break
                runPingAndSwitchIfBetter()
            }
        }
    }

    private fun stopAutoSwitching() { autoSwitchJob?.cancel(); autoSwitchJob = null }

    private suspend fun isTunnelAlive(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socksPort = SettingsManager.getSocksPort()
                if (socksPort == 0) return@withContext true
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
                val client = OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url("http://cp.cloudflare.com/").head().build()
                client.newCall(req).execute().use { true }
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun runPingAndSwitchIfBetter() {
        val guids = MmkvManager.decodeAllServerList()
        if (guids.size < 2) return
        val currentGuid = MmkvManager.getSelectServer() ?: guids[0]

        if (isTunnelAlive()) {
            tunnelFailCount = 0
            LogUtil.i(AppConfig.TAG, "Auto-switch: tunnel alive, no switch needed")
            withContext(Dispatchers.Main) { loadServerList() }
            return
        }

        tunnelFailCount++
        LogUtil.i(AppConfig.TAG, "Auto-switch: tunnel check failed ($tunnelFailCount/3)")
        if (tunnelFailCount < 3) return  // Need 3 consecutive failures before switching

        // Consecutive failures — switch to next server in round-robin order
        tunnelFailCount = 0
        val currentIdx = guids.indexOf(currentGuid)
        val nextGuid = guids[(currentIdx + 1) % guids.size]
        LogUtil.i(AppConfig.TAG, "Auto-switch: tunnel dead, switching to $nextGuid")
        withContext(Dispatchers.Main) {
            MmkvManager.setSelectServer(nextGuid)
            if (mainViewModel.isRunning.value == true) restartV2Ray()
            loadServerList()
        }
        // Wait for new connection to stabilise before next check
        delay(15_000L)
    }

    private fun initRussianBypassIfNeeded() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_RUSSIAN_BYPASS_INITIALIZED)) {
            // BYPASS_APPS must be true — without it, only Russian apps route through VPN (proxy-only mode)
            if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)) {
                MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, true)
            }
            return
        }
        val russianApps = mutableSetOf(
            // Банки
            "ru.sberbankmobile", "com.idamob.tinkoff.android", "ru.vtb24.mobilebanking.android",
            "ru.alfabank.mobile.android", "ru.gazprombank.android", "ru.psbank.mobile",
            "ru.mtsbank.android", "ru.ozon.finance", "ru.rosbank.android",
            "ru.open.mobile", "ru.sovcombank.mobile", "ru.raiffeisen.android",
            // Маркетплейсы
            "ru.wildberries.android", "com.wildberries.ru",
            "ru.ozon.app.android", "ru.sbermegamarket.app",
            "ru.yandex.market", "com.avito.android", "ru.avito",
            // Доставка еды и продуктов
            "ru.dodopizza.app", "com.dodopizza.app",
            "ru.samokat.app", "com.foodband.eda", "ru.eda",
            "ru.delivery.club", "ru.perekrestok.app", "ru.x5retail.app",
            "ru.chizhik.app", "ru.vkusvill.android",
            // Такси и транспорт
            "ru.yandex.taximeter", "ru.yandex.mobile",
            "ru.dublgis.dgismobile", "ru.rzd.passenger", "com.aviasales.app",
            // Соцсети и видео
            "com.vkontakte.android", "com.vk.video", "ru.ok.android", "ru.rutube.app",
            // Госуслуги и официальные
            "ru.gosuslugi.mobile", "ru.nalog.nalogpayer", "ru.russianpost.tracking.pochta",
            // Операторы связи
            "ru.mts.selfservice", "com.mts.android", "ru.megafon.selfservice",
            "ru.beeline.services", "ru.tele2.android",
            // Другие
            "ru.kinopoisk.android", "com.yandex.browser", "ru.yandex.music",
            "ru.taxsee.taxi", "ru.citypoint.carsharing"
        )
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, true)
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, russianApps)
        MmkvManager.encodeSettings(AppConfig.PREF_RUSSIAN_BYPASS_INITIALIZED, true)
    }

    private fun getServerMeta(remarks: String, host: String): Pair<String, String> {
        val r = remarks.uppercase()
        val h = host.lowercase()
        return when {
            r.contains("GERMANY") || r.contains("-DE") || h.contains("de1") || h.contains(".de.") -> "🇩🇪" to getString(R.string.saqanet_server_germany)
            r.contains("-CF") || (h.contains("saqanet.ru") && !h.contains("nl2")) -> "☁️" to getString(R.string.saqanet_server_amsterdam_cf)
            else -> "🇳🇱" to getString(R.string.saqanet_server_amsterdam)
        }
    }

    private fun buildAutoCard(isActive: Boolean, currentFlag: String = "", currentCity: String = ""): View {
        val dp = { v: Int -> (v * resources.displayMetrics.density + 0.5f).toInt() }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(if (isActive) R.drawable.bg_server_card_active else R.drawable.bg_server_card)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8) }
            layoutParams = lp
            isClickable = true; isFocusable = true
        }

        val icon = TextView(this).apply {
            text = "⚡"; textSize = 20f
            val lp = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.marginEnd = dp(10) }
            layoutParams = lp; gravity = Gravity.CENTER
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        TextView(this).apply {
            text = getString(R.string.saqanet_auto_mode); textSize = 15f; setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE5E7EB.toInt()); info.addView(this)
        }
        TextView(this).apply {
            text = if (isActive && currentCity.isNotEmpty()) "$currentFlag $currentCity" else getString(R.string.saqanet_best_server)
            textSize = 12f
            setTextColor(if (isActive && currentCity.isNotEmpty()) 0xFF9CA3AF.toInt() else 0xFF6B7280.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (2 * resources.displayMetrics.density).toInt(); layoutParams = lp
            info.addView(this)
        }
        val badge = TextView(this).apply {
            text = if (isActive) getString(R.string.saqanet_live) else getString(R.string.saqanet_server_select)
            textSize = 11f; setTypeface(null, Typeface.BOLD)
            setTextColor(if (isActive) 0xFF4F6EF7.toInt() else 0xFF9CA3AF.toInt())
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setBackgroundResource(if (isActive) R.drawable.bg_badge_blue else R.drawable.bg_badge_grey)
        }

        val refreshBtn = TextView(this).apply {
            text = "⟳"; textSize = 18f
            setPadding(dp(10), dp(3), dp(4), dp(3))
            setTextColor(0xFF6B7280.toInt())
            isClickable = true; isFocusable = true
            setOnClickListener { refreshSubscriptions() }
        }

        row.addView(icon); row.addView(info); row.addView(badge); row.addView(refreshBtn)
        row.setOnClickListener {
            if (isActive) toast(getString(R.string.saqanet_auto_active))
            else enableAutoMode()
        }
        return row
    }

    private fun buildServerCard(flag: String, city: String, proto: String, isActive: Boolean, onClick: () -> Unit): View {
        val dp = { v: Int -> (v * resources.displayMetrics.density + 0.5f).toInt() }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(if (isActive) R.drawable.bg_server_card_active else R.drawable.bg_server_card)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8) }
            layoutParams = lp
            isClickable = true; isFocusable = true
        }

        val tvFlag = TextView(this).apply {
            text = flag; textSize = 22f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(12); layoutParams = lp
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        TextView(this).apply {
            text = city; textSize = 15f
            if (isActive) setTypeface(null, Typeface.BOLD)
            setTextColor(if (isActive) 0xFFE5E7EB.toInt() else 0xFF9CA3AF.toInt())
            info.addView(this)
        }
        TextView(this).apply {
            text = proto; textSize = 12f
            setTextColor(0xFF6B7280.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (2 * resources.displayMetrics.density).toInt(); layoutParams = lp
            info.addView(this)
        }
        val right = if (isActive) {
            TextView(this).apply {
                text = getString(R.string.saqanet_live); textSize = 11f; setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF4F6EF7.toInt())
                setPadding(dp(8), dp(3), dp(8), dp(3))
                setBackgroundResource(R.drawable.bg_badge_blue)
            }
        } else {
            TextView(this).apply {
                text = getString(R.string.saqanet_server_select); textSize = 13f
                setTextColor(0xFF4F6EF7.toInt())
            }
        }

        row.addView(tvFlag); row.addView(info); row.addView(right)
        if (!isActive) row.setOnClickListener { onClick() }
        return row
    }

    // ── Traffic ────────────────────────────────────────────────────────────────

    private fun startTrafficPolling() {
        totalUpload = 0L; totalDownload = 0L
        val uid = android.os.Process.myUid()
        lastRxBytes = android.net.TrafficStats.getUidRxBytes(uid)
        lastTxBytes = android.net.TrafficStats.getUidTxBytes(uid)
        trafficJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000L)
                try {
                    val rxBytes = android.net.TrafficStats.getUidRxBytes(uid)
                    val txBytes = android.net.TrafficStats.getUidTxBytes(uid)
                    if (rxBytes >= 0 && lastRxBytes >= 0) {
                        val dD = rxBytes - lastRxBytes; val dU = txBytes - lastTxBytes
                        if (dD >= 0) totalDownload += dD; if (dU >= 0) totalUpload += dU
                    }
                    lastRxBytes = rxBytes; lastTxBytes = txBytes
                    val up = formatBytes(totalUpload); val down = formatBytes(totalDownload)
                    withContext(Dispatchers.Main) {
                        binding.tvTrafficUpload.text = up
                        binding.tvTrafficDownload.text = down
                    }
                } catch (e: Exception) { LogUtil.w("SAQANet", "Traffic poll error: ${e.message}") }
            }
        }
    }

    private fun stopTrafficPolling() { trafficJob?.cancel(); trafficJob = null }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    private fun updateServerCard() {
    }

    private fun showProfileSwitcher() {
        val cache = mainViewModel.serversCache
        if (cache.isEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        val currentGuid = MmkvManager.getSelectServer()
        val names = Array(cache.size) { i ->
            val p = cache[i].profile
            val (flag, city) = getServerMeta(p.remarks, p.server ?: "")
            val proto = when {
                p.security == "reality" || !p.publicKey.isNullOrBlank() -> "Reality"
                p.network == "ws" -> "WS TLS"
                else -> "VLESS"
            }
            "$flag $city · $proto"
        }
        val selected = cache.indexOfFirst { it.guid == currentGuid }

        AlertDialog.Builder(this)
            .setTitle(R.string.saqanet_select_server)
            .setSingleChoiceItems(names, selected) { dialog, which ->
                val newGuid = cache[which].guid
                MmkvManager.setSelectServer(newGuid)
                dialog.dismiss()
                if (mainViewModel.isRunning.value == true) {
                    restartV2Ray()
                } else {
                    updateServerCard()
                }
            }
            .setNegativeButton(R.string.saqanet_cancel, null)
            .show()
    }

    // Stub kept for GroupServerFragment compatibility
    fun refreshGroupTabTitles(refreshAll: Boolean = false) {}

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ── Import / export helpers ────────────────────────────────────────────────

    private fun importManually(createConfigType: Int) {
        when (createConfigType) {
            EConfigType.POLICYGROUP.value -> startActivity(
                Intent().putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
            EConfigType.PROXYCHAIN.value -> startActivity(
                Intent().putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
            else -> startActivity(
                Intent().putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
    }

    private fun importQRcode(): Boolean {
        launchQRCodeScanner { result -> if (result != null) importBatchConfig(result) }
        return true
    }

    private fun importClipboard(): Boolean {
        return try { importBatchConfig(Utils.getClipboard(this)); true }
        catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Failed to import from clipboard", e); false }
    }

    private fun importBatchConfig(server: String?) {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // If https:// URL — fetch subscription content and import VLESS keys directly
                val resolved = if (server != null &&
                    (server.startsWith("https://") || server.startsWith("http://"))
                ) {
                    try {
                        val raw = com.v2ray.ang.util.HttpUtil.getUrlContentWithUserAgent(
                            com.v2ray.ang.dto.UrlContentRequest(url = server)
                        )
                        val decoded = Utils.decode(raw)
                        if (decoded.contains("vless://") || decoded.contains("vmess://")) decoded else raw
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to fetch subscription URL", e)
                        server
                    }
                } else server

                val (count, countSub) = AngConfigManager.importBatchConfig(
                    resolved, mainViewModel.subscriptionId, true
                )
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                            val guids = MmkvManager.decodeServerList("")
                            if (guids.isNotEmpty()) {
                                MmkvManager.encodeSettings(AppConfig.PREF_AUTO_SELECT, true)
                                MmkvManager.setSelectServer(guids.last())
                            }
                            loadServerList()
                        }
                        countSub > 0 -> {}
                        else -> toastError(R.string.toast_failure)
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toastError(R.string.toast_failure); hideLoading() }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    private fun importConfigLocal(): Boolean {
        return try {
            launchFileChooser { uri ->
                if (uri != null) {
                    try {
                        contentResolver.openInputStream(uri).use { input ->
                            importBatchConfig(input?.bufferedReader()?.readText())
                        }
                    } catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Failed to read file", e) }
                }
            }
            true
        } catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Failed to launch file chooser", e); false }
    }

    fun importConfigViaSub(): Boolean {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                    toast(getString(R.string.title_update_config_count, result.configCount))
                    loadServerList()
                } else {
                    toast(R.string.title_update_subscription_no_subscription)
                }
                hideLoading()
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0) toast(getString(R.string.title_export_config_count, ret))
                else toastError(R.string.toast_failure)
                hideLoading()
            }
        }
    }

    private fun delAllConfig() {
        AlertDialog.Builder(this)
            .setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeAllServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_config_count, ret))
                        loadServerList()
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
