package cc.openxiot.matrix.ui.home

import cc.openxiot.matrix.data.api.MobileDashboardWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [pack] / [effectiveSides] / [deriveSides] / [nextHalfSide] / [arrange] 的契约：**纯 JVM 单测**。
 *
 * 这段排布 + 拖拽逻辑此前**没有任何测试网**（近 15 个碰它的提交全是修 bug），而这次的
 * 「横向不许补位」正是从契约上重写它。这里压住三件事：
 *
 * 1. **排布规则**逐例钉死（`FULL` 独占一行、`LEFT` 开行、`RIGHT` 填进去或另起一行留空左半格）；
 * 2. **存量布局零迁移**：全没写 `side` 时退化成 `LEFT,RIGHT,LEFT,RIGHT…`，也就是改造前
 *    「连续两张 HALF 并排」的老样子；
 * 3. **两条性质**（穷举落位与半格验证）：
 *    - 纵向让位 —— 任意落位之后，**除被拖那张外**每张卡的有效半格一格都不变（不横向补位）；
 *    - 框 = 落点 —— [arrange] 的产物里，被拖那张卡的**自己那一格**永远与候选 `(p, σ)` 相符。
 *
 * 夹具与断言都尽量写成「一眼读得出形状」的字符串（`"a,b|c"` = 第一行 a 与 b 并排、第二行 c 独占）。
 */
class DashboardLayoutTest {

    // ===== 排布规则 =====

    @Test
    fun pack_fullCardsEachOwnRow() {
        assertEquals("f1|f2", shape(pack(listOf(full("f1"), full("f2")))))
    }

    @Test
    fun pack_leftThenRightAreOneRow() {
        assertEquals("a,b", shape(pack(listOf(half("a", LEFT), half("b", RIGHT)))))
    }

    /** 落单的左半张：右边那半格**空着**，不占位补成整宽。 */
    @Test
    fun pack_loneLeftHalfKeepsAnEmptyRightHalf() {
        assertEquals("a", shape(pack(listOf(half("a", LEFT)))))
    }

    /** 落单的右半张：前面没有开着的行（前一张整宽，或它就是被留下的那半张）→ 独占一格、靠右。 */
    @Test
    fun pack_loneRightHalfKeepsAnEmptyLeftHalf() {
        assertEquals("f|b", shape(pack(listOf(full("f"), half("b", RIGHT)))))
    }

    /** `RIGHT` 只填**当前开着的**那一行；行已经被别人填满就另起一行、左半格空着。 */
    @Test
    fun pack_rightFillsOnlyAnOpenRow() {
        assertEquals("a,b|c", shape(pack(listOf(half("a", LEFT), half("b", RIGHT), half("c", RIGHT)))))
    }

    @Test
    fun pack_twoLeftsAreTwoRows() {
        assertEquals("a|b", shape(pack(listOf(half("a", LEFT), half("b", LEFT)))))
    }

    @Test
    fun pack_fullCardSplitsRows() {
        assertEquals("a|f|b", shape(pack(listOf(half("a", LEFT), full("f"), half("b", LEFT)))))
    }

    @Test
    fun pack_empty() {
        assertEquals(listOf<List<MobileDashboardWidget>>(), pack(emptyList()))
    }

    /** 排布必须**一个不漏、顺序不变**：落库的数组顺序就是阅读顺序，排布只是把它读出来。 */
    @Test
    fun pack_coversEveryCardExactlyOnceInOrder() {
        val draft = listOf(
            half("a", LEFT), full("f"), half("b", RIGHT),
            half("c"), half("d"), half("e", LEFT)
        )
        assertEquals(draft.map { it.id }, pack(draft).flatten().map { it.id })
    }

    // ===== 缺省推导：全没写 = 改造前的老样子 =====

    @Test
    fun sides_allUnsetDegeneratesToLegacyGreedyPairing() {
        assertEquals("L", sides(listOf(half("a"))))
        assertEquals("L,R", sides(listOf(half("a"), half("b"))))
        assertEquals("L,R,L", sides(listOf(half("a"), half("b"), half("c"))))
        assertEquals("L,R,L,R", sides(listOf(half("a"), half("b"), half("c"), half("d"))))
        // 排布形状同样逐例钉住：奇数张时最后那张靠左（旧代码也是这么配的）
        assertEquals("a,b|c", shape(pack(listOf(half("a"), half("b"), half("c")))))
    }

    @Test
    fun sides_fullCardClosesTheRow() {
        assertEquals("L,-,L", sides(listOf(half("a"), full("f"), half("b"))))
        assertEquals("L,R,-,L,R", sides(listOf(half("a"), half("b"), full("f"), half("c"), half("d"))))
        // 整宽卡后面紧跟的缺省半宽卡**不会回头**去补上一行的右半格
        assertEquals("a|f|b,c", shape(pack(listOf(half("a"), full("f"), half("b"), half("c")))))
    }

    // ===== 显式 side =====

    /** 显式 `LEFT` 时，前一张空着的右半格就**空着** —— 这就是「不横向补位」。 */
    @Test
    fun sides_explicitLeftOpensANewRowEvenIfRightHalfIsFree() {
        assertEquals("L,R,L", sides(listOf(half("a"), half("b"), half("c", LEFT))))
        assertEquals("a,b|c", shape(pack(listOf(half("a"), half("b"), half("c", LEFT)))))
    }

    @Test
    fun sides_explicitRightWithoutAFreeHalfOpensARowWithBlankLeft() {
        assertEquals("-,-,R", sides(listOf(full("f"), full("g"), half("c", RIGHT))))
        assertEquals("L,R,R", sides(listOf(half("a"), half("b"), half("c", RIGHT))))
        assertEquals("a,b|c", shape(pack(listOf(half("a"), half("b"), half("c", RIGHT)))))
    }

    /** 显式与缺省混排：状态机（不是奇偶交替）—— `b` 显式靠左，`a` 空出来的右半格就空着。 */
    @Test
    fun sides_explicitAndDerivedMixed() {
        assertEquals("L,L,R", sides(listOf(half("a"), half("b", LEFT), half("c"))))
        assertEquals("a|b,c", shape(pack(listOf(half("a"), half("b", LEFT), half("c")))))
    }

    /** 宽进：不认识的值不让它炸，按「没写」推（后端校验器会先拒掉；老客户端 / 手写请求不该炸）。 */
    @Test
    fun sides_unrecognizedSideIsTreatedAsUnset() {
        assertEquals("L,R", sides(listOf(half("a", "middle"), half("b"))))
    }

    /** 档位认不出来：当整宽卡处理（关掉当前行、自己不算半格）。 */
    @Test
    fun sides_unrecognizedSizeClosesTheRow() {
        val weird = MobileDashboardWidget(id = "x", type = "stat", size = "LARGE")
        assertEquals("L,-,L", sides(listOf(half("a"), weird, half("b"))))
    }

    /** 整宽卡身上带着 `side`：后端放行（下游一律忽略），本端也一样 —— 它没有半格，也不影响排布。 */
    @Test
    fun sides_fullCardCarryingASideIsIgnored() {
        assertEquals("-", sides(listOf(full("f", LEFT))))
        assertEquals("f", shape(pack(listOf(full("f", LEFT)))))
    }

    // ===== 不横向补位（用户报的那个场景） =====

    /** 抽走一对里的**左**半张 → 右半张原地不动（左半格空着）；抽走**右**半张 → 左半张照样在左。 */
    @Test
    fun removingOneHalfLeavesTheOtherOneInPlace() {
        val pair = deriveSides(listOf(half("a"), half("b")))

        val leftGone = pair.filterNot { it.id == "a" }
        assertEquals("R", sides(leftGone))
        assertEquals("b", shape(pack(leftGone)))

        val rightGone = pair.filterNot { it.id == "b" }
        assertEquals("L", sides(rightGone))
        assertEquals("a", shape(pack(rightGone)))
    }

    /** 纵向让位：把左下那张拖到最前，整行下移 —— 没有一张卡换半格（`c` 靠左、其余纹丝不动）。 */
    @Test
    fun arrange_verticalGiveWayNeverSlidesAnyoneSideways() {
        val draft = deriveSides(listOf(half("a"), half("b"), half("c"), half("d"))) // [a,b] [c,d]

        val moved = arrange(draft, "c", 0, LEFT)

        assertEquals("c|a,b|d", shape(pack(moved)))
        assertEquals("L,L,R,R", sides(moved))
    }

    /**
     * **性质**：任意落位 `(p, σ)` 之后，除被拖那张外每张卡的有效半格一个都不变。
     *
     * 这是「只纵向让位」的完整表述 —— 纵向怎么让都行，横向一格都不许动。它成立的前提是
     * 草稿里每张半宽卡都带显式 `side`（[deriveSides] 物化），所以这里喂的每份草稿都先过它。
     */
    @Test
    fun arrange_neverChangesAnyOtherCardsSide() {
        val drafts = listOf(
            deriveSides(listOf(half("a"), half("b"), half("c"), half("d"))),        // L,R,L,R
            deriveSides(listOf(half("a"), full("f"), half("b", RIGHT))),            // L,-,R
            deriveSides(listOf(half("a", LEFT), half("b", LEFT), half("c", LEFT))), // 三个空右半格
            deriveSides(listOf(half("a", RIGHT), full("f"), half("b", LEFT), half("c")))
        )
        for (draft in drafts) {
            val before = effectiveSides(draft)
            for (id in draft.mapNotNull { it.id }) {
                for (p in 0..draft.size - 1) {
                    for (sigma in listOf(LEFT, RIGHT)) {
                        val after = arrange(draft, id, p, sigma)
                        val afterSides = effectiveSides(after)
                        after.forEachIndexed { i, w ->
                            if (w.id != id) {
                                val j = draft.indexOfFirst { it.id == w.id }
                                assertEquals(
                                    "落位 (p=$p, σ=$sigma) 之后 ${w.id} 的半格不该变",
                                    before[j], afterSides[i]
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 框 = 落点 =====

    /**
     * **性质**：`arrange(draft, id, p, σ)` 的产物里，被拖那张卡的**自己那一格**永远与候选
     * `(p, σ)` 相符 —— 位次就是 `p`，`LEFT` 落在行内第一格、`RIGHT` 落在行内最后一格
     * （并排的右卡，或独占一行靠右）。
     *
     * 编辑页的落点框画的就是这一格（展示列表 = `arrange` 的产物），于是「框在哪就一定落在哪」
     * 不是靠两处代码对齐，而是**同一个函数的同一份结果**。
     */
    @Test
    fun arrange_landsTheDraggedCardExactlyInTheCandidateCell() {
        val draft = deriveSides(listOf(half("a"), half("b"), full("f"), half("c"), half("d")))
        for (id in draft.mapNotNull { it.id }) {
            for (p in 0..draft.size - 1) {
                for (sigma in listOf(LEFT, RIGHT)) {
                    val after = arrange(draft, id, p, sigma)
                    assertEquals("落库位次就是候选位次", p, after.indexOfFirst { it.id == id })

                    val placed = after[p]
                    val rows = pack(after)
                    val (r, c) = cellOf(rows, id)
                    if (placed.size == HALF) {
                        assertEquals("半宽卡带的就是手指选的那半格", sigma, placed.side)
                        if (sigma == LEFT) {
                            assertEquals("LEFT 落在行内第一格", 0, c)
                        } else {
                            assertEquals("RIGHT 落在行内最后一格", rows[r].size - 1, c)
                        }
                    } else {
                        assertNull("整宽卡没有半格", placed.side)
                        assertEquals("整宽卡独占一行", 1, rows[r].size)
                    }
                }
            }
        }
    }

    /** 被拖卡本来就排在原位、Σ 也没变 → 顺序一个字节都不动。 */
    @Test
    fun arrange_keepsTheListWhenNothingMoves() {
        val draft = deriveSides(listOf(half("a"), half("b"), half("c"), half("d")))
        assertEquals(draft, arrange(draft, "c", 2, LEFT))
    }

    @Test
    fun arrange_unknownIdIsANoOp() {
        val draft = deriveSides(listOf(half("a"), half("b")))
        assertEquals(draft, arrange(draft, "nope", 0, LEFT))
    }

    /** 整宽卡落位：`side` 一律写成 null（它没有半格）—— 连身上带着的陈旧值也顺手清掉。 */
    @Test
    fun arrange_fullCardDropsItsSide() {
        val draft = listOf(full("f", LEFT), half("a", LEFT))

        val arranged = arrange(draft, "f", 1, RIGHT)

        assertNull(arranged.first { it.id == "f" }.side)
        assertEquals(listOf("a", "f"), arranged.map { it.id })
    }

    // ===== 新卡默认位 =====

    @Test
    fun nextHalfSide_fillsTheLastOpenHalfOnly() {
        assertEquals(LEFT, nextHalfSide(emptyList()))
        assertEquals(LEFT, nextHalfSide(listOf(full("f"))))
        assertEquals(RIGHT, nextHalfSide(listOf(half("a", LEFT))))
        assertEquals(RIGHT, nextHalfSide(listOf(half("a"))))          // 缺省推出来也是 LEFT，同样填得进去
        assertEquals(LEFT, nextHalfSide(listOf(half("a", RIGHT))))    // 落单的右半张：那一行已经关了
        assertEquals(LEFT, nextHalfSide(listOf(half("a", LEFT), half("b", RIGHT))))
    }

    /** 追加在末尾时，中间被整宽卡打断留下的空右半格够不回去（要填它只能靠拖动）。 */
    @Test
    fun nextHalfSide_doesNotReachBackToAHalfOpenedBeforeAFullCard() {
        assertEquals(LEFT, nextHalfSide(deriveSides(listOf(half("a", LEFT), full("f")))))
    }

    /** 插在中间（改尺寸成半宽那一处）：只看**它前面那张**，与后面有什么无关。 */
    @Test
    fun nextHalfSide_atAnIndexLooksOnlyAtTheCardBeforeIt() {
        val pairThenFull = deriveSides(listOf(half("a"), half("b"), full("f")))
        assertEquals(LEFT, nextHalfSide(pairThenFull, 0))    // 插到最前
        assertEquals(LEFT, nextHalfSide(pairThenFull, 2))    // 前一张是满行的右卡
        assertEquals(LEFT, nextHalfSide(pairThenFull, 3))    // 前一张是整宽卡
        assertEquals(LEFT, nextHalfSide(emptyList(), 0))

        // 前一张是**落单的左半卡**（右半格空着）→ 填进去并排
        val loneLeft = deriveSides(listOf(half("a", LEFT), full("f")))
        assertEquals(RIGHT, nextHalfSide(loneLeft, 1))
    }

    /** 两个重载是同一件事：不带下标就是「追加在末尾」。 */
    @Test
    fun nextHalfSide_withoutIndexIsTheAppendPosition() {
        val draft = deriveSides(listOf(half("a"), half("b"), full("f")))
        assertEquals(nextHalfSide(draft), nextHalfSide(draft, draft.size))
    }

    // ===== 物化 =====

    @Test
    fun deriveSides_materializesEffectiveSidesAndIsIdempotent() {
        val derived = deriveSides(listOf(half("a"), half("b"), full("f"), half("c")))
        assertEquals(listOf(LEFT, RIGHT, null, LEFT), derived.map { it.side })
        assertEquals(derived, deriveSides(derived))
    }

    @Test
    fun deriveSides_clearsSideOnFullCardsAndKeepsEverythingElse() {
        val original = listOf(full("f", LEFT), half("a", RIGHT))
        val derived = deriveSides(original)
        assertNull(derived[0].side)
        assertEquals(RIGHT, derived[1].side)
        // 除了 side，卡本身一个字段都不动（顺序、id、type、size 原样）
        assertEquals(original.map { it.id }, derived.map { it.id })
        assertEquals(original.map { it.size }, derived.map { it.size })
    }

    // ===== 新卡 id =====

    @Test
    fun newDraftId_startsAtOneWhenNothingIsUsed() {
        assertEquals("draft-1" to 1L, newDraftId(emptyList(), 0L))
    }

    /**
     * **这条就是那次崩溃的复现**：库里那份布局带着上一轮保存下来的 `draft-1`，而计数器是
     * 每个 VM 实例自己的（重开编辑页从 0 起步）—— 照 `++` 加就会再造一个 `draft-1`。
     */
    @Test
    fun newDraftId_skipsIdsAlreadyInTheDraft() {
        assertEquals("draft-2" to 2L, newDraftId(listOf("draft-1"), 0L))
        assertEquals("draft-4" to 4L, newDraftId(listOf("draft-1", "draft-2", "draft-3"), 0L))
    }

    @Test
    fun newDraftId_continuesFromTheCounterWhenThatIdIsFree() {
        assertEquals("draft-3" to 3L, newDraftId(listOf("draft-1", "draft-2"), 2L))
    }

    @Test
    fun newDraftId_ignoresOtherKindsOfIds() {
        assertEquals("draft-1" to 1L, newDraftId(listOf("preset-1", "w_7f3a"), 0L))
    }

    /** 性质：连加 20 张，每次都拿**当时的草稿**判，生成的 id 两两不同。 */
    @Test
    fun newDraftId_neverRepeatsAcrossRepeatedAdds() {
        val used = mutableListOf<String>()
        var counter = 0L
        repeat(20) {
            val (id, next) = newDraftId(used, counter)
            counter = next
            assertFalse("id $id 撞了", id in used)
            used.add(id)
        }
    }

    // ===== 行 key =====

    @Test
    fun rowKeys_areStableAndPrefixed() {
        val rows = pack(listOf(half("a"), half("b"), full("f")))
        assertEquals("a,b|f", shape(rows))
        assertEquals(listOf("row:a~b", "row:f"), rowKeys(rows))
    }

    /**
     * 脏数据兜底：两张卡同 id（老版本真存进去过）也必须两两不同 —— LazyColumn 撞 key 是**崩溃**，
     * 不是画错。排布错一格还能用，崩了就什么都做不了。
     */
    @Test
    fun rowKeys_stayUniqueEvenWithDuplicateIds() {
        val dup = listOf(half("draft-1"), full("draft-1"), half("draft-1"))
        val keys = rowKeys(pack(dup))
        assertEquals(3, keys.size)
        assertEquals(3, keys.toSet().size)
    }

    /** 卡片 id 撞上列表里另外那几个 item 的字面量 key（`add` / `empty` / `spacer`）也不会串。 */
    @Test
    fun rowKey_doesNotCollideWithTheListsLiteralKeys() {
        assertEquals("row:add", rowKey(listOf(half("add"))))
    }

    // ===== 夹具 =====

    private val LEFT = DashboardTypes.SIDE_LEFT
    private val RIGHT = DashboardTypes.SIDE_RIGHT
    private val HALF = DashboardTypes.SIZE_HALF
    private val FULL = DashboardTypes.SIZE_FULL

    private fun half(id: String, side: String? = null) = MobileDashboardWidget(
        id = id, type = DashboardTypes.STAT, size = HALF, side = side
    )

    private fun full(id: String, side: String? = null) = MobileDashboardWidget(
        id = id, type = DashboardTypes.LINE, size = FULL, side = side
    )

    /** 排布结果的**形状**：`"a,b|c"` = 第一行 a 与 b 并排、第二行 c 独占。 */
    private fun shape(rows: List<List<MobileDashboardWidget>>): String =
        rows.joinToString("|") { row -> row.joinToString(",") { it.id.orEmpty() } }

    /** 有效半格的**序列**：`"L,R,-"`（`-` = 没有半格）。 */
    private fun sides(widgets: List<MobileDashboardWidget>): String =
        effectiveSides(widgets).joinToString(",") { it?.take(1) ?: "-" }

    /** 某张卡在排布结果里的 `行 to 格`。 */
    private fun cellOf(rows: List<List<MobileDashboardWidget>>, id: String): Pair<Int, Int> {
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, w -> if (w.id == id) return r to c }
        }
        throw AssertionError("卡片 $id 不在排布结果里")
    }
}
