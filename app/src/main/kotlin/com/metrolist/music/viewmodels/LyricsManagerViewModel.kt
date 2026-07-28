/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.lyrics.LyricsTranslationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsItem(
    val entity: LyricsEntity,
    val song: Song?,
)

@HiltViewModel
class LyricsManagerViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val lyricsHelper: LyricsHelper,
) : ViewModel() {

    val items: StateFlow<List<LyricsItem>> =
        database.allLyrics()
            .map { entities ->
                entities.filter {
                    it.lyrics.isNotBlank() && it.lyrics != LyricsEntity.LYRICS_NOT_FOUND
                }
            }
            .map { entities ->
                val songsById = database.getSongsByIds(entities.map { it.id })
                    .associateBy { it.id }
                entities.map { LyricsItem(it, songsById[it.id]) }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteLyrics(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            clearInMemoryCaches()
            database.query { deleteLyricsByIds(ids) }
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            clearInMemoryCaches()
            database.query { deleteAllLyrics() }
        }
    }

    private suspend fun clearInMemoryCaches() {
        lyricsHelper.clearCache()
        LyricsTranslationHelper.clearTranslationCache()
    }
}