/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.menu.LyricsMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.viewmodels.LyricsManagerViewModel
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.utils.ShowOffsetDialog

private data class LyricsDisplayItem(
    val id: String,
    val title: String,
    val artistsText: String,
    val thumbnailUrl: String?,
    val entity: LyricsEntity,
    val song: Song?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageLyricsScreen(
    navController: NavController,
    viewModel: LyricsManagerViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current

    val rawItems by viewModel.items.collectAsStateWithLifecycle()
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle(initialValue = emptyList())

    val queueMetadataById = remember(queueWindows) {
        queueWindows
            .mapNotNull { it.mediaItem.metadata }
            .associateBy { it.id }
    }

    val items = remember(rawItems, queueMetadataById) {
        rawItems
            .map { item ->
                val song = item.song
                val queued = queueMetadataById[item.entity.id]
                LyricsDisplayItem(
                    id = item.entity.id,
                    title = song?.song?.title
                        ?: queued?.title
                        ?: item.entity.id,
                    artistsText = song?.artists?.joinToString { it.name }
                        ?: queued?.artists?.joinToString { it.name }
                        ?: item.entity.provider,
                    thumbnailUrl = song?.song?.thumbnailUrl ?: queued?.thumbnailUrl,
                    entity = item.entity,
                    song = song,
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    var offsetTarget by remember { mutableStateOf<LyricsDisplayItem?>(null) }

    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    LaunchedEffect(items) {
        selection.removeAll { id -> items.none { it.id == id } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (inSelectMode) pluralStringResource(R.plurals.n_selected, selection.size, selection.size)
                        else stringResource(R.string.manage_downloaded_lyrics),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (inSelectMode) onExitSelectionMode()
                            else navController.navigateUp()
                        },
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (inSelectMode) {
                        IconButton(
                            onClick = {
                                if (selection.size == items.size) selection.clear()
                                else {
                                    selection.clear()
                                    selection.addAll(items.map { it.id })
                                }
                            },
                        ) {
                            Icon(painterResource(R.drawable.select_all), null)
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = selection.isNotEmpty(),
                        ) {
                            Icon(painterResource(R.drawable.delete), null)
                        }
                    } else if (items.isNotEmpty()) {
                        IconButton(onClick = { inSelectMode = true }) {
                            Icon(painterResource(R.drawable.select_all), null)
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.lyrics),
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.no_downloaded_lyrics),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
                    ),
                contentPadding = paddingValues,
            ) {
                item {
                    Text(
                        stringResource(R.string.lyrics_count, items.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(items, key = { it.id }) { item ->
                    LyricsRow(
                        item = item,
                        inSelectMode = inSelectMode,
                        checked = item.id in selection,
                        modifier = Modifier.animateItem(),
                        onToggle = {
                            val id = item.id
                            if (id in selection) {
                                selection.remove(id)
                                if (selection.isEmpty()) inSelectMode = false
                            } else {
                                selection.add(id)
                            }
                        },
                        onLongClick = {
                            if (!inSelectMode) {
                                inSelectMode = true
                                selection.add(item.id)
                            }
                        },
                        onOpenMenu = {
                            menuState.show {
                                LyricsMenu(
                                    lyricsProvider = { item.entity },
                                    songProvider = { item.song?.song },
                                    mediaMetadataProvider = {
                                        item.song?.toMediaMetadata()
                                            ?: queueMetadataById[item.id]
                                            ?: MediaMetadata(
                                                id = item.id,
                                                title = item.title,
                                                artists = listOf(
                                                    MediaMetadata.Artist(
                                                        id = null,
                                                        name = item.artistsText
                                                    )
                                                ),
                                                duration = -1,
                                            )
                                    },
                                    onDismiss = menuState::dismiss,
                                    onShowOffsetDialog = {
                                        menuState.dismiss()
                                        if (item.song != null) {
                                            offsetTarget = item
                                            showOffsetDialog = true
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        DefaultDialog(
            onDismiss = { showDeleteDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_lyrics_confirm, selection.size),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteLyrics(selection.toList())
                        onExitSelectionMode()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showOffsetDialog) {
        DefaultDialog(
            onDismiss = { showOffsetDialog = false; offsetTarget = null },
            content = {
                ShowOffsetDialog(songProvider = { offsetTarget?.song?.song })
            },
            buttons = {
                TextButton(onClick = { showOffsetDialog = false; offsetTarget = null }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LyricsRow(
    item: LyricsDisplayItem,
    inSelectMode: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (inSelectMode) onToggle() else onOpenMenu() },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AnimatedVisibility(visible = inSelectMode) {
            Row {
                Checkbox(checked = checked, onCheckedChange = { onToggle() })
                Spacer(Modifier.width(4.dp))
            }
        }

        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnailUrl.resize(96, 96))
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.lyrics),
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildString {
                    if (item.artistsText != item.entity.provider) {
                        append(item.artistsText)
                    } else {
                        append(stringResource(R.string.not_played_yet))
                    }
                    item.entity.provider.takeIf { it.isNotBlank() }?.let {
                        append(" • ").append(it)
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}