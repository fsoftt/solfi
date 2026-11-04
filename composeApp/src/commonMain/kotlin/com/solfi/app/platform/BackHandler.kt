package com.melodia.app.platform

import androidx.compose.runtime.Composable

/** System back gesture/button handling (Android); no-op on other platforms. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
