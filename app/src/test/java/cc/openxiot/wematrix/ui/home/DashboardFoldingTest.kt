package cc.openxiot.wematrix.ui.home

import cc.openxiot.wematrix.ui.home.MobileRenderFormat.Slice
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [truncateSlices]：显示层截断的边界。
 *
 * 用例逐条对着 web `dashboard.folding.spec.ts` 的 `truncatePoints` 那组抄 ——
 * 「和 web 一致」这句话要能被执行，就只能钉在测试里。
 */
class DashboardFoldingTest {

    private fun slice(name: String, count: Int) = Slice(name, count)

    @Test
    fun truncateSlices_returnsAsIsWhenLimitIsUnsetOrBigEnough() {
        val slices = listOf(slice("a", 3), slice("b", 2))

        assertEquals(slices, truncateSlices(slices, null, "其他"))
        assertEquals(slices, truncateSlices(slices, 0, "其他"))
        assertEquals(slices, truncateSlices(slices, 2, "其他"))
        assertEquals(slices, truncateSlices(slices, 5, "其他"))
    }

    @Test
    fun truncateSlices_takesTheFirstNAndFoldsTheRestIntoOther() {
        val slices = listOf(slice("a", 5), slice("b", 4), slice("c", 3), slice("d", 2))

        assertEquals(
            listOf(slice("a", 5), slice("b", 4), slice("其他", 5)),
            truncateSlices(slices, 2, "其他")
        )
    }

    @Test
    fun truncateSlices_doesNotAppendAnEmptyOtherWhenLimitEqualsCount() {
        val slices = listOf(slice("a", 1), slice("b", 1))

        assertEquals(2, truncateSlices(slices, 2, "其他").size)
    }

    @Test
    fun truncateSlices_negativeLimitReturnsAsIs() {
        val slices = listOf(slice("a", 1), slice("b", 1))

        assertEquals(slices, truncateSlices(slices, -1, "其他"))
    }

    @Test
    fun truncateSlices_emptyStaysEmpty() {
        assertEquals(emptyList<Slice>(), truncateSlices(emptyList(), 3, "其他"))
    }

    @Test
    fun truncateSlices_withASingleSliceFoldsAllTheRest() {
        val slices = listOf(slice("a", 7), slice("b", 2), slice("c", 1))

        assertEquals(
            listOf(slice("a", 7), slice("其他", 3)),
            truncateSlices(slices, 1, "其他")
        )
    }
}
