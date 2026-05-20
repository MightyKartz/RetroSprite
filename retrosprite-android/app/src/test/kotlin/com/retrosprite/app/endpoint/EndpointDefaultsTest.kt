package com.retrosprite.app.endpoint

import com.retrosprite.app.ui.viewmodel.UiSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointDefaultsTest {

    @Test
    fun `product default port matches RetroArch AI Service default`() {
        assertEquals(4_404, RetroArchEndpointServer.DEFAULT_PORT)
        assertEquals(RetroArchEndpointServer.DEFAULT_PORT, UiSettings().port)
    }
}
