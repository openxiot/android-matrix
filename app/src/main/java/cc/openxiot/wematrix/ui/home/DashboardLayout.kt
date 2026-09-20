package cc.openxiot.wematrix.ui.home

import cc.openxiot.wematrix.data.api.MobileDashboardWidget

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
 * 新加一张半宽卡时它该占哪半格：**只看最后一张**（新卡永远追加在末尾）。
 * 末张是落单的 `LEFT`（右半格空着）→ `RIGHT` 填进去；其余（末张是 `RIGHT`、整宽，或还没有卡）
 * → `LEFT` 另起一行。
 *
 * 不做全局搜索：中间被整宽卡打断留下的空右半格，按排布规则**永远填不上**（新卡追加在末尾，
 * 够不回去），去找它只会把卡放到用户没指的地方。
 */
fun nextHalfSide(widgets: List<MobileDashboardWidget>): String =
    if (effectiveSides(widgets).lastOrNull() == DashboardTypes.SIDE_LEFT) {
        DashboardTypes.SIDE_RIGHT
    } else {
        DashboardTypes.SIDE_LEFT
    }

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
