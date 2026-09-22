package cc.openxiot.wematrix.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * 布局方向符号：LTR 是 `+1`，RTL 是 `-1`。
 *
 * 用途是把**物理坐标**换算成**语义方向**。Compose 在 RTL 下会自动镜像的东西很有限：
 * `Row`/`Column` 的子项顺序、`start`/`end` 内边距、`Alignment.CenterStart`、
 * `Icons.AutoMirrored.*` —— 这些不用管。但下面这些是**物理量，不镜像**：
 * `detectHorizontalDragGestures` 给的 `dragAmount`、`graphicsLayer` 的 `translationX`、
 * 以及一切手写的 `x >= 0` / `x > width / 2f` 判定。
 *
 * ⚠️ 最容易踩的一条：**`Modifier.offset` 本身是镜像的**。它是 `rtlAware = true`
 * （`foundation-layout` 的 `OffsetPxElement`），内部走 `placeRelativeWithLayer`，
 * RTL 下实际落在 `parentWidth - width - x`。所以**用物理像素做位移必须写 `absoluteOffset`**
 * （`rtlAware = false`，落在物理 x），不能用 `offset`。两者搞混正是滑动行那类 bug 的来源：
 * 拿物理 `dragAmount` 喂给镜像的 `offset`，阿拉伯语下卡片会朝手指的**反方向**滑 ——
 * 而且因为背景 `Row` 同时也在镜像，**「露出的那一侧」与「执行的那个动作」当时仍然是对上的**，
 * 于是它不会表现成「删错了」，只表现成「卡片不跟手」，很容易被当成手感问题放过去。
 * LTR 下两者逐像素等价（`placeRelative` 在 LTR 就是 `place`），所以这个改动对 LTR 是零风险。
 *
 * 两者混用就会出现「**画面镜像了、判定没镜像**」：卡片被往左拖，露出的是「删除」背景，
 * 执行的却是「改名」；而且只在 RTL 语言下才错，LTR 下跑测试永远全绿。
 *
 * 所以凡是要按「用户往哪边划」做判断的地方，都乘上这个符号，让**物理量进、语义量出**：
 *
 * ```
 * val dirSign = dirSign(LocalLayoutDirection.current)
 * val isRightSwipe by remember(dirSign) { derivedStateOf { offsetX.value * dirSign >= 0f } }
 * ```
 *
 * 取 `+1` 时算式**逐字不变**，LTR 行为零风险 —— 这正是它比 `if (rtl) -x else x` 好的地方：
 * 后者等于把每个算式抄两遍，将来改一处必漏另一处。
 *
 * ⚠️ 别忘了 `pointerInput` 也要带上 `dirSign` 做 key，否则方向变了而指针手势还活着时，
 * 闭包里捕获到的仍是旧符号。
 */
internal fun dirSign(direction: LayoutDirection): Float =
    if (direction == LayoutDirection.Rtl) -1f else 1f

/**
 * 物理左右**靠重排子项**来实现，而不是固定布局方向。
 *
 * 背景：看板的半宽卡带一个**持久化到服务端**的 `side`（`LEFT`/`RIGHT`）。它指的是
 * 「这张卡占这一行的**物理**左半格还是右半格」，与 web 看板共用同一份数据；排布算法
 * （`DashboardLayout`）与长按命中判定（`cellCenterPx`：第 0 格 = 左 1/4、第 1 格 = 右 3/4）
 * 也全都是物理坐标。所以在 RTL 下渲染**不能**跟着镜像 —— 否则同一份已保存的布局在阿拉伯语
 * 下会左右翻转：与 web 上看到的不是同一个排版，用户切一次语言自己的排版就变了，抽走一张
 * 半宽卡时该留白的那半格也会跑到另一边去。
 *
 * 做法：RTL 下把子项顺序倒过来。`Row` 在 RTL 下从右往左排，于是「倒序」正好抵消镜像，
 * 落点仍是物理原位。**LTR 下这两个函数都是恒等变换**，算出的子项顺序与改造前逐字相同。
 *
 * 为什么不用 `CompositionLocalProvider(LocalLayoutDirection provides Ltr)` 把整块钉成 LTR：
 * 那会连**卡片里的文字**一起钉死 —— 阿拉伯语的正文会变成左对齐、标点跑到错的一侧。
 * 这里只调整「哪一格摆哪边」，格子里内容的书写方向照旧随语言。
 */

/** 并排两格：把数据序（第 0 张 = 物理左）换成 `Row` 该吃的摆放序。 */
internal fun <T> physicalOrder(items: List<T>, direction: LayoutDirection): List<T> =
    if (direction == LayoutDirection.Rtl) items.asReversed() else items

/**
 * 落单一格：`Row` 的第 0 个子项在 LTR 下落在物理左、RTL 下落在物理右，
 * 所以「该不该把这一格排在前面」= 它靠右 与 当前是 RTL **同真同假**。
 */
internal fun cellComesFirst(onRight: Boolean, direction: LayoutDirection): Boolean =
    onRight == (direction == LayoutDirection.Rtl)

/**
 * 把一个**方向性图标**在 RTL 下水平翻转。LTR 下 `scaleX = 1f`，与不写这个 modifier 等同。
 *
 * 为什么不用 `Icons.AutoMirrored.*`（那是 Compose 的标准做法）：**`ChevronRight` 没有
 * AutoMirrored 版本**。`material-icons-extended` 的 automirrored 包里没有它，只有
 * `KeyboardArrowRight`（带一道横杠的箭头）与 `ArrowForwardIos`（比 chevron 高近一倍）。
 * 换成前者会改字形、换后者会改大小 —— 而本项目 16 处用的都是 chevron。为了 RTL 而把
 * 16 个图标换成另一个字形，是夹带了一次与多语言无关的视觉改动；而在 LTR 的六十多种语言下
 * 它们本来是对的。所以保住字形、只补上翻转。
 *
 * 只作用于**方向性**图标（chevron、箭头）。对关于竖轴对称的图形（如 `ExpandMore`）
 * 翻转是恒等变换，用了也无害 —— `SpaceTreeScreen` 里「展开/收起」共用一处 Icon，
 * 正好省掉按状态分支判断。
 */
@Composable
internal fun Modifier.mirrorInRtl(): Modifier =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) scale(scaleX = -1f, scaleY = 1f) else this
