package com.onthecrow.wallper.settings.view

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.settings.ui.R
import timber.log.Timber

/** The author's card, as on Caffeine's main screen: who made the app and where to reach the author. */
@Composable
internal fun SignatureFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val handle = stringResource(R.string.settings_footer_handle)
        val signature = stringResource(R.string.settings_footer_signature)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) {
                    append(signature)
                    append(' ')
                }
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(handle)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Link(R.drawable.ic_telegram, R.string.settings_footer_telegram, TELEGRAM_URL)
            Link(R.drawable.ic_github, R.string.settings_footer_github, GITHUB_URL)
            Link(R.drawable.ic_instagram, R.string.settings_footer_instagram, INSTAGRAM_URL)
        }
        Text(
            text = stringResource(R.string.settings_footer_contact),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Link(@DrawableRes icon: Int, @StringRes name: Int, url: String) {
    val uriHandler = LocalUriHandler.current
    IconButton(
        // A device with nothing to open the link with keeps the screen.
        onClick = { runCatching { uriHandler.openUri(url) }.onFailure { Timber.w(it, "Can't open %s", url) } },
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(name),
            modifier = Modifier.size(32.dp),
        )
    }
}

private const val TELEGRAM_URL = "https://t.me/onthecrow"
private const val GITHUB_URL = "https://github.com/onthecrow"
private const val INSTAGRAM_URL = "https://www.instagram.com/onthecrow"

@Preview(widthDp = 360)
@Composable
private fun SignatureFooterPreview() {
    SignatureFooter()
}
