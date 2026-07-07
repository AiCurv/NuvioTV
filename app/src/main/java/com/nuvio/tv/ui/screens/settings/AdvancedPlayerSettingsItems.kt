@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.R
import com.nuvio.tv.data.local.PlayerSettings
import kotlinx.coroutines.launch

// ─── Disk Cache Size Options ────────────────────────────────────────────

@Composable
private fun diskCacheOptions(): List<SettingsPickerOption<Int>> {
    val disabledLabel = stringResource(R.string.disk_cache_disabled)
    return PlayerSettings.DISK_CACHE_SIZE_OPTIONS.map { mb ->
        SettingsPickerOption(
            value = mb,
            title = if (mb == 0) disabledLabel else "$mb MB",
            description = if (mb == 0) stringResource(R.string.video_cache_on_disk_desc) else null
        )
    }
}

// ─── RAM Buffer Size Options ────────────────────────────────────────────

@Composable
private fun ramBufferOptions(): List<SettingsPickerOption<Int>> {
    val defaultLabel = stringResource(R.string.buffer_ram_default)
    return PlayerSettings.BUFFER_RAM_SIZE_OPTIONS.map { mb ->
        SettingsPickerOption(
            value = mb,
            title = if (mb == 0) defaultLabel else "$mb MB",
            description = if (mb == 0) stringResource(R.string.video_buffer_ram_size_desc) else null
        )
    }
}

// ─── Buffer Duration Options ────────────────────────────────────────────

@Composable
private fun bufferDurationOptions(): List<SettingsPickerOption<Int>> {
    val autoLabel = stringResource(R.string.buffer_duration_auto)
    return PlayerSettings.BUFFER_DURATION_OPTIONS.map { ms ->
        SettingsPickerOption(
            value = ms,
            title = when (ms) {
                0 -> autoLabel
                60_000 -> "1 min"
                90_000 -> "1.5 min"
                120_000 -> "2 min"
                300_000 -> "5 min"
                600_000 -> "10 min"
                1_800_000 -> "30 min"
                else -> "${ms / 60_000} min"
            },
            description = if (ms == 0) stringResource(R.string.video_buffer_duration_desc) else null
        )
    }
}

// ─── Advanced Player Settings Items ─────────────────────────────────────

/**
 * Adds the Advanced Player Settings items to a [LazyListScope].
 * These items mirror Cloudstream's caching and buffering logic:
 * - Video Cache on Disk (SimpleCache + LRU evictor)
 * - Video Buffer Size RAM (LoadControl targetBufferBytes)
 * - Video Buffer Duration (LoadControl bufferDurationsMs)
 * - Clear Video & Image Cache (one-tap utility)
 */
fun LazyListScope.advancedPlayerSettingsItems(
    playerSettings: PlayerSettings,
    onSetDiskCacheSizeMb: (Int) -> Unit,
    onSetBufferRamSizeMb: (Int) -> Unit,
    onSetBufferDurationMs: (Int) -> Unit,
    onClearCache: () -> Unit
) {
    item(key = "advanced_disk_cache_header") {
        SettingsDetailHeader(
            title = stringResource(R.string.video_cache_on_disk),
            subtitle = stringResource(R.string.video_cache_on_disk_desc)
        )
    }

    item(key = "advanced_disk_cache_size") {
        var showDialog by remember { mutableStateOf(false) }
        val currentMb = playerSettings.diskCacheSizeMb
        val options = diskCacheOptions()
        val currentLabel = options.find { it.value == currentMb }?.title
            ?: if (currentMb == 0) stringResource(R.string.disk_cache_disabled) else "$currentMb MB"

        SettingsActionRow(
            title = stringResource(R.string.video_cache_on_disk),
            subtitle = stringResource(R.string.video_cache_on_disk_desc),
            value = currentLabel,
            onClick = { showDialog = true }
        )

        if (showDialog) {
            SettingsSingleChoiceDialog(
                title = stringResource(R.string.video_cache_on_disk),
                options = options,
                selectedValue = currentMb,
                onOptionSelected = { onSetDiskCacheSizeMb(it) },
                onDismiss = { showDialog = false }
            )
        }
    }

    item(key = "advanced_ram_buffer_header") {
        SettingsDetailHeader(
            title = stringResource(R.string.video_buffer_ram_size),
            subtitle = stringResource(R.string.video_buffer_ram_size_desc)
        )
    }

    item(key = "advanced_ram_buffer_size") {
        var showDialog by remember { mutableStateOf(false) }
        val currentMb = playerSettings.bufferRamSizeMb
        val options = ramBufferOptions()
        val currentLabel = options.find { it.value == currentMb }?.title
            ?: if (currentMb == 0) stringResource(R.string.buffer_ram_default) else "$currentMb MB"

        SettingsActionRow(
            title = stringResource(R.string.video_buffer_ram_size),
            subtitle = stringResource(R.string.video_buffer_ram_size_desc),
            value = currentLabel,
            onClick = { showDialog = true }
        )

        if (showDialog) {
            SettingsSingleChoiceDialog(
                title = stringResource(R.string.video_buffer_ram_size),
                options = options,
                selectedValue = currentMb,
                onOptionSelected = { onSetBufferRamSizeMb(it) },
                onDismiss = { showDialog = false }
            )
        }
    }

    item(key = "advanced_buffer_duration_header") {
        SettingsDetailHeader(
            title = stringResource(R.string.video_buffer_duration),
            subtitle = stringResource(R.string.video_buffer_duration_desc)
        )
    }

    item(key = "advanced_buffer_duration") {
        var showDialog by remember { mutableStateOf(false) }
        val currentMs = playerSettings.bufferDurationMs
        val options = bufferDurationOptions()
        val currentLabel = options.find { it.value == currentMs }?.title
            ?: if (currentMs == 0) stringResource(R.string.buffer_duration_auto) else "${currentMs / 60_000} min"

        SettingsActionRow(
            title = stringResource(R.string.video_buffer_duration),
            subtitle = stringResource(R.string.video_buffer_duration_desc),
            value = currentLabel,
            onClick = { showDialog = true }
        )

        if (showDialog) {
            SettingsSingleChoiceDialog(
                title = stringResource(R.string.video_buffer_duration),
                options = options,
                selectedValue = currentMs,
                onOptionSelected = { onSetBufferDurationMs(it) },
                onDismiss = { showDialog = false }
            )
        }
    }

    item(key = "advanced_clear_cache_header") {
        SettingsDetailHeader(
            title = stringResource(R.string.clear_video_image_cache),
            subtitle = stringResource(R.string.clear_video_image_cache_desc)
        )
    }

    item(key = "advanced_clear_cache_button") {
        val scope = rememberCoroutineScope()
        SettingsActionRow(
            title = stringResource(R.string.clear_video_image_cache),
            subtitle = stringResource(R.string.clear_video_image_cache_desc),
            onClick = { scope.launch { onClearCache() } }
        )
    }
}
