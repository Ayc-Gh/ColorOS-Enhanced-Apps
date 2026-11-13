package com.op.aod.enhance.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodConfig
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AodConfigStore.read(this)
        setContent {
            MiuixTheme {
                MainScreen(
                    onOpenBrightness = { startActivity(Intent(this, BrightnessActivity::class.java)) },
                    onOpenFeatures = { startActivity(Intent(this, FeaturesActivity::class.java)) },
                    onOpenStatus = { startActivity(Intent(this, RuntimeStatusActivity::class.java)) },
                    onReset = { android.app.AlertDialog.Builder(this).setTitle("恢复 AOD 系统默认")
                        .setMessage("亮度跟随系统，关闭 AOD 增强并恢复系统显示时长。其他模块设置保留。")
                        .setNegativeButton("取消",null).setPositiveButton("恢复"){_,_->AodConfigStore.write(this,AodConfig())}.show() },
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    onOpenBrightness: () -> Unit,
    onOpenFeatures: () -> Unit,
    onOpenStatus: () -> Unit,
    onReset: () -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "AOD 设置",
                color = MiuixTheme.colorScheme.secondaryContainer,
            )
        },
        containerColor = MiuixTheme.colorScheme.secondaryContainer,
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)) {
                Column(Modifier.padding(18.dp)) {
                    Text("AOD")
                    Text("息屏显示 · 亮度、时长与全天能力分别设置")
                }
            }
            ConfigSyncStatusCard()
            Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)) {
                ArrowPreference(
                    title = "AOD亮度设置",
                    summary = "调整初始亮度与运行时倍率",
                    onClick = onOpenBrightness,
                )
                ArrowPreference(
                    title = "AOD功能设置",
                    summary = "系统界面、息屏设置与唤醒行为",
                    onClick = onOpenFeatures,
                )
                ArrowPreference(title = "功能运行状态", summary = "检查新版是否加载，以及各功能是否已触发", onClick = onOpenStatus)
            }
            TextButton(text="恢复 AOD 系统默认",onClick=onReset)
        }
    }
}
