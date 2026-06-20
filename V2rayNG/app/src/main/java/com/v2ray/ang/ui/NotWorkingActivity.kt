package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.v2ray.ang.databinding.ActivityNotWorkingBinding

class NotWorkingActivity : BaseActivity() {

    private val binding by lazy { ActivityNotWorkingBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnOpenBot.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=SAQANet_bot")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SAQANet_bot")))
            }
        }
    }
}
