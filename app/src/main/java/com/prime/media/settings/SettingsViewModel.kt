package com.prime.media.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prime.media.Audiofy
import com.prime.media.core.asComposeState
import com.primex.core.Text
import com.primex.preferences.Key
import com.primex.preferences.Preferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


@Immutable
data class Preference<out P>(
    val value: P,
    @JvmField val title: Text,
    val vector: ImageVector? = null,
    @JvmField val summery: Text? = null,
)

typealias Settings = SettingsViewModel.Companion

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: Preferences
) : ViewModel() {

    companion object {
        const val route = "settings"
    }


    val darkUiMode = with(preferences) {
        preferences[Audiofy.NIGHT_MODE].map {
            Preference(
                value = it,
                title = Text("Dark Mode"),
                summery = Text("Click to change the app night/light mode."),
                vector = Icons.Outlined.Lightbulb
            )
        }.asComposeState()
    }

    val font = with(preferences) {
        preferences[Audiofy.FONT_FAMILY].map {
            Preference(
                vector = Icons.Default.TextFields,
                title = Text("Font"),
                summery = Text("Choose font to better reflect your desires."),
                value = it
            )
        }.asComposeState()
    }

    val colorStatusBar = with(preferences) {
        preferences[Audiofy.COLOR_STATUS_BAR].map {
            Preference(
                vector = null,
                title = Text("Color Status Bar"),
                summery = Text("Force color status bar."),
                value = it
            )
        }.asComposeState()
    }

    val hideStatusBar = with(preferences) {
        preferences[Audiofy.HIDE_STATUS_BAR].map {
            Preference(
                value = it,
                title = Text("Hide Status Bar"),
                summery = Text("hide status bar for immersive view"),
                vector = Icons.Outlined.HideImage
            )
        }.asComposeState()
    }

    val forceAccent = with(preferences) {
        preferences[Audiofy.FORCE_COLORIZE].map {
            Preference(
                value = it,
                title = Text("Force Accent Color"),
                summery = Text("Normally the app follows the rule of using 10% accent color. But if this setting is toggled it can make it use  more than 30%")
            )
        }.asComposeState()
    }


    val fontScale = with(preferences) {
        preferences[Audiofy.FONT_SCALE].map {
            Preference(
                value = it,
                title = Text("Font Scale"),
                summery = Text("Zoom in or out the text shown on the screen."),
                vector = Icons.Outlined.ZoomIn
            )
        }.asComposeState()
    }

    fun <S, O> set(key: Key<S, O>, value: O) {
        viewModelScope.launch {
            preferences[key] = value
        }
    }
}

context (Preferences, ViewModel)
        private fun <T> Flow<T>.asComposeState(): State<T> = asComposeState(runBlocking { first() })