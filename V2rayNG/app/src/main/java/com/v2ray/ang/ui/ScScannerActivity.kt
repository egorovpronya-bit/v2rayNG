package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScScannerActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val resolved = if (scanResult.startsWith("https://") || scanResult.startsWith("http://")) {
                        try {
                            val raw = HttpUtil.getUrlContentWithUserAgent(UrlContentRequest(url = scanResult))
                            val decoded = Utils.decode(raw)
                            if (decoded.contains("vless://") || decoded.contains("vmess://")) decoded else raw
                        } catch (e: Exception) {
                            scanResult
                        }
                    } else scanResult

                    val (count, countSub) = AngConfigManager.importBatchConfig(resolved, "", false)

                    withContext(Dispatchers.Main) {
                        if (count + countSub > 0) {
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                        startActivity(Intent(this@ScScannerActivity, MainActivity::class.java))
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }
}
