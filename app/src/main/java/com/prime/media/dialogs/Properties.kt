package com.prime.media.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.prime.media.*
import com.prime.media.R
import com.prime.media.core.ContentPadding
import com.prime.media.core.compose.overlay
import com.prime.media.core.util.DateUtil
import com.prime.media.core.db.Audio
import com.primex.material2.*
import com.primex.material2.dialog.PrimeDialog

@Composable
private fun Property(
    title: String, subtitle: String, icon: ImageVector
) {
    ListTile(secondaryText = {
        Label(
            text = subtitle,
            maxLines = 3,
            modifier = Modifier.padding(start = ContentPadding.normal),
            fontWeight = FontWeight.SemiBold
        )
    },
        centreVertically = true,
        leading = { Icon(imageVector = icon, contentDescription = null) },
        text = {
            Caption(
                text = title,
                modifier = Modifier.padding(start = ContentPadding.normal),
                fontWeight = FontWeight.SemiBold
            )
        })
}

/**
 * A [Dialog] to show the properties of the [Audio] file.
 */
@Composable
@Deprecated("Re-write this")
fun Audio.Properties(
    expanded: Boolean, onDismissRequest: () -> Unit
) {
    val audio = this
    if (expanded) {
        PrimeDialog(
            title = "Properties",
            onDismissRequest = onDismissRequest,
            vectorIcon = Icons.Outlined.Info,
            button2 = stringResource(id = R.string.dismiss) to onDismissRequest,
            topBarBackgroundColor = Theme.colors.overlay,
            topBarContentColor = Theme.colors.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = ContentPadding.normal, vertical = ContentPadding.medium
                )
            ) {
                Property(
                    title = "Title", subtitle = audio.name, icon = Icons.Outlined.Title
                )

                Property(
                    title = "Path", subtitle = audio.data, icon = Icons.Outlined.LocationCity
                )

                Property(
                    title = "Album", subtitle = audio.album, icon = Icons.Outlined.Album
                )

                Property(
                    title = "Artist", subtitle = audio.artist, icon = Icons.Outlined.Person
                )

                Property(
                    title = "Track number",
                    subtitle = "${audio.track}",
                    icon = Icons.Outlined.FormatListNumbered
                )

                Property(
                    title = "Year", subtitle = "${audio.year}", icon = Icons.Outlined.DateRange
                )

                Property(
                    title = "Duration",
                    subtitle = DateUtil.formatAsDuration(audio.duration),
                    icon = Icons.Default.Timer3
                )

                Property(
                    title = "Date Modified",
                    subtitle = DateUtil.formatAsRelativeTimeSpan(audio.dateModified).toString(),
                    icon = Icons.Outlined.Update
                )
            }
        }
    }
}

