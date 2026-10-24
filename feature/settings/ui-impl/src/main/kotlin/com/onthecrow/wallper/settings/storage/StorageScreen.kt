@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.settings.storage

import android.text.format.Formatter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.onthecrow.wallper.settings.ui.R

@Composable
internal fun StorageScreen(
    state: StorageState,
    onEvent: (StorageEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(R.string.storage_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(StorageEvent.OnBackClick) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item { Summary(totalBytes = state.totalBytes) }
            val items = state.items
            if (items != null && items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.storage_empty),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(items.orEmpty(), key = { it.id }) { item ->
                StorageRow(item = item, onOptimiseClick = { onEvent(StorageEvent.OnOptimiseClick(item.id)) })
            }
        }
    }
}

@Composable
private fun Summary(totalBytes: Long?) {
    val context = LocalContext.current
    Text(
        text = totalBytes?.let { stringResource(R.string.settings_storage_summary, Formatter.formatShortFileSize(context, it)) }
            ?: stringResource(R.string.settings_storage_counting),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.storage_explanation),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StorageRow(item: StorageItem, onOptimiseClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        leadingContent = {
            AsyncImage(
                model = item.picturePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        },
        headlineContent = { Text(text = Formatter.formatShortFileSize(context, item.bytes)) },
        supportingContent = { Text(text = stringResource(item.kind.labelRes)) },
        trailingContent = if (item.canOptimise) {
            {
                OutlinedButton(onClick = onOptimiseClick) {
                    Text(text = stringResource(R.string.storage_optimise))
                }
            }
        } else {
            null
        },
    )
}

private val StorageItem.Kind.labelRes: Int
    get() = when (this) {
        StorageItem.Kind.ORIGINAL_VIDEO -> R.string.storage_original_video
        StorageItem.Kind.OPTIMISED_VIDEO -> R.string.storage_optimised_video
        StorageItem.Kind.PICTURE -> R.string.storage_picture
    }

@Preview
@Composable
private fun StorageScreenPreview() {
    StorageScreen(
        state = StorageState(
            items = listOf(
                StorageItem(1, "", StorageItem.Kind.ORIGINAL_VIDEO, 160_000_000),
                StorageItem(2, "", StorageItem.Kind.OPTIMISED_VIDEO, 12_000_000),
                StorageItem(3, "", StorageItem.Kind.PICTURE, 3_000_000),
            ),
        ),
        onEvent = {},
    )
}
