package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.v2ray.ang.databinding.ActivitySubscriptionBinding

class SubscriptionActivity : BaseActivity() {

    private val binding by lazy { ActivitySubscriptionBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // All plan cards open the bot
        val openBot: () -> Unit = {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=SAQANet_bot")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SAQANet_bot")))
            }
        }

        // Один
        binding.planOne30.setOnClickListener { openBot() }
        binding.planOne90.setOnClickListener { openBot() }
        binding.planOne180.setOnClickListener { openBot() }
        // Пара
        binding.planPair30.setOnClickListener { openBot() }
        binding.planPair90.setOnClickListener { openBot() }
        binding.planPair180.setOnClickListener { openBot() }
        // Семейный
        binding.planFamily30.setOnClickListener { openBot() }
        binding.planFamily90.setOnClickListener { openBot() }
        binding.planFamily180.setOnClickListener { openBot() }
        binding.btnPay.setOnClickListener { openBot() }
    }
}
