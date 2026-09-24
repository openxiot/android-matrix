package cc.openxiot.matrix.ui.profile

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.openxiot.matrix.APP_LANGUAGES
import cc.openxiot.matrix.AppLanguage
import cc.openxiot.matrix.AppLocale
import cc.openxiot.matrix.R
import cc.openxiot.matrix.appLanguage

/**
 * 语言设置。65 行：钉在最上面的「跟随系统」+ 64 种可选语言，可搜索。
 *
 * 为什么是二级页而不是对话框：切换要走 `recreate()`，而对话框挂在当前组合里，重建会把
 * 它连根拔掉 —— 用户看到的是「点一下闪一下又回到我页」。二级页则因为导航栈存在
 * saveable 状态里，重建后仍停在原地，视觉上只是文案变了。
 *
 * 选中态用「卡片底色 + 右侧打钩」表达，不再画左边的单选圆点（两者并存是重复表达）。
 *
 * 64 行必须用 `LazyColumn`：`Column` 会一次性组合全部 64 张卡（每张带 Card 的
 * Surface + 语义节点），在低端机上滚起来会掉帧。项目里所有长列表都已是 LazyColumn。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val selected = AppLocale.current

    // 用 rememberSaveable：切换语言会 recreate() 整个 Activity，用 remember 的话
    // 用户搜了「日本」选中后，列表会跳回全量 —— 明明只是想切个语言，却丢了搜索上下文。
    var query by rememberSaveable { mutableStateOf("") }

    val matches = remember(query) { filterLanguages(query) }

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
            SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // 「跟随系统」不参与过滤，永远钉在第一行：它是默认值，也是用户
                // 搜不到想要的语言时唯一正确的退路，不能被搜索藏起来。
                item(key = AppLocale.SYSTEM) {
                    LanguageCard(
                        title = stringResource(R.string.profile_language_system),
                        selected = selected == AppLocale.SYSTEM,
                        onSelect = { switchTo(context, activity, AppLocale.SYSTEM) }
                    )
                }
                items(matches, key = { it.tag }) { lang ->
                    LanguageCard(
                        title = lang.endonym,
                        selected = selected == lang.tag,
                        onSelect = { switchTo(context, activity, lang.tag) }
                    )
                }
                if (matches.isEmpty()) {
                    item(key = "__no_match__") {
                        Text(
                            text = stringResource(R.string.profile_language_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                }
                // 底部留白：最后一张卡贴着导航栏不好点
                item(key = "__bottom__") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * 按自称、英文名、tag 三路匹配，不区分大小写。
 *
 * 三路都要，各自解决一类真实诉求：`日本語`（列表里看到的名字）、
 * `Japan`（知道是哪个国家但记不住自称怎么拼）、`ja`（开发/测试时按代码写）。
 *
 * 中文用户输「日本」也能命中 —— 靠的是**自称的子串**（`日本語` 含 `日本`），
 * 不是因为我们有中文语言名表。这是运气好：汉字圈的语言名恰好含中文写法。
 * 输「德国」「法语」则命中不了（`Deutsch` / `Français` 都不含），有意如此 ——
 * 加一份中文名表等于把 64 个语言名再翻一遍，而三路匹配已覆盖绝大多数情况。
 */
internal fun filterLanguages(query: String): List<AppLanguage> {
    val q = query.trim()
    if (q.isEmpty()) return APP_LANGUAGES
    return APP_LANGUAGES.filter {
        it.endonym.contains(q, ignoreCase = true) ||
            it.englishName.contains(q, ignoreCase = true) ||
            it.tag.contains(q, ignoreCase = true)
    }
}

/**
 * 搜索框。项目里此前没有任何搜索/过滤列表，这是第一个 —— 骨架沿用既有的
 * `OutlinedTextField` 写法（见 `ui/components/CommonComponents.kt` 的 `InputDialog`）。
 *
 * `singleLine = true` 必须开：否则回车换行会把 48dp 的框撑高，列表被顶下去。
 * 清除按钮只在有内容时出现（空框上放个 ✕ 是死控件）。
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.profile_language_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.profile_language_search_clear)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
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
 *
 * [title] 是调用方给的**已解析字符串**而不是资源 id：语言名来自数据表（不是资源），
 * 只有「跟随系统」那一行是资源。传资源 id 的话数据表那 64 条就没法走这里了。
 */
@Composable
private fun LanguageCard(
    title: String,
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
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                // 自称可能很长（`Nederlands (België)`、`Français (Canada)`），
                // 极窄屏上要省略号而不是换行把卡片撑成两行
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/**
 * 「我」页那张卡副标题用的语言名：选过的语言显示**自称**，跟随系统显示「跟随系统」。
 *
 * 数据表里查不到（即 SYSTEM，或 prefs 里存了个已下线的 tag）就退回后者 ——
 * 副标题绝不能空白。
 */
@Composable
fun languageLabel(tag: String): String =
    appLanguage(tag)?.endonym ?: stringResource(R.string.profile_language_system)
