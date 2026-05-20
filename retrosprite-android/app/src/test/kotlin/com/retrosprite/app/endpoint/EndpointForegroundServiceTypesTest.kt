package com.retrosprite.app.endpoint

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointForegroundServiceTypesTest {

    @Test
    fun `uses data sync only when microphone permission is missing`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            EndpointForegroundServiceTypes.forPermissionState(hasRecordAudioPermission = false),
        )
    }

    @Test
    fun `adds microphone type when microphone permission is granted`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            EndpointForegroundServiceTypes.forPermissionState(hasRecordAudioPermission = true),
        )
    }
}
