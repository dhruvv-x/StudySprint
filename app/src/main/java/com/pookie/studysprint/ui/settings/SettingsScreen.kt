package com.pookie.studysprint.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pookie.studysprint.data.settings.ThemeMode
import com.pookie.studysprint.ui.AppViewModelProvider
import com.pookie.studysprint.ui.components.AppCard
import com.pookie.studysprint.ui.components.SectionHeader
import com.pookie.studysprint.ui.theme.Radius
import com.pookie.studysprint.ui.theme.Spacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    top = Spacing.sm,
                    bottom = Spacing.xxl
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionHeader(title = "Session")
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                        SettingRow(
                            title = "Auto-advance",
                            subtitle = "Move to the next segment as soon as the planned time ends. When off, the timer keeps running as overrun.",
                            trailing = {
                                Switch(
                                    checked = settings.autoAdvance,
                                    onCheckedChange = viewModel::setAutoAdvance,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Auto-advance"
                                    }
                                )
                            }
                        )
                        SettingRow(
                            title = "Break length",
                            subtitle = "Default break between tasks in a new session.",
                            trailing = {
                                MinutesStepper(
                                    value = settings.breakMinutes,
                                    onValueChange = viewModel::setBreakMinutes,
                                    min = SettingsViewModel.MIN_BREAK,
                                    max = SettingsViewModel.MAX_BREAK,
                                    step = 1
                                )
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionHeader(title = "Goal")
                AppCard {
                    SettingRow(
                        title = "Daily goal",
                        subtitle = "Used to measure today's progress on Home.",
                        trailing = {
                            MinutesStepper(
                                value = settings.dailyGoalMinutes,
                                onValueChange = viewModel::setDailyGoalMinutes,
                                min = SettingsViewModel.MIN_GOAL,
                                max = SettingsViewModel.MAX_GOAL,
                                step = 15
                            )
                        }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionHeader(title = "Appearance")
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ThemeChip("System", ThemeMode.SYSTEM, settings.themeMode, viewModel::setThemeMode)
                            ThemeChip("Light", ThemeMode.LIGHT, settings.themeMode, viewModel::setThemeMode)
                            ThemeChip("Dark", ThemeMode.DARK, settings.themeMode, viewModel::setThemeMode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) }
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}

@Composable
private fun MinutesStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    step: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(Radius.control)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.control),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onValueChange((value - step).coerceAtLeast(min)) },
            enabled = value > min
        ) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text(
            text = "$value min",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp)
        )
        IconButton(
            onClick = { onValueChange((value + step).coerceAtMost(max)) },
            enabled = value < max
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase")
        }
    }
}