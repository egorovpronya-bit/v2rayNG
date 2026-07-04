# Update Button + Persistent Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить кнопку "Проверить обновления" в настройки (шестерёнка) и сделать уведомление об обновлении постоянным в шторке (не исчезает пока не нажмёшь).

**Architecture:**
- Логика скачивания и диалогов переезжает из `MainActivity` в новый `UpdateUiHelper.kt` — вызывается из обоих Activity.
- Канал уведомлений меняется на `IMPORTANCE_HIGH` через новый ID `saqanet_update_v2` (старый ID нельзя изменить — Android игнорирует изменения importance существующего канала).
- Кнопка в `pref_settings.xml` вызывает `UpdateUiHelper` напрямую из `SettingsFragment`.

**Tech Stack:** Kotlin, AndroidX Preference, NotificationManager, DownloadManager, lifecycleScope

## Global Constraints

- minSdk = 21, targetSdk = 34
- Пакет: `com.v2ray.ang`
- `BuildConfig.VERSION_NAME` = `"2.2.4"` (текущий APK)
- `version.json` URL: `https://saqanet.ru/version.json`
- Не трогать логику VPN, серверов, роутинга

---

### Task 1: Создать `UpdateUiHelper.kt` — общая логика показа диалогов и скачивания

**Files:**
- Create: `app/src/main/java/com/v2ray/ang/ui/UpdateUiHelper.kt`

**Interfaces:**
- Consumes: `AppCompatActivity`, `LifecycleCoroutineScope`, `SaqaNetUpdateInfo`
- Produces:
  - `UpdateUiHelper.checkAndShow(activity, scope)` — проверяет и показывает результат
  - `UpdateUiHelper.showResult(activity, info)` — показывает диалог/уведомление по готовому info
  - `UpdateUiHelper.downloadAndInstall(activity, apkUrl)` — скачивает и запускает установку

- [ ] **Step 1: Создать файл**

```kotlin
package com.v2ray.ang.ui

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleCoroutineScope
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
        if (info.isForced) {
            showForcedDialog(activity, info)
        } else {
            showNotification(activity, info)
            showOptionalDialog(activity, info)
        }
    }

    private fun showForcedDialog(activity: AppCompatActivity, info: SaqaNetUpdateInfo) {
        val dialog = AlertDialog.Builder(activity)
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
        dialog.show()
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
        activity.toast(R.string.update_downloading)
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("SAQANet")
            .setDescription(activity.getString(R.string.update_downloading))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "saqanet_update.apk")
            .setMimeType("application/vnd.android.package-archive")

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                ctx.unregisterReceiver(this)
                val uri = dm.getUriForDownloadedFile(downloadId) ?: return
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                activity.startActivity(install)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }
}
```

- [ ] **Step 2: Убедиться что файл создан корректно — нет синтаксических ошибок**

Мысленно пройти по импортам: все классы существуют в AndroidX / Android SDK. `MainActivity.EXTRA_UPDATE_URL` — companion object константа, уже есть.

---

### Task 2: Добавить новые строки в strings.xml

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

**Interfaces:**
- Produces: `R.string.update_checking`, `R.string.update_latest`, `R.string.update_check_failed`, `R.string.title_check_update`, `R.string.summary_check_update`

- [ ] **Step 1: Добавить в `values/strings.xml`** (перед закрывающим `</resources>`):

```xml
    <string name="update_checking">Checking for updates…</string>
    <string name="update_latest">You have the latest version</string>
    <string name="update_check_failed">Failed to check for updates</string>
    <string name="title_check_update">Check for updates</string>
    <string name="summary_check_update">Current version: %s</string>
```

- [ ] **Step 2: Добавить в `values-ru/strings.xml`** (перед закрывающим `</resources>`):

```xml
    <string name="update_checking">Проверяем обновления…</string>
    <string name="update_latest">У вас последняя версия</string>
    <string name="update_check_failed">Не удалось проверить обновления</string>
    <string name="title_check_update">Проверить обновления</string>
    <string name="summary_check_update">Текущая версия: %s</string>
```

---

### Task 3: Добавить кнопку в `pref_settings.xml`

**Files:**
- Modify: `app/src/main/res/xml/pref_settings.xml`

- [ ] **Step 1: Добавить секцию перед закрывающим `</PreferenceScreen>`**:

```xml
    <PreferenceCategory android:title="@string/title_about">

        <Preference
            android:key="pref_check_update"
            android:title="@string/title_check_update"
            android:summary="@string/summary_check_update" />

    </PreferenceCategory>
```

---

### Task 4: Обновить `MainActivity.kt` — использовать `UpdateUiHelper`

**Files:**
- Modify: `app/src/main/java/com/v2ray/ang/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `UpdateUiHelper.initChannel()`, `UpdateUiHelper.checkAndShow()`, `UpdateUiHelper.showResult()`, `UpdateUiHelper.downloadAndInstall()`

- [ ] **Step 1: Убрать старые методы и поля в MainActivity**

Удалить из companion object:
```kotlin
private const val NOTIF_CHANNEL_ID = "saqanet_update"
private const val NOTIF_UPDATE_ID = 1001
```

Удалить методы (они переехали в UpdateUiHelper):
- `showForcedUpdateDialog()`
- `showOptionalUpdateDialog()`
- `createUpdateNotificationChannel()`
- `downloadAndInstallApk()`

- [ ] **Step 2: Обновить `onCreate` — заменить вызовы**

Было:
```kotlin
createUpdateNotificationChannel()
handleUpdateIntent(intent)
checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
checkForUpdatesOnStartup()
```

Стало:
```kotlin
UpdateUiHelper.initChannel(this)
handleUpdateIntent(intent)
checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
checkForUpdatesOnStartup()
```

- [ ] **Step 3: Обновить `checkForUpdatesOnStartup`**

Было:
```kotlin
private fun checkForUpdatesOnStartup() {
    lifecycleScope.launch {
        try {
            val info = UpdateCheckerManager.checkSaqaNetUpdate()
            if (!info.hasUpdate) return@launch
            withContext(Dispatchers.Main) {
                if (info.isForced) showForcedUpdateDialog(info)
                else showOptionalUpdateDialog(info)
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Update check failed: ${e.message}")
        }
    }
}
```

Стало:
```kotlin
private fun checkForUpdatesOnStartup() {
    UpdateUiHelper.checkAndShow(this, lifecycleScope)
}
```

- [ ] **Step 4: Обновить `handleUpdateIntent`**

Было:
```kotlin
private fun handleUpdateIntent(intent: Intent) {
    val url = intent.getStringExtra(EXTRA_UPDATE_URL) ?: return
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancel(NOTIF_UPDATE_ID)
    downloadAndInstallApk(url)
}
```

Стало:
```kotlin
private fun handleUpdateIntent(intent: Intent) {
    val url = intent.getStringExtra(EXTRA_UPDATE_URL) ?: return
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancel(UpdateUiHelper.NOTIF_UPDATE_ID)
    UpdateUiHelper.downloadAndInstall(this, url)
}
```

- [ ] **Step 5: Убрать неиспользуемые импорты**

После удаления методов убрать импорты которые больше не используются в MainActivity:
- `import android.app.DownloadManager` — проверить, теперь только в UpdateUiHelper
- `import android.content.BroadcastReceiver`
- `import android.content.IntentFilter`
- `import android.os.Environment`
- `import com.v2ray.ang.handler.UpdateCheckerManager`
- `import kotlinx.coroutines.Dispatchers`
- `import kotlinx.coroutines.withContext`

Каждый — проверить что больше не упоминается в файле, тогда удалить.

---

### Task 5: Обновить `SettingsActivity.kt` — добавить обработчик кнопки

**Files:**
- Modify: `app/src/main/java/com/v2ray/ang/ui/SettingsActivity.kt`

- [ ] **Step 1: Добавить `checkUpdate` preference в `SettingsFragment`**

В блок `private val ...` добавить:
```kotlin
private val checkUpdate by lazy { findPreference<androidx.preference.Preference>("pref_check_update") }
```

- [ ] **Step 2: Обновить summary кнопки с текущей версией**

В `onCreatePreferences`, после `initPreferenceSummaries()`:
```kotlin
checkUpdate?.summary = getString(R.string.summary_check_update, BuildConfig.VERSION_NAME)
checkUpdate?.setOnPreferenceClickListener {
    UpdateUiHelper.checkAndShowManual(requireActivity() as AppCompatActivity, lifecycleScope)
    true
}
```

- [ ] **Step 3: Добавить импорты**

```kotlin
import com.v2ray.ang.BuildConfig
```

`UpdateUiHelper` в том же пакете `com.v2ray.ang.ui` — импорт не нужен.

---

### Task 6: Проверить компиляцию и запустить сборку

- [ ] **Step 1: Проверить компиляцию**

```bash
cd D:\SAQANet-Android\V2rayNG
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```

Ожидание: `BUILD SUCCESSFUL`

- [ ] **Step 2: Коммит**

```bash
git add app/src/main/java/com/v2ray/ang/ui/UpdateUiHelper.kt \
        app/src/main/java/com/v2ray/ang/ui/MainActivity.kt \
        app/src/main/java/com/v2ray/ang/ui/SettingsActivity.kt \
        app/src/main/res/xml/pref_settings.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-ru/strings.xml
git commit -m "feat: add check-update button in settings + persistent notification

- Extract update UI logic to UpdateUiHelper (DRY)
- Add 'Check for updates' preference at bottom of settings screen
- Summary shows current version number
- Manual check shows toast while checking, dialog on result
- Startup check shows IMPORTANCE_HIGH notification (stays in drawer)
  + dialog (works even when notifications blocked)
- Channel ID upgraded to saqanet_update_v2 (IMPORTANCE_HIGH)

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
git push origin master
```
