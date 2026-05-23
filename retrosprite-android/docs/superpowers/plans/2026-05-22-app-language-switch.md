# App Language Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a player-facing Settings control that switches RetroSprite's app UI between Chinese and English.

**Architecture:** Keep this as an app UI locale setting, separate from GKP/query answer language. Persist the selected UI language in the existing `SettingsStore`, apply it through AndroidX AppCompat per-app locales, and migrate the affected Compose UI text to Android string resources so configuration changes re-resolve localized copy.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, DataStore Preferences, AndroidX AppCompat 1.6.1, Android string resources, JUnit4, Compose UI tests.

---

## Product Decision

Ship the first version as a two-option UI language setting:

- `中文` -> `zh-CN`
- `English` -> `en`

Do not change `QueryPipelineResponseGenerator.defaultLanguage` in this work. That value controls answer/retrieval language and currently defaults to `zh`; switching it before English GKP/template coverage exists would make answers less reliable. A later "回答语言 / Answer language" setting can be planned separately.

Do not expose Android 13 system App Language settings in this first pass. Adding `android:localeConfig` without bidirectional sync from system settings back into `SettingsStore` can make the app's Settings selection disagree with Android Settings. The in-app picker remains the source of truth for MVP.

## File Structure

- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
  - Add `UiAppLanguage`.
  - Add `UiSettings.appLanguage`.
  - Add `SettingsStore.updateAppLanguage`.
- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/settings/UiSettingsStore.kt`
  - Persist `app_language`.
  - Map unknown/missing values to `UiAppLanguage.Chinese`.
- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModel.kt`
  - Expose `applyAppLanguage`.
- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
  - Add a "界面语言 / App language" section near the top of Settings.
  - Use a segmented/two-button control for `中文` and `English`.
- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/MainActivity.kt`
  - Extend `AppCompatActivity`.
  - Collect `settingsStore.settings` and call `AppCompatDelegate.setApplicationLocales(...)` on the main thread when `appLanguage` changes.
- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/navigation/Destination.kt`
  - Replace hardcoded labels with string resource ids.
- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/navigation/RetroSpriteBottomBar.kt`
  - Resolve nav labels via `stringResource`.
- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/RetroSpriteRoot.kt`
  - Resolve current destination label via `stringResource`.
- Modify `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/PreviewStubs.kt`
  - Add fake store support for app language.
- Modify `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt`
  - Add fake store support and a ViewModel test for language updates.
- Modify `retrosprite-android/app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt`
  - Assert the setting renders and can select English in fake dependencies.
- Modify `retrosprite-android/app/src/main/res/values/strings.xml`
  - Add Chinese default strings for app shell and Settings language section.
- Create `retrosprite-android/app/src/main/res/values-en/strings.xml`
  - Add English translations for the same keys.

## Task 1: Settings Model And Persistence

**Files:**
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/settings/UiSettingsStore.kt`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/PreviewStubs.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Extend UI contracts**

In `UiContracts.kt`, add this enum near `UiSpoilerLevel`:

```kotlin
enum class UiAppLanguage(val id: String, val localeTag: String) {
    Chinese("zh", "zh-CN"),
    English("en", "en");

    companion object {
        fun fromId(id: String?): UiAppLanguage =
            values().firstOrNull { it.id == id } ?: Chinese
    }
}
```

Update `UiSettings`:

```kotlin
data class UiSettings(
    val port: Int = 4_404,
    val llmProvider: UiLlmProvider = UiLlmProvider.OpenAI,
    val llmApiKey: String = "",
    val llmBaseUrl: String = UiLlmProvider.OpenAI.defaultBaseUrl,
    val llmModel: String = UiLlmProvider.OpenAI.defaultModel,
    val llmTimeoutSeconds: Int = DEFAULT_LLM_TIMEOUT_SECONDS,
    val llmMaxTokens: Int = DEFAULT_LLM_MAX_TOKENS,
    val spoilerLevel: UiSpoilerLevel = UiSpoilerLevel.Light,
    val appLanguage: UiAppLanguage = UiAppLanguage.Chinese,
)
```

Update `SettingsStore`:

```kotlin
interface SettingsStore {
    val settings: Flow<UiSettings>
    suspend fun updatePort(port: Int)
    suspend fun updateLlmConfig(
        provider: UiLlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String,
        timeoutSeconds: Int = DEFAULT_LLM_TIMEOUT_SECONDS,
        maxTokens: Int = DEFAULT_LLM_MAX_TOKENS,
    )
    suspend fun updateSpoilerLevel(level: UiSpoilerLevel)
    suspend fun updateAppLanguage(language: UiAppLanguage)
}
```

- [ ] **Step 2: Persist app language in DataStore**

In `UiSettingsStore.kt`, import `UiAppLanguage`, add the setting to the mapped snapshot, and add an update method:

```kotlin
UiSettings(
    port = prefs[Keys.PORT] ?: 4_404,
    llmProvider = provider,
    llmApiKey = decryptApiKey(prefs),
    llmBaseUrl = prefs[Keys.LLM_BASE_URL] ?: provider.defaultBaseUrl,
    llmModel = prefs[Keys.LLM_MODEL] ?: provider.defaultModel,
    llmTimeoutSeconds = (prefs[Keys.LLM_TIMEOUT_SECONDS] ?: DEFAULT_LLM_TIMEOUT_SECONDS)
        .coerceIn(MIN_LLM_TIMEOUT_SECONDS, MAX_LLM_TIMEOUT_SECONDS),
    llmMaxTokens = (prefs[Keys.LLM_MAX_TOKENS] ?: DEFAULT_LLM_MAX_TOKENS)
        .coerceIn(MIN_LLM_MAX_TOKENS, MAX_LLM_MAX_TOKENS),
    spoilerLevel = (prefs[Keys.SPOILER_LEVEL] ?: UiSpoilerLevel.Light.id).toSpoiler(),
    appLanguage = UiAppLanguage.fromId(prefs[Keys.APP_LANGUAGE]),
)
```

```kotlin
override suspend fun updateAppLanguage(language: UiAppLanguage) {
    context.uiSettingsDataStore.edit { it[Keys.APP_LANGUAGE] = language.id }
}
```

Add the key:

```kotlin
val APP_LANGUAGE = stringPreferencesKey("app_language")
```

- [ ] **Step 3: Update fake stores**

In both fake `SettingsStore` implementations, add:

```kotlin
override suspend fun updateAppLanguage(language: UiAppLanguage) {
    state.value = state.value.copy(appLanguage = language)
}
```

Use `_settings.update { it.copy(appLanguage = language) }` in `PreviewStubs.kt`, matching that file's current style.

- [ ] **Step 4: Run contract tests**

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
./gradlew testDebugUnitTest --tests 'com.retrosprite.app.ui.screens.settings.SettingsViewModelTest'
```

Expected: compilation succeeds after all `SettingsStore` implementations include `updateAppLanguage`.

## Task 2: Runtime Locale Application

**Files:**
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/MainActivity.kt`

- [ ] **Step 1: Convert the host Activity to AppCompatActivity**

Replace the import and superclass:

```kotlin
import androidx.appcompat.app.AppCompatActivity
```

```kotlin
class MainActivity : AppCompatActivity() {
```

Keep `enableEdgeToEdge()` and `setContent { ... }` as they are; `AppCompatActivity` is still a `ComponentActivity`, so Compose `setContent` remains valid.

- [ ] **Step 2: Apply persisted locale from Compose**

Add imports:

```kotlin
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import com.retrosprite.app.ui.viewmodel.UiAppLanguage
import com.retrosprite.app.ui.viewmodel.UiSettings
```

Inside `setContent`, collect settings and apply only when the selected language changes:

```kotlin
setContent {
    val settings by ServiceLocator.settingsStore.settings.collectAsState(initial = UiSettings())
    LaunchedEffect(settings.appLanguage) {
        applyAppLocale(settings.appLanguage)
    }
    RetroSpriteTheme {
        ProvideUiDependencies(deps = ServiceLocator.uiDependencies) {
            RetroSpriteRoot()
        }
    }
}
```

Add a private helper under `MainActivity`:

```kotlin
private fun applyAppLocale(language: UiAppLanguage) {
    val target = LocaleListCompat.forLanguageTags(language.localeTag)
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != target.toLanguageTags()) {
        AppCompatDelegate.setApplicationLocales(target)
    }
}
```

- [ ] **Step 3: Run app compile**

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
./gradlew :app:compileDebugKotlin
```

Expected: Kotlin compile passes; no Activity inheritance or Compose `setContent` errors.

## Task 3: Localized App Shell Resources

**Files:**
- Modify: `retrosprite-android/app/src/main/res/values/strings.xml`
- Create: `retrosprite-android/app/src/main/res/values-en/strings.xml`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/navigation/Destination.kt`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/navigation/RetroSpriteBottomBar.kt`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/RetroSpriteRoot.kt`

- [ ] **Step 1: Add resource keys**

Update Chinese defaults:

```xml
<resources>
    <string name="app_name">RetroSprite</string>
    <string name="nav_home">首页</string>
    <string name="nav_diagnostics">诊断</string>
    <string name="nav_packs">知识包</string>
    <string name="nav_settings">设置</string>
</resources>
```

Create English resources:

```xml
<resources>
    <string name="app_name">RetroSprite</string>
    <string name="nav_home">Home</string>
    <string name="nav_diagnostics">Diagnostics</string>
    <string name="nav_packs">Packs</string>
    <string name="nav_settings">Settings</string>
</resources>
```

- [ ] **Step 2: Replace destination labels with resource ids**

In `Destination.kt`, import `androidx.annotation.StringRes` and `com.retrosprite.app.R`, then change the constructor:

```kotlin
sealed class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)
```

Update destinations:

```kotlin
data object Home : Destination(
    route = "home",
    labelRes = R.string.nav_home,
    icon = Icons.Filled.Home,
)
```

Apply the same pattern for Diagnostics, Packs, and Settings.

- [ ] **Step 3: Resolve labels in composables**

In `RetroSpriteBottomBar.kt`, add `import androidx.compose.ui.res.stringResource`, then replace `dest.label` usage:

```kotlin
val label = stringResource(dest.labelRes)
Icon(imageVector = dest.icon, contentDescription = label)
Text(text = label, style = MaterialTheme.typography.labelMedium)
```

In `RetroSpriteRoot.kt`, add `import androidx.compose.ui.res.stringResource`, then resolve the current label:

```kotlin
val currentLabel = stringResource(current.labelRes)
```

Replace:

```kotlin
text = "/ ${current.label}",
```

with:

```kotlin
text = "/ $currentLabel",
```

## Task 4: Settings Language Section

**Files:**
- Modify: `retrosprite-android/app/src/main/res/values/strings.xml`
- Modify: `retrosprite-android/app/src/main/res/values-en/strings.xml`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModel.kt`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`

- [ ] **Step 1: Add strings for the section**

Chinese:

```xml
<string name="settings_language_title">界面语言</string>
<string name="settings_language_body">切换 RetroSprite 的应用界面文字。游戏问答语言暂时保持中文。</string>
<string name="settings_language_chinese">中文</string>
<string name="settings_language_english">English</string>
```

English:

```xml
<string name="settings_language_title">App language</string>
<string name="settings_language_body">Switch RetroSprite UI text. Game answers stay Chinese for now.</string>
<string name="settings_language_chinese">中文</string>
<string name="settings_language_english">English</string>
```

- [ ] **Step 2: Add ViewModel event**

In `SettingsViewModel.kt`, import `UiAppLanguage` and add:

```kotlin
fun applyAppLanguage(language: UiAppLanguage) {
    viewModelScope.launch { store.updateAppLanguage(language) }
}
```

- [ ] **Step 3: Thread the callback into SettingsContent**

Add this parameter:

```kotlin
onApplyAppLanguage: (UiAppLanguage) -> Unit,
```

Pass it from `SettingsScreen`:

```kotlin
onApplyAppLanguage = viewModel::applyAppLanguage,
```

Render it immediately after `RetroArchSetupSection`:

```kotlin
LanguageSection(
    language = settings.appLanguage,
    onApply = onApplyAppLanguage,
)
```

- [ ] **Step 4: Add the composable**

Use a compact, Settings-native control instead of a marketing-style card:

```kotlin
@Composable
private fun LanguageSection(
    language: UiAppLanguage,
    onApply: (UiAppLanguage) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_language_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.settings_language_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_language_section"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguageOptionButton(
                    text = stringResource(R.string.settings_language_chinese),
                    selected = language == UiAppLanguage.Chinese,
                    onClick = { onApply(UiAppLanguage.Chinese) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_language_zh"),
                )
                LanguageOptionButton(
                    text = stringResource(R.string.settings_language_english),
                    selected = language == UiAppLanguage.English,
                    onClick = { onApply(UiAppLanguage.English) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_language_en"),
                )
            }
        }
    }
}
```

```kotlin
@Composable
private fun LanguageOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = colors,
        border = if (selected) null else ButtonDefaults.outlinedButtonBorder,
    ) {
        Text(text)
    }
}
```

If `ButtonDefaults.outlinedButtonBorder` is unavailable in the current Material 3 version, use the existing default `OutlinedButton` without the `border` override and only customize selected colors.

## Task 5: Tests

**Files:**
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt`
- Modify: `retrosprite-android/app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt`

- [ ] **Step 1: Add ViewModel test**

Add:

```kotlin
@Test
fun `applyAppLanguage persists selected language`() = runTest(mainDispatcherRule.dispatcher) {
    val overlay = FakeOverlayPermissionProvider(UiOverlayPermissionState(isGranted = true))
    val viewModel = viewModel(overlay)

    viewModel.applyAppLanguage(UiAppLanguage.English)
    advanceUntilIdle()

    val settings = viewModel.settings.first()
    assertEquals(UiAppLanguage.English, settings.appLanguage)
}
```

Add imports:

```kotlin
import com.retrosprite.app.ui.viewmodel.UiAppLanguage
import kotlinx.coroutines.flow.first
```

- [ ] **Step 2: Add Compose smoke coverage**

In `canNavigateThroughPlayerTabsAndAdvancedDiagnostics`, after opening Settings, assert and click the language section:

```kotlin
composeRule.onNodeWithTag("settings_language_section")
    .performScrollTo()
    .assertIsDisplayed()
composeRule.onNodeWithTag("settings_language_en")
    .performScrollTo()
    .performClick()
```

If using fake dependencies, also assert the fake `settingsStore` changed by exposing a recording fake for this test:

```kotlin
val settingsStore = RecordingSettingsStore(UiSettings())
setRoot(deps = previewDeps().copy(settingsStore = settingsStore))
composeRule.onNodeWithText("设置").performClick()
composeRule.onNodeWithTag("settings_language_en").performScrollTo().performClick()
composeRule.waitUntil(timeoutMillis = 5_000) {
    settingsStore.state.value.appLanguage == UiAppLanguage.English
}
```

- [ ] **Step 3: Run targeted tests**

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
./gradlew testDebugUnitTest --tests 'com.retrosprite.app.ui.screens.settings.SettingsViewModelTest'
```

Expected: ViewModel language persistence test passes.

Run on an emulator or device:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
./gradlew connectedDebugAndroidTest --tests 'com.retrosprite.app.ui.RetroSpriteAppSmokeTest'
```

Expected: the Settings language section renders and selecting English updates the fake settings state.

## Task 6: Manual QA

**Files:**
- No code changes.

- [ ] **Step 1: Install and open the app**

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
./gradlew installDebug
```

Open RetroSprite on the target device.

- [ ] **Step 2: Verify default Chinese**

Expected:

- Bottom nav shows `首页 / 知识包 / 设置`.
- Settings shows `界面语言`.
- `中文` appears selected.

- [ ] **Step 3: Switch to English**

Tap Settings -> `English`.

Expected:

- The Activity may recreate once.
- Bottom nav changes to `Home / Packs / Settings`.
- Settings language section changes to `App language`.
- The answer/retrieval behavior remains unchanged; sample GKP answers can still be Chinese.

- [ ] **Step 4: Relaunch persistence check**

Force-close and reopen the app.

Expected:

- English remains selected.
- No crash during endpoint startup.
- The local RetroArch endpoint still starts on the configured port.

## Follow-Up Scope

After the MVP lands, run a separate localization pass for the rest of the player-facing UI. Candidate files:

- `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/screens/home/HomeScreen.kt`
- `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/screens/packs/PacksScreen.kt`
- `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/screens/diagnostics/DiagnosticsScreen.kt`
- `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/integration/*Provider.kt` for user-visible state messages
- `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/overlay/*` only for app-facing fallback/error messages; keep HUD status labels short and mostly uppercase

This follow-up should convert hardcoded text to resources screen-by-screen and add English strings in the same commit as each screen migration.
