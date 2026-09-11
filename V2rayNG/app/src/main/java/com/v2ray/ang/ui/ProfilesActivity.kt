package com.v2ray.ang.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityProfilesBinding
import com.v2ray.ang.handler.MmkvManager

class ProfilesActivity : AppCompatActivity() {

    private val binding by lazy { ActivityProfilesBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        loadProfiles()
    }

    private fun loadProfiles() {
        val container = binding.profilesContainer
        container.removeAllViews()

        val guids = MmkvManager.decodeAllServerList()
        val currentGuid = MmkvManager.getSelectServer()

        if (guids.isEmpty()) {
            binding.tvEmpty.visibility = android.view.View.VISIBLE
            binding.scrollProfiles.visibility = android.view.View.GONE
            return
        }
        binding.tvEmpty.visibility = android.view.View.GONE
        binding.scrollProfiles.visibility = android.view.View.VISIBLE

        guids.forEach { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@forEach
            val rawName = profile.remarks.ifEmpty { profile.server ?: guid }
            val displayName = if (rawName.contains("Marz", ignoreCase = true) || rawName.contains("user_"))
                getString(R.string.saqanet_default_profile_name) else rawName
            val isSelected = guid == currentGuid

            // Row card
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(10), dp(14))
                setBackgroundResource(R.drawable.bg_server_card)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(10)
                layoutParams = lp
                isClickable = true
                isFocusable = true
            }

            // Active indicator dot
            val dot = TextView(this).apply {
                text = if (isSelected) "●" else "○"
                textSize = 16f
                setTextColor(if (isSelected) 0xFF4F6EF7.toInt() else 0xFF6B7280.toInt())
                val lp = LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = dp(10)
                layoutParams = lp
            }

            // Profile name
            val tvName = TextView(this).apply {
                text = displayName
                textSize = 15f
                setTextColor(if (isSelected) 0xFFE5E7EB.toInt() else 0xFF9CA3AF.toInt())
                if (isSelected) setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Select button (only for non-active)
            val btnSelect = Button(this).apply {
                text = if (isSelected) getString(R.string.saqanet_profile_active) else getString(R.string.saqanet_profile_select)
                textSize = 13f
                isEnabled = !isSelected
                setTextColor(if (isSelected) 0xFF10B981.toInt() else 0xFF4F6EF7.toInt())
                setBackgroundColor(0x00000000)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            }

            // Delete button
            val btnDelete = Button(this).apply {
                text = getString(R.string.saqanet_profile_delete)
                textSize = 13f
                setTextColor(0xFFEF4444.toInt())
                setBackgroundColor(0x00000000)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            }

            btnSelect.setOnClickListener {
                MmkvManager.setSelectServer(guid)
                setResult(Activity.RESULT_OK)
                Toast.makeText(this, getString(R.string.saqanet_profile_selected, displayName), Toast.LENGTH_SHORT).show()
                loadProfiles()
            }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.saqanet_profile_delete_confirm)
                    .setMessage(displayName)
                    .setPositiveButton(R.string.saqanet_profile_delete) { _, _ ->
                        MmkvManager.removeServer(guid)
                        setResult(Activity.RESULT_OK)
                        Toast.makeText(this, R.string.saqanet_profile_deleted, Toast.LENGTH_SHORT).show()
                        loadProfiles()
                    }
                    .setNegativeButton(R.string.saqanet_profile_cancel, null)
                    .show()
            }

            row.addView(dot)
            row.addView(tvName)
            row.addView(btnSelect)
            row.addView(btnDelete)
            container.addView(row)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
