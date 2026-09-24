package cc.openxiot.matrix.ui.home

import cc.openxiot.matrix.data.api.MobileDashboardWidget

/**
 * 移动端看板的**排布规则**（纯 Kotlin，不碰 Compose，可被 JVM 单测覆盖）。
 *
 * ## 规则（唯一真源，三端逐字一致：这里 / 后端 `MobileDashboardWidgetSide` / iPhone）
 *
 * - `FULL` → 独占一行；
 * - `HALF` + `LEFT` → 另起一行、占左半格（右边没别的卡就是**空的**）；
 * - `HALF` + `RIGHT` → 当前行右半格空着就填进去；否则另起一行、占右半格（左半格**空着**）。
 *
 * 「占哪半格」写在卡自己的 `side` 上。**没写**的按 [effectiveSides] 那台状态机推：从左往右扫，
 * 显式 `side` 的卡用自己的；`null` 卡则「当前行右半格空着 → `RIGHT`，否则 `LEFT`」；`FULL`
 * 关掉当前行。全 `null` 时退化成 `LEFT,RIGHT,LEFT,RIGHT…` —— 就是改造前「连续两张 HALF 并排」
 * 的老样子，所以存量布局不需要迁移、长相不变。
 *
 * ## 为什么要有这一层
 *
 * 用户报的「拖动时横向补位」根因在契约：半宽卡占左还是占右原来是**按数组顺序贪心配**出来的，
 * 抽走一张，同伴必然被重新配对、从右半格滑到左半格 —— 而「左半格空着、右半格还有卡」这个状态
 * 根本写不出来。现在每张卡自己记 `side`，排布只做一件事：**按 `side` 把卡摆好**，
 * 谁都不会为了补上空出来的半格而左右挪动。
 *
 * ## 一条不变量（拖动期间不横向让位的根据）
 *
 * **草稿里每张 `HALF` 卡都必须带着显式 `side`**（[deriveSides] 就是物化它的函数，进编辑态时
 * 对草稿与基线各跑一次）。理由：显式 `side` 的卡，其有效半格与**它前面有哪些卡无关** ——
 * 状态机只在 `null` 卡上才依赖历史（`rightFree`）。于是「插入 / 抽走别的卡」在数学上不可能
 * 改变任何别的卡的有效半格：纵向让位、横向一格不动。反过来，只要还有 `null` 卡，拖动松手时
 * 同伴就会被重新推导、横着翻面（这正是这次要修的 bug）。
 *
 * 排布结果**一行只装真卡**，没有「空格子」这种 cell：单格行的左右留白由那一格自己的 `side`
 * 在渲染处画（靠左 = 半宽；靠右 = 左边一个 `Spacer`）。
 */

// ===== 排布 =====

/**
 * 把卡按有效半格排成行：整宽卡独占一行，半宽卡两张一行（左卡在前），落单的半宽卡也占一行
 * —— 它是靠左还是靠右，由它自己的 `side` 说了算。
 *
 * 成行的判据只有一条：**一张有效半格为 `LEFT` 的卡，紧跟着一张有效半格为 `RIGHT` 的卡**。
 * 这恰好就是上面那条规则：`LEFT` 开一行并空出右半格，紧跟着的 `RIGHT` 正好填进去；被整宽卡
 * 打断、或前一张是 `RIGHT`（行已关）时给出的 `RIGHT`，前面没有开着的行，自然独占一格靠右。
 */
fun <T> pack(items: List<T>, sizeOf: (T) -> String?, sideOf: (T) -> String?): List<List<T>> {
    val sides = effectiveSides(items, sizeOf, sideOf)
    val rows = mutableListOf<List<T>>()
    var i = 0
    while (i < items.size) {
        // 只有 LEFT 会开一行、等着别人来填右半格；RIGHT 与整宽（null）都自己成一格
        if (sides[i] != DashboardTypes.SIDE_LEFT) {
            rows.add(listOf(items[i]))
            i += 1
            continue
        }
        val next = i + 1
        if (next < items.size && sides[next] == DashboardTypes.SIDE_RIGHT) {
            rows.add(listOf(items[i], items[next]))
            i += 2
        } else {
            rows.add(listOf(items[i])) // 右边那半格空着
            i += 1
        }
    }
    return rows
}

/** 草稿的排布（[pack] 的实体重载）。 */
fun pack(widgets: List<MobileDashboardWidget>): List<List<MobileDashboardWidget>> =
    pack(widgets, { w: MobileDashboardWidget -> w.size }, { w: MobileDashboardWidget -> w.side })

/**
 * 每张卡的**有效半格**：`LEFT` / `RIGHT`；整宽卡（以及档位认不出来的）是 `null` ——
 * 它们没有半格，也都**关掉当前行**（紧跟的半宽卡从新的一行开始，不会回头补上一行的右半格）。
 *
 * 从左往右扫的状态机，**不是**「奇偶交替」：显式与缺省混排时奇偶会推错。
 * 值不认识时按「没写」推（后端校验器会先拒掉；老客户端 / 手写请求漏进来时不该炸）。
 */
fun <T> effectiveSides(
    items: List<T>,
    sizeOf: (T) -> String?,
    sideOf: (T) -> String?
): List<String?> {
    val sides = ArrayList<String?>(items.size)
    var rightFree = false
    for (item in items) {
        if (sizeOf(item) != DashboardTypes.SIZE_HALF) {
            rightFree = false
            sides.add(null)
            continue
        }
        val explicit = explicitSide(sideOf(item))
        when {
            explicit == DashboardTypes.SIDE_LEFT -> {
                sides.add(DashboardTypes.SIDE_LEFT)
                rightFree = true
            }

            explicit == DashboardTypes.SIDE_RIGHT -> {
                sides.add(DashboardTypes.SIDE_RIGHT)
                rightFree = false
            }

            rightFree -> {
                sides.add(DashboardTypes.SIDE_RIGHT)
                rightFree = false
            }

            else -> {
                sides.add(DashboardTypes.SIDE_LEFT)
                rightFree = true
            }
        }
    }
    return sides
}

/** 草稿里每张卡的有效半格（[effectiveSides] 的实体重载）。 */
fun effectiveSides(widgets: List<MobileDashboardWidget>): List<String?> =
    effectiveSides(widgets, { w: MobileDashboardWidget -> w.size }, { w: MobileDashboardWidget -> w.side })

/**
 * 把「有效半格」**物化**到卡上：半宽卡写上推出来的 `side`，整宽卡清成 `null`（它没有半格，
 * 身上那个字符串谁也不读）。幂等。
 *
 * 进编辑态时对**草稿与基线都跑一次**：草稿里每张半宽卡都带显式 `side` 是「拖动不横向让位」
 * 的前提（见文件头那条不变量）；基线也跑一遍，才不会让「物化」这件事本身被算成一次用户改动。
 */
fun deriveSides(widgets: List<MobileDashboardWidget>): List<MobileDashboardWidget> {
    val sides = effectiveSides(widgets)
    return widgets.mapIndexed { i, w -> w.copy(side = sides[i]) }
}

/**
 * 一张半宽卡插在 [index] 位时该占哪半格：**只看它前面那一张**。
 * 前一张的有效半格是 `LEFT`（它的右半格空着）→ `RIGHT` 填进去（这等于「并排放到它右边」）；
 * 其余（前一张是 `RIGHT`、整宽，或它前面没有卡）→ `LEFT` 另起一行。
 *
 * 只要这一个判断就够了：状态机里「当前行右半格空不空」**只**由前一张卡的有效半格决定。
 */
fun nextHalfSide(widgets: List<MobileDashboardWidget>, index: Int): String =
    if (effectiveSides(widgets.take(index.coerceIn(0, widgets.size))).lastOrNull() == DashboardTypes.SIDE_LEFT) {
        DashboardTypes.SIDE_RIGHT
    } else {
        DashboardTypes.SIDE_LEFT
    }

/**
 * 追加一张半宽卡（加卡 / 拖到末尾）时它该占哪半格：就是 [nextHalfSide] 在末尾那一处。
 *
 * 不做全局搜索：中间被整宽卡打断留下的空右半格，**不是不能填**，而是追加够不回去 ——
 * 要去填它得靠拖动（候选里选那一格），自动去找只会把卡放到用户没指的地方。
 */
fun nextHalfSide(widgets: List<MobileDashboardWidget>): String = nextHalfSide(widgets, widgets.size)

/**
 * 落位的**唯一**产物函数：把 [dragId] 那张从 [draft] 里摘掉，再按 `(index, side)` 插回去。
 *
 * 这是「展示 = 落库」的根据 —— 编辑页画出来的列表就是它的返回值（再喂给 [pack]），保存落库的
 * 也是同一份，所以落点框画在哪一格，松手就一定落在哪一格，不存在「框和结果对不上」。
 *
 * [side] 只有半宽卡有意义：整宽卡一律写成 `null`；半宽卡传 `null` 时保留它原本的 `side`
 * （原本也没写就交给排布按缺省推，即靠左）。[index] 是插入位，越界会被夹到 `0..size`。
 */
fun arrange(
    draft: List<MobileDashboardWidget>,
    dragId: String,
    index: Int,
    side: String?
): List<MobileDashboardWidget> {
    val dragged = draft.firstOrNull { it.id == dragId } ?: return draft
    val placed = dragged.copy(
        side = when {
            dragged.size != DashboardTypes.SIZE_HALF -> null
            side != null -> side
            else -> dragged.side
        }
    )
    val rest = draft.filterNot { it.id == dragId }.toMutableList()
    rest.add(index.coerceIn(0, rest.size), placed)
    return rest
}

/** 认得出才算写了：`null` 与不认识的值都当「没写」。 */
private fun explicitSide(side: String?): String? =
    side?.takeIf { it == DashboardTypes.SIDE_LEFT || it == DashboardTypes.SIDE_RIGHT }

// ===== 新卡的 id =====

/**
 * 新卡的 id：从 [counter] 往上找第一个**草稿里还没用**的 `draft-N`，返回它与新的计数。
 *
 * 唯一的基准是**当前草稿**，不是计数器：计数器是每个 VM 实例自己的（重开编辑页从 0 起步），
 * 而 id 会**跟着布局存进库** —— 保存一次，`draft-1` 就永久留在那份布局里。于是新开编辑页时
 * 闭着眼 `++` 必然再生成一个 `draft-1`：草稿里两张卡同一个 id，LazyColumn 的 item key 正是
 * 由 id 拼的，撞 key 当场崩（`Key "draft-1" was already used`）。
 */
fun newDraftId(used: Collection<String>, counter: Long): Pair<String, Long> {
    var n = counter
    while (true) {
        n += 1
        val id = "draft-$n"
        if (id !in used) {
            return id to n
        }
    }
}

// ===== 行 key =====

/**
 * 行在 LazyColumn 里的 key（也是拖拽签名）：行内各卡 id 拼接。
 *
 * 前缀 `row:` 是为了与列表里另外那几个 item 的 key（`empty` / `add` / `spacer`）错开 ——
 * 卡片 id 撞上那三个字面量同样会崩。整屏用请走 [rowKeys]。
 */
fun rowKey(row: List<MobileDashboardWidget>): String =
    "row:" + row.joinToString("~") { it.id.orEmpty() }

/**
 * 整屏行的 key，**保证两两不同**：同一个 key 在 LazyColumn 里出现两次会直接崩
 * （`Key "..." was already used. If you are using LazyColumn ...`）。
 *
 * 正常布局里每张卡 id 唯一，这里就原样返回 [rowKey]（key 稳定，让位动画与滚动锚点都正常）；
 * 万一拿到 id 重复的脏数据（库里真存过），给后来那行补一个位置后缀兜底 —— 排布错一格还能用，
 * 崩了则什么都做不了。
 */
fun rowKeys(rows: List<List<MobileDashboardWidget>>): List<String> {
    val used = HashSet<String>()
    return rows.mapIndexed { i, row ->
        val key = rowKey(row)
        if (used.add(key)) key else "$key#$i"
    }
}
