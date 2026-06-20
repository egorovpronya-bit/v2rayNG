package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

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
            override fun handleOnBackPressed() {
                moveTaskToBack(false)
            }
        })
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
                R.id.settings_config -> { requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java)); true }
                R.id.settings_per_app -> { requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java)); true }
                R.id.settings_killswitch -> { requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java)); true }
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
            binding.tvPowerHint.text = getString(R.string.saqanet_connected)
            binding.tvPowerHint.setTextColor(0xFF4F6EF7.toInt())
            binding.tvConnectionState.text = getString(R.string.saqanet_connected)
            binding.tvConnectionState.setTextColor(0xFF4F6EF7.toInt())

            binding.serverCard.visibility = View.VISIBLE
            updateServerCard()
        } else {
            binding.shieldBar.setBackgroundResource(R.drawable.bg_shield_inactive)
            binding.shieldDot.setBackgroundResource(R.drawable.bg_dot_red)
            binding.tvShieldStatus.text = getString(R.string.saqanet_vpn_inactive)
            binding.tvShieldStatus.setTextColor(0xFFEF4444.toInt())

            binding.powerGlow.visibility = View.INVISIBLE
            binding.powerBtnCircle.setBackgroundResource(R.drawable.bg_power_btn)
            binding.ivPowerIcon.setImageResource(R.drawable.ic_power)
            binding.tvPowerHint.text = getString(R.string.saqanet_tap_to_connect)
            binding.tvPowerHint.setTextColor(0xFF6B7280.toInt())
            binding.tvConnectionState.text = getString(R.string.saqanet_disconnected)
            binding.tvConnectionState.setTextColor(0xFF4B5563.toInt())

            binding.serverCard.visibility = View.INVISIBLE
            binding.tvTrafficUpload.text = "—"
            binding.tvTrafficDownload.text = "—"
        }
    }

    private fun updateServerCard() {
        val guid = MmkvManager.getSelectServer() ?: return
        val config = MmkvManager.decodeServerConfig(guid) ?: return
        binding.tvServerName.text = config.remarks.ifEmpty { config.server ?: "VPN Server" }
        binding.tvServerSub.text = config.configType.name.uppercase()
    }

    // Stub kept for GroupServerFragment compatibility
    fun refreshGroupTabTitles(refreshAll: Boolean = false) {}

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ── Import / export helpers ─────────────────────────────────────────────

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
        return try {
            importBatchConfig(Utils.getClipboard(this))
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import from clipboard", e)
            false
        }
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
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to read file", e)
                    }
                }
            }
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to launch file chooser", e)
            false
        }
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
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
