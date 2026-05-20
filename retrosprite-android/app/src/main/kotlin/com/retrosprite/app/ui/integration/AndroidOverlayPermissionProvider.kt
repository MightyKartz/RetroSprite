package com.retrosprite.app.ui.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.retrosprite.app.ui.viewmodel.OverlayPermissionProvider
import com.retrosprite.app.ui.viewmodel.UiOverlayPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidOverlayPermissionProvider(
    context: Context,
) : OverlayPermissionProvider {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(readState())
    override val state: StateFlow<UiOverlayPermissionState> = _state.asStateFlow()

    override suspend fun refresh() {
        _state.value = readState()
    }

    override suspend fun openSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        refresh()
    }

    private fun readState(): UiOverlayPermissionState {
        val granted = Settings.canDrawOverlays(appContext)
        return UiOverlayPermissionState(
            isGranted = granted,
            message = if (granted) {
                "已允许游戏内语音 overlay。按 RetroArch AI Service 热键时会显示收音波形。"
            } else {
                "需要授权“显示在其他应用上层”，RetroSprite 才能在 RetroArch 画面右上角显示语音波形。"
            },
        )
    }
}
