package com.v2ray.ang.ui

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.SaqaNetUpdateInfo
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UpdateUiHelper {

    private const val NOTIF_CHANNEL_ID = "saqanet_update_v2"
    const val NOTIF_UPDATE_ID = 1001

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Обновления SAQANet",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых версиях приложения"
                enableVibration(false)
                setSound(null, null)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    fun checkAndShow(activity: AppCompatActivity, scope: LifecycleCoroutineScope) {
        scope.launch {
            try {
                val info = UpdateCheckerManager.checkSaqaNetUpdate()
                if (!info.hasUpdate) return@launch
                withContext(Dispatchers.Main) { showResult(activity, info) }
            } catch (e: Exception) {
                LogUtil.w("SAQANet", "Update check failed: ${e.message}")
            }
        }
    }

    fun checkAndShowManual(activity: AppCompatActivity, scope: LifecycleCoroutineScope) {
        activity.toast(R.string.update_checking)
        scope.launch {
            try {
                val info = UpdateCheckerManager.checkSaqaNetUpdate()
                withContext(Dispatchers.Main) {
                    if (info.hasUpdate) showResult(activity, info)
                    else activity.toast(R.string.update_latest)
                }
            } catch (e: Exception) {
                LogUtil.w("SAQANet", "Update check failed: ${e.message}")
                withContext(Dispatchers.Main) { activity.toast(R.string.update_check_failed) }
            }
        }
    }

    fun showResult(activity: AppCompatActivity, info: SaqaNetUpdateInfo) {
        activity.findViewById<TextView>(R.id.tv_update_banner)?.let { banner ->
            banner.text = activity.getString(R.string.update_banner_available, info.version)
            banner.visibility = View.VISIBLE
            banner.setOnClickListener {
                if (info.isForced) showForcedDialog(activity, info)
                else showOptionalDialog(activity, info)
            }
        }
        if (info.isForced) showForcedDialog(activity, info)
        else {
            showNotification(activity, info)
            showOptionalDialog(activity, info)
        }
    }

    private fun showForcedDialog(activity: AppCompatActivity, info: SaqaNetUpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_required_title))
            .setMessage(
                activity.getString(R.string.update_required_message, info.version) +
                if (info.notes.isNotEmpty()) "\n\n${info.notes}" else ""
            )
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.update_now)) { _, _ ->
                downloadAndInstall(activity, info.apkUrl)
            }
            .create()
            .show()
    }

    private fun showOptionalDialog(activity: AppCompatActivity, info: SaqaNetUpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_new_version_found, info.version))
            .setMessage(activity.getString(R.string.update_optional_message))
            .setPositiveButton(activity.getString(R.string.update_now)) { _, _ ->
                downloadAndInstall(activity, info.apkUrl)
            }
            .setNegativeButton(activity.getString(R.string.update_later), null)
            .show()
    }

    private fun showNotification(activity: AppCompatActivity, info: SaqaNetUpdateInfo) {
        val tapIntent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_UPDATE_URL, info.apkUrl)
        }
        val pi = PendingIntent.getActivity(
            activity, NOTIF_UPDATE_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(activity, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(activity.getString(R.string.update_new_version_found, info.version))
            .setContentText(activity.getString(R.string.update_optional_message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .addAction(0, activity.getString(R.string.update_now), pi)
            .build()
        (activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_UPDATE_ID, notif)
    }

    fun downloadAndInstall(activity: AppCompatActivity, apkUrl: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("SAQANet")
            .setDescription(activity.getString(R.string.update_downloading))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "saqanet_update.apk")
            .setMimeType("application/vnd.android.package-archive")

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)
        val banner = activity.findViewById<TextView>(R.id.tv_update_banner)

        activity.lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(600)
                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (!cursor.moveToFirst()) { cursor.close(); continue }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                cursor.close()

                withContext(Dispatchers.Main) {
                    when (status) {
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> {
                            banner?.visibility = View.VISIBLE
                            banner?.text = if (total > 0) {
                                val dlMb = downloaded / 1048576
                                val totMb = total / 1048576
                                val pct = (downloaded * 100 / total).toInt()
                                "⬇ $dlMb МБ / $totMb МБ ($pct%)"
                            } else {
                                "⬇ Загрузка: ${downloaded / 1048576} МБ..."
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            banner?.text = "✓ Готово, открываем установщик..."
                            val uri = dm.getUriForDownloadedFile(downloadId)
                            if (uri != null) {
                                val install = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                activity.startActivity(install)
                            }
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            banner?.text = "⚠ Ошибка загрузки, попробуй ещё раз"
                            return@launch
                        }
                    }
                }
            }
        }
    }
}
