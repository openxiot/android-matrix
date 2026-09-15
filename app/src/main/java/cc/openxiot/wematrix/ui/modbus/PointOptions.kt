package cc.openxiot.wematrix.ui.modbus

import cc.openxiot.wematrix.data.api.ModbusBitName

/**
 * 点表功能码的**数量口径**，逐条对齐 webapp-matrix 的
 * `pages/main/modbus/command/point.options.ts`（单独成文件是照 web 那份的文件边界，
 * 免得把「帧里读几个寄存器」这类编码口径混进 [ModbusFormat] 的展示口径里）。
 *
 * 为什么「数量」需要一整套折算：点表里用户填的 `quantity` 是**值的个数**（03/04），
 * 而 Modbus 帧里读的是**寄存器个数** —— 一个 int32/float32 值占两个寄存器。
 * 于是同一件事有三个数（值的个数 / 帧里的寄存器数 / 应答字段数），string 又是特例
 * （数量本身就是字符串长度、整段算一个值、不加倍）。折算错一位，帧就发给设备了，
 * 故这层单独收拢、逐条注明。
 */

/** 读位（01/02）：每点 1 位布尔，应答的位清单按位数走 */
private val READ_BIT_FCS = setOf("01", "02")

/** 读寄存器（03/04）：带数据格式 / 字节序 / 缩放 / 单位 */
private val READ_REG_FCS = setOf("03", "04")

/**
 * 数据格式占用的寄存器数：int16/uint16 → 1，int32/uint32/float32 → 2，
 * 其余（string、未知）返回 null —— 调用方各自兜 1 或另走分支。
 */
fun registerSpan(dataType: String?): Int? = when (dataType) {
    "int16", "uint16" -> 1
    "int32", "uint32", "float32" -> 2
    else -> null
}

/**
 * **请求帧里的数量**：01/02 即位/线圈个数；03/04 非 string = 值的个数 × 类型跨度
 * （string 的数量本身就是长度，不加倍）。与后端 `ModbusFrameCodec.readQuantity` 同口径。
 */
fun frameQuantityOf(fc: String?, quantity: Int?, dataType: String?): Int {
    val q = maxOf(1, quantity ?: 1)
    if (fc !in READ_REG_FCS) return q
    return q * (registerSpan(dataType) ?: 1)
}

/**
 * 一条读命令的**应答字段个数**（＝要填的字段名称个数）：03/04 string 恒为 1、
 * 其余类型为值的个数；01/02 的应答字段恒为 1（整段位掩码，逐位命名另走 [expectedBitCount]）；
 * 写操作没有应答字段，返回 0。
 */
fun expectedFieldCount(fc: String?, quantity: Int?, dataType: String?): Int {
    if (fc == null) return 0
    if (fc in READ_REG_FCS) {
        return if (dataType == "string") 1 else maxOf(1, quantity ?: 1)
    }
    return 0
}

/** 一条读位命令要展示的位名称行数（01/02 = 位/线圈个数），其余功能码返回 0 */
fun expectedBitCount(fc: String?, quantity: Int?): Int {
    if (fc == null || fc !in READ_BIT_FCS) return 0
    return maxOf(1, quantity ?: 1)
}

/**
 * 位名称表 → 提交口径：**只保留真正命名了的位**（留空 = 该位不单独出值，仍可从整段掩码读），
 * 按偏移升序、同一位只留一条；偏移超出当前数量的条目丢弃（数量改小后不留残名）。
 *
 * 本端只读，不做提交；但展示要跟着这份口径 —— 后端落库的就是它，直接照抄原始数组会显示出
 * 用户已经删掉、或已经超出数量的残名。
 */
fun fitBitNames(names: List<ModbusBitName>, count: Int): List<ModbusBitName> {
    val kept = mutableListOf<ModbusBitName>()
    val seen = mutableSetOf<Int>()
    for (item in names) {
        val offset = item.offset
        val name = item.name?.trim().orEmpty()
        if (offset == null || offset < 0 || offset >= count || name.isEmpty() || offset in seen) {
            continue
        }
        seen.add(offset)
        kept += ModbusBitName(offset = offset, name = name)
    }
    return kept.sortedBy { it.offset ?: 0 }
}

/**
 * 应答字段的默认名称：单个字段用基名，多个字段在基名后加序号。
 * 只在用户没填名字时兜底，与生成服务时的 `response[].field` 同一套。
 */
fun defaultFieldName(baseName: String, index: Int, total: Int): String {
    val base = baseName.trim()
    return if (total > 1) "$base ${index + 1}" else base
}

/** 把名称列表补/裁到 count 个：保留已有的名字，缺的用默认名（基名 [baseName]）补齐 */
fun fitFieldNames(names: List<String>, count: Int, baseName: String): List<String> =
    (0 until maxOf(0, count)).map { i ->
        val kept = names.getOrNull(i)?.trim().orEmpty()
        if (kept.isNotEmpty()) kept else defaultFieldName(baseName, i, count)
    }

/**
 * 应答字段默认名的基名：取命令名称去掉「读」/「写」前缀的主体
 * （如「读开关状态」→「开关状态」）—— 字段名是读回来的那个值的标签，带动作前缀反而不像数据字段。
 *
 * 只认中文的「读 / 写」两个前缀：点表多以中文录入，而本端没有 i18n、命令名就是落库的原文，
 * 不存在 web 那种「当前语言的前缀另算一遍」的问题。
 */
fun fieldBaseName(name: String?): String {
    val text = name.orEmpty()
    for (prefix in listOf("读", "写")) {
        if (text.startsWith(prefix)) return text.substring(prefix.length).trim()
    }
    return text
}
