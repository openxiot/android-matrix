package cc.openxiot.wematrix.ui.profile

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.AppLocale
import cc.openxiot.wematrix.R

/**
 * 语言设置。三选一：跟随系统 / 中文 / English。
 *
 * 为什么是二级页而不是对话框：切换要走 `recreate()`，而对话框挂在当前组合里，重建会把
 * 它连根拔掉 —— 用户看到的是「点一下闪一下又回到我页」。二级页则因为导航栈存在
 * saveable 状态里，重建后仍停在原地，视觉上只是文案变了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val selected = AppLocale.current

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_language_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LanguageRow(AppLocale.SYSTEM, selected, context, activity)
            LanguageRow(AppLocale.ZH, selected, context, activity)
            LanguageRow(AppLocale.EN, selected, context, activity)
        }
    }
}

/**
 * 语言标签的资源 id。语言页三行与「我」页那张卡的副标题共用一处，
 * 免得将来加语言时漏改一边。
 */
@StringRes
fun languageLabelRes(tag: String): Int = when (tag) {
    AppLocale.ZH -> R.string.profile_language_zh
    AppLocale.EN -> R.string.profile_language_en
    else -> R.string.profile_language_system
}

@Composable
private fun LanguageRow(
    tag: String,
    selected: String,
    context: Context,
    activity: Activity?
) {
    Row(
        // 整行可点，不是只有那个小圆点 —— 先例见 OrganizationDetailScreen 的角色选择行
        modifier = Modifier
            .fillMaxWidth()
            .clickable { switchTo(context, activity, tag) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected == tag,
            onClick = { switchTo(context, activity, tag) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(languageLabelRes(tag)),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * 顺序不能反：先落盘 —— `edit {}` 对内存的写是同步的，紧接着 recreate 出来的新
 * `attachBaseContext` 才读得到。
 *
 * 「切回跟随系统」也必须 recreate：那不是换一个 locale，而是要把 locale wrapper **摘掉**。
 */
private fun switchTo(context: Context, activity: Activity?, tag: String) {
    if (AppLocale.current == tag) return
    AppLocale.set(context, tag)
    activity?.recreate()
}
