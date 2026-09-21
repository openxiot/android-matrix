package cc.openxiot.wematrix.ui.profile

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
 *
 * 选中态用「卡片底色 + 右侧打钩」表达，不再画左边的单选圆点（两者并存是重复表达）。
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
                .padding(top = 8.dp)
        ) {
            listOf(AppLocale.SYSTEM, AppLocale.ZH, AppLocale.EN).forEach { tag ->
                LanguageCard(
                    tag = tag,
                    selected = selected == tag,
                    onSelect = { switchTo(context, activity, tag) }
                )
            }
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

/**
 * 一张语言卡。整张可点。
 *
 * 用 `selectable` 而不是项目里更常见的 `clickable`：原先左边的单选圆点会向 TalkBack
 * 播报「已选中」，换成打钩图标后（图标本身 `contentDescription = null`，是装饰）那条
 * 信息就没地方说了 —— `selectable` 把选中态挂在**整行**的语义上，读屏照样念得出，
 * 也就不必为此新造一条文案。
 *
 * 底色与文字/图标的颜色交给 `cardColors` 成套给：`Icon` 不写 `tint` 时取
 * `LocalContentColor`，而 `Card` 会按容器色把它设成对应的 `onXxxContainer`，
 * 选中与未选中两态的对比度都不用人盯。
 */
@Composable
private fun LanguageCard(
    tag: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(languageLabelRes(tag)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                // 占位，免得选中与未选中的文字左边界跳一下（先例见 SpaceTreeScreen）
                Spacer(Modifier.width(24.dp))
            }
        }
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
