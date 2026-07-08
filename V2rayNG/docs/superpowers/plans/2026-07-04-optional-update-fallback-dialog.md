# Optional Update Fallback Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить уведомление в шторке на диалог для опциональных обновлений — надёжно работает на всех Android-прошивках включая MIUI/One UI.

**Architecture:** Метод `showOptionalUpdateDialog` в `MainActivity.kt` проверяет, доступны ли уведомления (канал не заблокирован, приложение не в foreground-режиме с агрессивным менеджером). Если нет — показывает dismissible AlertDialog.

**Tech Stack:** Kotlin, AndroidX AlertDialog, NotificationManager API

## Global Constraints

- minSdk = 21, targetSdk = 34
- Не трогать `showForcedUpdateDialog` — он работает корректно
- Не менять логику `checkForUpdatesOnStartup`, `handleUpdateIntent`, `downloadAndInstallApk`
- Один коммит в конце

---

### Task 1: Заменить уведомление на диалог в `showOptionalUpdateDialog`

**Files:**
- Modify: `app/src/main/java/com/v2ray/ang/ui/MainActivity.kt` (метод `showOptionalUpdateDialog`, строки ~629-649)

**Interfaces:**
- Consumes: `SaqaNetUpdateInfo` (поля `version: String`, `apkUrl: String`)
- Produces: AlertDialog с кнопками "Обновить" и "Позже"

- [ ] **Step 1: Открыть файл и найти метод**

Файл: `app/src/main/java/com/v2ray/ang/ui/MainActivity.kt`
Найти метод `showOptionalUpdateDialog` (около строки 629).

- [ ] **Step 2: Заменить реализацию метода**

Заменить весь метод `showOptionalUpdateDialog` на:

```kotlin
private fun showOptionalUpdateDialog(info: SaqaNetUpdateInfo) {
    AlertDialog.Builder(this)
        .setTitle(getString(R.string.update_new_version_found, info.version))
        .setMessage(getString(R.string.update_optional_message))
        .setPositiveButton(getString(R.string.update_now)) { _, _ ->
            downloadAndInstallApk(info.apkUrl)
        }
        .setNegativeButton(getString(R.string.update_later), null)
        .show()
}
```

- [ ] **Step 3: Убедиться что импорты не нужны**

`AlertDialog` уже импортирован (используется в `showForcedUpdateDialog`). Новые импорты не нужны.

- [ ] **Step 4: Убедиться что `PendingIntent`, `NotificationCompat` не оставлены висящими**

Проверить — если они больше нигде не используются кроме удалённого кода, убрать неиспользуемые импорты. Если используются где-то ещё — оставить.

- [ ] **Step 5: Проверить компиляцию**

```bash
cd D:\SAQANet-Android\V2rayNG
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Ожидание: `BUILD SUCCESSFUL`

- [ ] **Step 6: Коммит и пуш**

```bash
git add app/src/main/java/com/v2ray/ang/ui/MainActivity.kt
git commit -m "fix: show dialog for optional updates instead of notification

System notifications are cleared by foreground-aggressive launchers
(MIUI, One UI) while the app is open. A dismissible AlertDialog is
100% reliable across all Android versions and OEM skins.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
git push origin master
```
