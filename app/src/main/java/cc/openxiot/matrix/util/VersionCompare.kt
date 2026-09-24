package cc.openxiot.matrix.util

/**
 * 版本号比对。**取向是「宁可漏报，不可假报」**：解析不出来就返回 null，由调用方报
 * 「无法识别版本信息」，绝不猜、绝不截断。假报的代价是让用户白下 54MB 再被安装器拒掉，
 * 漏报只是晚一天看到升级提示。
 *
 * 两条不能走的捷径：
 * - **不能按字符串比**：`"1.0.10" < "1.0.9"` 成立，而 1.0.10 才是新的。
 * - **不能转成 Double 比**：`1.0.10` 与 `1.0.1` 都会变成 `1.01`，10 和 1 就此分不出来。
 *
 * 段数不固定（`1.0` 与 `1.0.0` 等值，短的一侧补 0），但每段必须是**不带前导零的纯数字**。
 * 这与 CI 里 `X*10000 + Y*100 + Z` 那条 versionCode 公式是同一套假设：若认下 `1.05`，
 * 它在数值上等于 `1.5`，算出的 versionCode 却差 500，两套口径迟早会在某个版本上打架。
 * 没人会写 `1.05`，直接拒掉最省事。
 */
object VersionCompare {

    /** 一段：`0` 本身，或不为 0 开头的纯数字。 */
    private val SEGMENT = Regex("""^(?:0|[1-9]\d*)$""")

    /**
     * `"v1.0.5"` → `[1, 0, 5]`、`"1.0"` → `[1, 0]`。可选的 `v`/`V` 前缀与首尾空白容忍；
     * 其余任何形式（空、`1.0.x`、`1.0.5-rc1`、`1..0`、`1.`、段大过 Int）一律 null。
     */
    fun parse(raw: String?): List<Int>? {
        val text = raw?.trim().orEmpty().removePrefix("v").removePrefix("V")
        if (text.isEmpty()) return null
        return text.split('.').map { segment ->
            if (!SEGMENT.matches(segment)) return null
            // 段长到超出 Int 也在这里挡掉，顺便给 compare 的减法留出不受溢出的余地
            segment.toIntOrNull() ?: return null
        }
    }

    /**
     * 远端相对本机：[remote] 更新返回正数、相同返回 0、更旧返回负数，
     * **任一侧解析不出来返回 null** —— 不是 0，也不是负数，免得调用方把「不知道」
     * 顺手当成「不需要更新」。
     */
    fun compare(remote: String?, local: String?): Int? {
        val remoteParts = parse(remote) ?: return null
        val localParts = parse(local) ?: return null
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val diff = remoteParts.getOrElse(i) { 0 } - localParts.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
