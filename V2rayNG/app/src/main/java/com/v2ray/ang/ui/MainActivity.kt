package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
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
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private var trafficJob: Job? = null
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
        binding.btnNotification.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        setupViewModel()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(false) }
        })
    }

    override fun onResume() {
        super.onResume()
        loadServerList()
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
        } else {
            stopTrafficPolling()
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

        // Auto card
        container.addView(buildAutoCard(guids, currentGuid))

        // Server cards
        guids.forEach { guid ->
            val config = MmkvManager.decodeServerConfig(guid) ?: return@forEach
            val (flag, city) = getServerMeta(config.remarks, config.server ?: "")
            val isActive = guid == currentGuid
            val card = buildServerCard(flag, city, "VLESS", isActive) {
                selectServer(guid)
            }
            container.addView(card)
        }
    }

    private fun selectServer(guid: String) {
        MmkvManager.setSelectServer(guid)
        if (mainViewModel.isRunning.value == true) restartV2Ray()
        loadServerList()
    }

    private fun getServerMeta(remarks: String, host: String): Pair<String, String> {
        val r = remarks.uppercase()
        val h = host.lowercase()
        return when {
            r.contains("-DE") || h.contains("de1") || h.contains(".de.") -> "🇩🇪" to "Германия"
            r.contains("-CF") || (h.contains("saqanet.ru") && !h.contains("nl2")) -> "☁️" to "Амстердам CF"
            else -> "🇳🇱" to "Амстердам"
        }
    }

    private fun buildAutoCard(guids: List<String>, currentGuid: String?): View {
        val dp = { v: Int -> (v * resources.displayMetrics.density + 0.5f).toInt() }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.bg_server_card)
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
            text = "Авто"; textSize = 15f; setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE5E7EB.toInt()); info.addView(this)
        }
        TextView(this).apply {
            text = "Лучший по скорости"; textSize = 12f
            setTextColor(0xFF6B7280.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (2 * resources.displayMetrics.density).toInt(); layoutParams = lp
            info.addView(this)
        }
        val badge = TextView(this).apply {
            text = "LIVE"; textSize = 11f; setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF4F6EF7.toInt())
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setBackgroundResource(R.drawable.bg_badge_blue)
        }

        row.addView(icon); row.addView(info); row.addView(badge)
        row.setOnClickListener { toast("Авто: выбирается лучший сервер по пингу") }
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
                text = "LIVE"; textSize = 11f; setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF4F6EF7.toInt())
                setPadding(dp(8), dp(3), dp(8), dp(3))
                setBackgroundResource(R.drawable.bg_badge_blue)
            }
        } else {
            TextView(this).apply {
                text = "Выбрать"; textSize = 13f
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
                val (count, countSub) = AngConfigManager.importBatchConfig(
                    server, mainViewModel.subscriptionId, true
                )
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                            val guids = MmkvManager.decodeServerList("")
                            if (guids.isNotEmpty()) MmkvManager.setSelectServer(guids.last())
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
