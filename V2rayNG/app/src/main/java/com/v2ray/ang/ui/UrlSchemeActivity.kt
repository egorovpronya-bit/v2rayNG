package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseActivity() {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                            confirmAndImport(text, null)
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    when (data?.host) {
                        "install-config" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url").orEmpty()
                            confirmAndImport(shareUrl, uri?.fragment)
                        }
                        "install-sub" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url").orEmpty()
                            confirmAndImport(shareUrl, uri?.fragment)
                        }
                        else -> {
                            toastError(R.string.toast_failure)
                            finish()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
            finish()
        }
    }

    private fun confirmAndImport(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Импорт конфигурации")
            .setMessage("Добавить конфигурацию в SAQANet?")
            .setPositiveButton("Добавить") { _, _ ->
                parseUri(uriString, fragment)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setNegativeButton("Отмена") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun parseUri(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) return
        LogUtil.i(AppConfig.TAG, uriString)

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null) {
            if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
                decodedUrl += "#${fragment}"
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val (count, countSub) = AngConfigManager.importBatchConfig(decodedUrl, "", false)
                withContext(Dispatchers.Main) {
                    if (count + countSub > 0) {
                        toast(R.string.import_subscription_success)
                    } else {
                        toast(R.string.import_subscription_failure)
                    }
                }
            }
        }
    }
}
