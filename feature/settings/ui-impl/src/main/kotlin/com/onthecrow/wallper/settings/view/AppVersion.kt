package com.onthecrow.wallper.settings.view

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.pm.PackageInfoCompat
import com.onthecrow.wallper.settings.ui.R

/** The app's version and build, as the report names them: under the author's card. */
@Composable
internal fun AppVersion(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Nothing to show where the package can't be read, like Android Studio's previews.
    val version = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrNull()
    } ?: return
    Text(
        text = stringResource(R.string.settings_version, version),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Preview(widthDp = 360)
@Composable
private fun AppVersionPreview() {
    AppVersion()
}
