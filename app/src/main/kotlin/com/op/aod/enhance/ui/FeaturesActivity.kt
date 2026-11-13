package com.op.aod.enhance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodInputValidation
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class FeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AodConfigStore.read(this)
        setContent { MiuixTheme { FeaturesScreen() } }
    }
}

@Composable
private fun FeaturesScreen() {
    val config by AodConfigStore.config.collectAsState()
    val context = LocalContext.current
    var advanced by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { SmallTopAppBar(title = "AOD功能设置", color = MiuixTheme.colorScheme.secondaryContainer) },
        containerColor = MiuixTheme.colorScheme.secondaryContainer,
    ) { paddingValues: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
            contentPadding = paddingValues,
            overscrollEffect = null,
        ) {
            item {
                Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)) {
                    Text("常用开关会即时同步；关闭增强后会恢复系统原生值。", Modifier.padding(16.dp))
                }
            }
            item { ConfigSyncStatusCard(Modifier.padding(top = 12.dp, bottom = 12.dp)) }
            item {
                Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)) {
                    SwitchPreference(
                        title = "全天全景息屏",
                        summary = "启用全天全景能力及设置入口；请在系统显示设置中选择全天显示，下一次息屏生效",
                        checked = config.enablePanoramic,
                        onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(enablePanoramic = enabled) } },
                    )
                    ArrowPreference(title="高级：单独显示设置入口",summary=if(config.enablePanoramic)"当前入口已由全天全景提供" else if(config.enableSettingsSupport)"独立入口已开启，仅显示选项" else "独立入口已关闭",onClick={advanced=!advanced})
                    if(advanced){
                        if(config.enablePanoramic)Text("全天全景已包含设置入口，无需额外开启。原有独立入口选择会保留。",Modifier.padding(16.dp))
                        else SwitchPreference(title="仅显示全天设置入口",summary="保留独立控制；单独显示入口不会延长全景息屏时长",checked=config.enableSettingsSupport,onCheckedChange={enabled->AodConfigStore.update(context){it.copy(enableSettingsSupport=enabled)}})
                    }
                    SwitchPreference(
                        title = "AOD单击唤醒屏蔽",
                        summary = "避免 AOD 单击误触导致唤醒；双击仍正常，修改后近实时生效",
                        checked = config.blockSingleClick,
                        onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(blockSingleClick = enabled) } },
                    )
                    SwitchPreference(
                        title = "低光/特殊规则保持AOD显示",
                        summary = "阻止极暗、夜间低光以及当前 ColorOS 的 4 小时特殊规则自动关闭 AOD",
                        checked = config.blockLowLightHide,
                        onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(blockLowLightHide = enabled) } },
                    )
                }
            }
            item {
                Text(
                    text = "AOD 目标显示时长：${durationModeLabel(config.aodDurationMode, config.aodDurationCustomMinutes)}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background),
                ) {
                    for (mode in AodConfigContract.DURATION_MODE_SYSTEM..AodConfigContract.DURATION_MODE_CUSTOM) {
                        RadioButtonPreference(
                            title = if (mode == AodConfigContract.DURATION_MODE_CUSTOM) "自定义" else durationModeLabel(mode, config.aodDurationCustomMinutes),
                            selected = config.aodDurationMode == mode,
                            onClick = { AodConfigStore.update(context) { it.copy(aodDurationMode = mode) } },
                        )
                    }
                }
            }
            if (config.aodDurationMode == AodConfigContract.DURATION_MODE_CUSTOM) {
                item {
                    ValidatedNumberField(
                        value = config.aodDurationCustomMinutes.toString(),
                        label = "自定义分钟数（1–1440）",
                        errorHint = "请输入 1–1440 的整数",
                        isValid = { AodInputValidation.durationMinutes(it) != null },
                        onValidValueChange = { text ->
                            AodInputValidation.durationMinutes(text)?.let { minutes ->
                                AodConfigStore.update(context) { it.copy(aodDurationCustomMinutes = minutes) }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    text = "开关会自动同步，全天能力可在关闭后恢复原生值，无需重启系统界面；重新打开系统息屏设置查看入口，显示策略在下一次息屏使用。安装或升级后仍需重启手机一次。\n\n目标时长只接管全景 AOD 的普通自动超时；来电、解锁、指纹、主动唤醒等其他退出路径仍按系统原逻辑执行。“始终显示”也不会阻止这些退出路径。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item { Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}

private fun durationModeLabel(mode: Int, customMinutes: Int): String = when (mode) {
    AodConfigContract.DURATION_MODE_30_SECONDS -> "30 秒"
    AodConfigContract.DURATION_MODE_1_MINUTE -> "1 分钟"
    AodConfigContract.DURATION_MODE_5_MINUTES -> "5 分钟"
    AodConfigContract.DURATION_MODE_10_MINUTES -> "10 分钟"
    AodConfigContract.DURATION_MODE_30_MINUTES -> "30 分钟"
    AodConfigContract.DURATION_MODE_60_MINUTES -> "60 分钟"
    AodConfigContract.DURATION_MODE_ALWAYS -> "始终显示"
    AodConfigContract.DURATION_MODE_CUSTOM -> "自定义 $customMinutes 分钟"
    else -> "使用系统默认"
}
