package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLanguageBinding
import com.v2ray.ang.enums.Language
import com.v2ray.ang.handler.MmkvManager

class LanguageActivity : BaseActivity() {

    private val binding by lazy { ActivityLanguageBinding.inflate(layoutInflater) }

    private var selectedLang = Language.RUSSIAN.code

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        selectedLang = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE) ?: Language.RUSSIAN.code
        updateSelection()

        binding.langRu.setOnClickListener { selectedLang = Language.RUSSIAN.code; updateSelection() }
        binding.langEn.setOnClickListener { selectedLang = Language.ENGLISH.code; updateSelection() }
        binding.langSakha.setOnClickListener { selectedLang = Language.SAKHA.code; updateSelection() }

        binding.btnApplyLang.setOnClickListener { applyLanguage() }
    }

    private fun updateSelection() {
        binding.langRu.setBackgroundResource(
            if (selectedLang == Language.RUSSIAN.code) R.drawable.bg_card_active else R.drawable.bg_card_default
        )
        binding.langEn.setBackgroundResource(
            if (selectedLang == Language.ENGLISH.code) R.drawable.bg_card_active else R.drawable.bg_card_default
        )
        binding.langSakha.setBackgroundResource(
            if (selectedLang == Language.SAKHA.code) R.drawable.bg_card_active else R.drawable.bg_card_default
        )
        binding.checkRu.visibility = if (selectedLang == Language.RUSSIAN.code) View.VISIBLE else View.INVISIBLE
        binding.checkEn.visibility = if (selectedLang == Language.ENGLISH.code) View.VISIBLE else View.INVISIBLE
        binding.checkSakha.visibility = if (selectedLang == Language.SAKHA.code) View.VISIBLE else View.INVISIBLE
    }

    private fun applyLanguage() {
        MmkvManager.encodeSettings(AppConfig.PREF_LANGUAGE, selectedLang)
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
