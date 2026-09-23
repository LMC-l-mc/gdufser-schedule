package com.gdufs.schedule.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.ThemeMode
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

/**
 * 外观设置页:
 * 主题模式(跟随系统/浅色/深色)、动态颜色开关,
 * 以及课程卡片样式(圆角/间距/透明度/备注行数)与课表样式(行高/显示开关)。
 * 所有修改立即经 [AppearanceViewModel] 写库持久化,并通过 Flow 即时生效于今日页与周课表页。
 */
@Composable
fun AppearanceScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val viewModel: AppearanceViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        SectionTitle(stringResource(R.string.appearance_section_theme))

        // 主题模式:跟随系统 / 浅色 / 深色
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.appearance_theme_mode),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            listOf(
                ThemeMode.SYSTEM to R.string.appearance_theme_system,
                ThemeMode.LIGHT to R.string.appearance_theme_light,
                ThemeMode.DARK to R.string.appearance_theme_dark,
            ).forEach { (mode, labelRes) ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
        HorizontalDivider()

        // 动态颜色:仅 Android 12+ 可用
        switchRow(
            title = stringResource(R.string.appearance_dynamic_color),
            supporting = stringResource(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    R.string.appearance_dynamic_color_desc
                } else {
                    R.string.appearance_dynamic_color_unsupported
                }
            ),
            checked = settings.dynamicColorEnabled,
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onChange = { viewModel.setDynamicColorEnabled(it) },
        )

        SectionTitle(stringResource(R.string.appearance_section_timetable))

        sliderRow(
            label = stringResource(R.string.appearance_row_height),
            valueText = stringResource(R.string.appearance_dp_format, settings.timetableRowHeight.roundToInt()),
            value = settings.timetableRowHeight,
            range = 60f..120f,
            steps = 11,
            onValueChange = { value -> viewModel.updateAppearance { current -> current.copy(timetableRowHeight = value) } },
        )

        switchRow(
            title = stringResource(R.string.appearance_show_teacher),
            supporting = null,
            checked = settings.showTeacher,
            enabled = true,
            onChange = { value -> viewModel.updateAppearance { current -> current.copy(showTeacher = value) } },
        )
        switchRow(
            title = stringResource(R.string.appearance_show_location),
            supporting = null,
            checked = settings.showLocation,
            enabled = true,
            onChange = { value -> viewModel.updateAppearance { current -> current.copy(showLocation = value) } },
        )
        switchRow(
            title = stringResource(R.string.appearance_show_time),
            supporting = null,
            checked = settings.showTime,
            enabled = true,
            onChange = { value -> viewModel.updateAppearance { current -> current.copy(showTime = value) } },
        )

        SectionTitle(stringResource(R.string.appearance_section_card))

        sliderRow(
            label = stringResource(R.string.appearance_card_corner_radius),
            valueText = stringResource(R.string.appearance_dp_format, settings.cardCornerRadius.roundToInt()),
            value = settings.cardCornerRadius,
            range = 0f..24f,
            steps = 11,
            onValueChange = { value -> viewModel.updateAppearance { current -> current.copy(cardCornerRadius = value) } },
        )
        sliderRow(
            label = stringResource(R.string.appearance_card_spacing),
            valueText = stringResource(R.string.appearance_dp_format, settings.cardSpacing.roundToInt()),
            value = settings.cardSpacing,
            range = 0f..12f,
            steps = 11,
            onValueChange = { value -> viewModel.updateAppearance { current -> current.copy(cardSpacing = value) } },
        )
        sliderRow(
            label = stringResource(R.string.appearance_card_opacity),
            valueText = stringResource(R.string.appearance_percent_format, settings.cardOpacity),
            value = settings.cardOpacity.toFloat(),
            range = 5f..100f,
            steps = 94,
            onValueChange = { value -> viewModel.updateAppearance { current -> current.copy(cardOpacity = value.roundToInt()) } },
        )

        // 备注预览行数:一行 / 两行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.appearance_remark_lines),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            listOf(
                1 to R.string.appearance_remark_lines_one,
                2 to R.string.appearance_remark_lines_two,
            ).forEach { (lines, labelRes) ->
                FilterChip(
                    selected = settings.remarkPreviewLines == lines,
                    onClick = { viewModel.updateAppearance { it.copy(remarkPreviewLines = lines) } },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** 小节标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** 开关行(带标题与可选说明) */
@Composable
private fun switchRow(
    title: String,
    supporting: String?,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, enabled = enabled, role = Role.Switch, onClick = { onChange(!checked) })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!supporting.isNullOrBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** 滑杆行(标题 + 当前值 + 滑杆) */
@Composable
private fun sliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}