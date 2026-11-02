package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.databinding.ActivitySubscriptionBinding

class SubscriptionActivity : BaseActivity() {

    private val binding by lazy { ActivitySubscriptionBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        fun showChoice(planId: String, label: String) {
            AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(arrayOf("💬 Оплатить через Telegram", "🌐 Оплатить на сайте")) { _, which ->
                    when (which) {
                        0 -> openBot()
                        1 -> openBrowser("https://saqanet.ru/buy?plan=$planId")
                    }
                }
                .show()
        }

        // Один
        binding.planOne30.setOnClickListener  { showChoice("one_30",    "Один · 30 дней · 99 ₽") }
        binding.planOne90.setOnClickListener  { showChoice("one_90",    "Один · 90 дней · 277 ₽") }
        binding.planOne180.setOnClickListener { showChoice("one_180",   "Один · 180 дней · 554 ₽") }
        // Пара
        binding.planPair30.setOnClickListener  { showChoice("pair_30",  "Пара · 30 дней · 199 ₽") }
        binding.planPair90.setOnClickListener  { showChoice("pair_90",  "Пара · 90 дней · 557 ₽") }
        binding.planPair180.setOnClickListener { showChoice("pair_180", "Пара · 180 дней · 1 114 ₽") }
        // Семейный
        binding.planFamily30.setOnClickListener  { showChoice("family_30",  "Семейный · 30 дней · 299 ₽") }
        binding.planFamily90.setOnClickListener  { showChoice("family_90",  "Семейный · 90 дней · 837 ₽") }
        binding.planFamily180.setOnClickListener { showChoice("family_180", "Семейный · 180 дней · 1 674 ₽") }

        // Кнопка "Оплатить" внизу — общий вход (без конкретного плана)
        binding.btnPay.setOnClickListener {
            showChoice("one_90", "Оплата подписки")
        }
    }

    private fun openBot() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=SAQANet_bot")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SAQANet_bot")))
        }
    }

    private fun openBrowser(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
