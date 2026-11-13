package io.github.neoruaa.glasmio.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/** Optional host-owned shared title; ordinary pages keep their existing drawing. */
val LocalPageTitleDecoration = staticCompositionLocalOf<@Composable (String, TextStyle, Float) -> Modifier> {
    { _, _, _ -> Modifier }
}
