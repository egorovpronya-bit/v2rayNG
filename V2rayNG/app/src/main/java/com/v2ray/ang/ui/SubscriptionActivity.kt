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

        binding.plan30.setOnClickListener { openBot() }
        binding.plan90.setOnClickListener { openBot() }
        binding.plan180.setOnClickListener { openBot() }
        binding.planFamily30.setOnClickListener { openBot() }
        binding.planFamily90.setOnClickListener { openBot() }
        binding.planFamily180.setOnClickListener { openBot() }
        binding.btnPay.setOnClickListener { openBot() }
    }
}
