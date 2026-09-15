package cc.openxiot.wematrix.ui.modbus

import cc.openxiot.wematrix.data.api.ModbusCommand
import cc.openxiot.wematrix.data.api.ModbusCoilItem
import cc.openxiot.wematrix.data.api.ModbusRegisterItem

/**
 * 生成功能码动作对应的 Modbus RTU 请求帧与应答帧（完整帧：从站地址 + PDU + CRC16），
 * 并把帧按字段切开供展示。逐条对齐 webapp-matrix 的
 * `pages/main/modbus/editor/request/request.frame.ts`。
 *
 * **这是逐字节比对的东西**：抄错一位不会报错，只会生成一帧发给设备。改这里之前先看 web 那份。
 *
 * 编码约定（与设备点表模型一致）：
 * - 从站地址取自设备信息，帧首字节；
 * - 寄存器/线圈地址 = 0 基数据地址（start），与逻辑地址换算口径一致；
 * - 01/02 读位：帧里数量 = 位/线圈个数；03/04 读寄存器：点表里的数量是**值的个数**，
 *   帧里数量 = 数量 × 类型跨度（string 的数量本身就是长度，不加倍）—— 见 [frameQuantityOf]；
 * - 06 单寄存器值按 16 位无符号写；
 * - 05 写单线圈按 coilState → 0xFF00 / 0x0000；
 * - 0F 写多线圈：数量 = 条目数，按 offset 打包为位（bit0 = 起始地址），byteCount = ceil(n/8)；
 * - 10 写多寄存器：数量 = 各条类型占用之和，数据区按条 dataType/byteOrder 编码 2/4 字节真实值
 *   （int16/uint16 = 2 字节；int32/uint32/float32 = 4 字节；float32 的 value 即其 32 位位模式）。
 *
 * 本端只**预览**帧，不发送、不落库（web 里这一层也只出现在编辑器的「命令」对话框上）。
 */

/**
 * 生成结果帧（字节 + 展示用十六进制 + 字节数）。
 * [hex] 大写、空格分隔。
 */
data class RequestFrame(
    val bytes: List<Int>,
    val hex: String,
    val count: Int
)

/** 应答形态：read = 带数据区（01–04）；echo = 请求回显（05/06）；ack = 只回显地址与数量（0F/10）；exception = 异常应答 */
enum class ResponseKind { READ, ECHO, ACK, EXCEPTION }

/**
 * 应答帧：[bytes] 里为 null 的字节表示「设备返回后才能确定」，十六进制显示为 `??`。
 *
 * [sample] 为 true 表示数据区是示例值：读应答的真实数据由设备返回，此处按字节数补零，
 * 只为给出一个完整、可照抄的帧形。
 */
data class ResponseFrame(
    val bytes: List<Int?>,
    val hex: String,
    val count: Int,
    val kind: ResponseKind,
    val sample: Boolean
)

/** 一次命令的两条应答帧：正常应答 + 异常应答（异常应答恒一并给出） */
data class ResponsePreview(
    val frame: ResponseFrame,
    val exception: ResponseFrame
)

/**
 * 解析出的帧字段：[label] 是字段名，[hex] 是该字段的字节；
 * 解读用 [text]（一行）或 [lines]（数据区的逐位 / 逐寄存器多行）。
 */
data class FramePart(
    val label: String,
    val hex: String,
    val text: String? = null,
    val lines: List<String> = emptyList()
)

/** 命令数据不完整（缺从站地址、功能码不认、必要字段没填）时统一给这一句 */
const val FRAME_INCOMPLETE_MESSAGE = "命令数据不完整，无法生成请求帧"

/** 异常应答里那个异常码的常见含义；设备返回哪个由现场决定，故只列常见的四个 */
const val FRAME_EXCEPTION_CODES = "01 非法功能码 / 02 非法数据地址 / 03 非法数据值 / 04 从站设备故障"

/** 读功能码（01–04）：应答带数据区；其余为写功能码 */
private val READ_FC_NUMBERS = setOf(0x01, 0x02, 0x03, 0x04)

/** 读位功能码（01/02）：数据区按位数向上取整到字节；读寄存器（03/04）按每个寄存器 2 字节 */
private val READ_BIT_FC_NUMBERS = setOf(0x01, 0x02)

/** 数值 → 无符号位模式（负数补码、超界按 mod 折叠），与寄存器 hex 口径一致 */
private fun toUint(value: Long, bits: Int): Long {
    val mod = 1L shl bits
    var v = value % mod
    if (v < 0) v += mod
    return v
}

/** 16 位数值 → 高、低两字节 */
private fun u16(n: Long): List<Int> {
    val u = (n and 0xFFFFL).toInt()
    return listOf((u ushr 8) and 0xFF, u and 0xFF)
}

/** Modbus CRC16（多项式 0xA001，初值 0xFFFF，低字节在前） */
private fun crc16(bytes: List<Int>): Int {
    var crc = 0xFFFF
    for (b in bytes) {
        crc = crc xor (b and 0xFF)
        repeat(8) {
            crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
        }
    }
    return crc and 0xFFFF
}

private fun toHex(bytes: List<Int>): String =
    bytes.joinToString(" ") { "%02X".format(it and 0xFF) }

/** 十六进制文本；null（设备返回后才确定）显示为 `??` */
private fun toHexOrPlaceholder(bytes: List<Int?>): String =
    bytes.joinToString(" ") { b -> if (b == null) "??" else "%02X".format(b and 0xFF) }

/** 16/32 位位模式 → 大端字节序，再按 byteOrder 重排成线上字节 */
private fun valueBytes(u: Long, span: Int, byteOrder: String?): List<Int> {
    val be: List<Int> = if (span == 1) {
        listOf(((u ushr 8) and 0xFF).toInt(), (u and 0xFF).toInt())
    } else {
        listOf(
            ((u ushr 24) and 0xFF).toInt(),
            ((u ushr 16) and 0xFF).toInt(),
            ((u ushr 8) and 0xFF).toInt(),
            (u and 0xFF).toInt()
        )
    }
    val order = byteOrder ?: "ABCD"
    // 16 位只用 大端/小端 两种排布（另两种是 32 位跨寄存器才有的说法），故这里只认 DCBA
    if (span == 1) {
        return if (order == "DCBA") listOf(be[1], be[0]) else be
    }
    return when (order) {
        "DCBA" -> listOf(be[3], be[2], be[1], be[0]) // 小端
        "BADC" -> listOf(be[1], be[0], be[3], be[2]) // 字节交换
        "CDAB" -> listOf(be[2], be[3], be[0], be[1]) // 字交换
        else -> be // ABCD 大端
    }
}

/** 0F 多线圈：条目 offset→on 打包为位流；返回 [数量, 字节数, 位字节] */
private fun packCoils(items: List<ModbusCoilItem>): Triple<Int, Int, List<Int>>? {
    if (items.isEmpty()) return null
    var count = 0
    val onOffsets = mutableSetOf<Int>()
    items.forEachIndexed { index, item ->
        // offset 缺省时按条目顺序占位（与 0F 的线上形状一致）
        val offset = item.offset ?: index
        if (offset < 0) return@forEachIndexed
        if (offset + 1 > count) count = offset + 1
        if (item.on == true) onOffsets.add(offset)
    }
    val byteCount = (count + 7) / 8
    val bytes = MutableList(byteCount) { 0 }
    for (offset in onOffsets) {
        val i = offset shr 3
        bytes[i] = bytes[i] or (1 shl (offset and 7))
    }
    return Triple(count, byteCount, bytes)
}

/** 10 多寄存器：按条类型/字节序编码真实值；返回 [寄存器数, 字节数, 数据字节] */
private fun encodeRegisters(items: List<ModbusRegisterItem>): Triple<Int, Int, List<Int>>? {
    if (items.isEmpty()) return null
    val data = mutableListOf<Int>()
    var quantity = 0
    for (item in items) {
        val span = registerSpan(item.dataType) ?: 1
        val bits = if (span == 1) 16 else 32
        quantity += span
        // value 允许是 0..0xFFFFFFFF 的位模式（uint32/float32），故走 Long 再折成无符号
        data += valueBytes(toUint(item.value ?: 0L, bits), span, item.byteOrder)
    }
    return Triple(quantity, quantity * 2, data)
}

/** 按功能码组出 PDU 数据区；必要字段缺失返回 null */
private fun buildData(command: ModbusCommand): List<Int>? {
    val start = u16((command.start ?: 0).toLong())
    return when (command.fc) {
        // 03/04 的数量是「值的个数」，帧里要读的寄存器数 = 数量 × 类型跨度
        "01", "02", "03", "04" -> start +
            u16(frameQuantityOf(command.fc, command.quantity, command.dataType).toLong())

        "05" -> when (command.coilState) {
            "on" -> start + listOf(0xFF, 0x00)
            "off" -> start + listOf(0x00, 0x00)
            else -> null
        }

        "06" -> command.registerValue?.let { start + u16(it) }

        "0F" -> packCoils(command.coils)?.let { (count, byteCount, coilBytes) ->
            start + u16(count.toLong()) + listOf(byteCount) + coilBytes
        }

        "10" -> encodeRegisters(command.registers)?.let { (quantity, byteCount, regBytes) ->
            start + u16(quantity.toLong()) + listOf(byteCount) + regBytes
        }

        else -> null
    }
}

/**
 * 0F/10 写多个时应答回显的数量：0F 为线圈条目数，10 为各条类型占用的寄存器数之和
 * （与请求帧数据区同源，故直接复用打包/编码结果）。
 */
private fun writeQuantity(command: ModbusCommand, fc: Int): Int? {
    val packed = if (fc == 0x0F) packCoils(command.coils) else encodeRegisters(command.registers)
    return packed?.first
}

/** 按字节补 CRC16 尾，得到完整帧 */
private fun withCrc(body: List<Int>): List<Int> {
    val crc = crc16(body)
    return body + listOf(crc and 0xFF, (crc ushr 8) and 0xFF)
}

/**
 * 异常应答：从站 + (功能码 | 0x80) + 异常码 + CRC16；异常码与 CRC 由设备返回，以 `??` 占位。
 */
private fun exceptionFrame(slaveId: Int, fc: Int): ResponseFrame {
    val bytes: List<Int?> = listOf(slaveId, fc or 0x80, null, null, null)
    return ResponseFrame(
        bytes = bytes,
        hex = toHexOrPlaceholder(bytes),
        count = bytes.size,
        kind = ResponseKind.EXCEPTION,
        sample = false
    )
}

/**
 * 生成完整 RTU 请求帧。从站地址（0–255）不合法或命令数据不完整时返回 null
 * （页面上给 [FRAME_INCOMPLETE_MESSAGE]）。
 *
 * [slaveId] 取自点表的 `slave.slaveId`：**缺省就是生成不了帧**，不是拿 0 兜
 * —— 0 是一个合法的从站地址（广播），猜一个发出去会打到别的设备。
 */
fun buildRequestFrame(command: ModbusCommand, slaveId: Int?): RequestFrame? {
    if (slaveId == null || slaveId < 0 || slaveId > 255) return null
    val fc = command.fc?.toIntOrNull(16) ?: return null
    val data = buildData(command) ?: return null
    val bytes = withCrc(listOf(slaveId, fc) + data)
    return RequestFrame(bytes = bytes, hex = toHex(bytes), count = bytes.size)
}

/**
 * 生成功能码动作对应的应答帧（正常应答 + 异常应答）；从站地址非法或命令数据不完整时返回 null。
 *
 * - 01–04 读：`从站 + 功能码 + 字节数 + 数据区 + CRC16`，字节数由数量算出（读位 ceil(n/8)、
 *   读寄存器 n×2），数据区按示例值（全 0）补全 —— 真实数据由设备返回，故整帧标记 `sample`；
 * - 05/06 写单个：应答是请求回显，与请求帧逐字节相同；
 * - 0F/10 写多个：应答只回显 起始地址 + 数量，不带数据区。
 */
fun buildResponseFrame(command: ModbusCommand, slaveId: Int?): ResponsePreview? {
    if (slaveId == null || slaveId < 0 || slaveId > 255) return null
    val fc = command.fc?.toIntOrNull(16) ?: return null

    var body: List<Int>? = null
    var kind: ResponseKind? = null
    var sample = false

    if (fc in READ_FC_NUMBERS) {
        // 读位按位数向上取整到字节；读寄存器按帧里实际读的寄存器数（值的个数 × 类型跨度）× 2 字节
        val byteCount = if (fc in READ_BIT_FC_NUMBERS) {
            ((command.quantity ?: 1) + 7) / 8
        } else {
            frameQuantityOf(command.fc, command.quantity, command.dataType) * 2
        }
        if (byteCount < 1) return null
        body = listOf(slaveId, fc, byteCount) + List(byteCount) { 0 }
        kind = ResponseKind.READ
        sample = true
    } else if (fc == 0x05 || fc == 0x06) {
        // 回显即请求的数据区
        buildData(command)?.let {
            body = listOf(slaveId, fc) + it
            kind = ResponseKind.ECHO
        }
    } else if (fc == 0x0F || fc == 0x10) {
        writeQuantity(command, fc)?.let { quantity ->
            body = listOf(slaveId, fc) + u16((command.start ?: 0).toLong()) + u16(quantity.toLong())
            kind = ResponseKind.ACK
        }
    }

    val echoBody = body ?: return null
    val echoKind = kind ?: return null
    val bytes = withCrc(echoBody)
    return ResponsePreview(
        frame = ResponseFrame(
            bytes = bytes.map { it },
            hex = toHex(bytes),
            count = bytes.size,
            kind = echoKind,
            sample = sample
        ),
        exception = exceptionFrame(slaveId, fc)
    )
}

/* ----------------------------------------------------------------------------------------------
 * 帧结构解析：把已生成的 frame.bytes 按字段切开，附含义与解读，供预览对话框在帧下方展示。
 * 字节切分一律以 frame.bytes 为准（与帧一致，不依赖命令字段重复推算）。
 * ----------------------------------------------------------------------------------------------*/

/** 数据区某两项之间的解读行（线圈位 / 寄存器逐项），供小字号多行渲染 */
private data class DecodeLine(
    /** 线圈偏移 / 寄存器地址（多寄存器为起始地址） */
    val at: String,
    /** 类型 + 字节序（寄存器）；线圈该项为空 */
    val kind: String,
    /** 状态 ON/OFF（线圈）或类型化数值（寄存器） */
    val value: String
)

/**
 * JS `arr.slice(from, to)`：越界自动夹到边界、不抛异常。
 * 帧字节是算出来的、本不该越界，但按 JS 口径兜一层，坏数据不至于把详情页炸掉。
 */
private fun slice(bytes: List<Int>, from: Int, to: Int): List<Int> {
    val start = from.coerceIn(0, bytes.size)
    val end = to.coerceIn(start, bytes.size)
    return bytes.subList(start, end)
}

/** 取字节；null（设备返回后才确定）按 0 计，仅用于读已经确定的前置字段 */
private fun at(bytes: List<Int?>, index: Int): Int = bytes.getOrNull(index) ?: 0

/** CRC16 尾字段（末两字节）；两字节未知时不给解读，由调用方补文案 */
private fun crcPart(bytes: List<Int?>): FramePart {
    val lo = bytes.getOrNull(bytes.size - 2)
    val hi = bytes.getOrNull(bytes.size - 1)
    if (lo == null || hi == null) return FramePart("CRC16", "?? ??")
    val value = ((lo or (hi shl 8)) and 0xFFFF).toString(16).uppercase().padStart(4, '0')
    return FramePart("CRC16", toHex(listOf(lo, hi)), text = "0x$value")
}

/**
 * 把字节流按 大端→小端 拆回无符号整型（[valueBytes] 的逆过程）。
 *
 * 用 Long 而不是 Int：JS 的 `<<` 是 32 位有符号运算，4 字节全 1 会得到 -1，
 * 于是 web 的 uint32/float32 会显示成 `0x-1`（`(-1).toString(16)` 就是 `-1`）。
 * 那是 web 侧的一个显示缺陷，这里按正确口径给出 `0xFFFFFFFF`。
 */
private fun fromWireBytes(bytes: List<Int>, span: Int, byteOrder: String?): Long {
    val order = byteOrder ?: "ABCD"
    val be: List<Int> = if (span == 1) {
        val a = bytes[0]
        val b = bytes[1]
        if (order == "DCBA") listOf(b, a) else listOf(a, b)
    } else {
        val a = bytes[0]
        val b = bytes[1]
        val c = bytes[2]
        val d = bytes[3]
        when (order) {
            "DCBA" -> listOf(d, c, b, a)
            "BADC" -> listOf(b, a, d, c)
            "CDAB" -> listOf(c, d, a, b)
            else -> listOf(a, b, c, d)
        }
    }
    var u = 0L
    for (x in be) {
        u = (u shl 8) or (x.toLong() and 0xFF)
    }
    return u
}

/** 位模式 → 展示文本：有符号整型按补码解释，其余（无符号 / 浮点）给位模式十六进制 */
private fun decodeValue(u: Long, type: String, bits: Int): String {
    if (type == "int16" || type == "int32") {
        val mod = 1L shl bits
        return if (u >= mod / 2) (u - mod).toString() else u.toString()
    }
    return "0x" + u.toString(16).uppercase().padStart(bits / 4, '0')
}

/** 线圈数据区 → 每偏移一位的解读 */
private fun coilDecodeLines(data: List<Int>, quantity: Int): List<DecodeLine> {
    val lines = mutableListOf<DecodeLine>()
    val count = minOf(quantity, data.size * 8)
    for (offset in 0 until maxOf(0, count)) {
        val on = ((data[offset shr 3] shr (offset and 7)) and 1) == 1
        lines += DecodeLine(offset.toString(), "", if (on) "ON" else "OFF")
    }
    return lines
}

/** 寄存器数据区 → 逐寄存器条目的解读（地址/类型/字节序 → 还原数值） */
private fun registerDecodeLines(command: ModbusCommand, data: List<Int>): List<DecodeLine> {
    val lines = mutableListOf<DecodeLine>()
    var registerAddress = command.start ?: 0
    var cursor = 0
    for (item in command.registers) {
        val span = registerSpan(item.dataType) ?: 1
        val chunk = slice(data, cursor, cursor + span * 2)
        val type = item.dataType ?: "int16"
        val order = item.byteOrder ?: "ABCD"
        val at = if (span > 1) "$registerAddress-${registerAddress + span - 1}" else "$registerAddress"
        if (chunk.size == span * 2) {
            val value = decodeValue(fromWireBytes(chunk, span, order), type, span * 8)
            lines += DecodeLine(at, "$type $order", value)
        }
        cursor += span * 2
        registerAddress += span
    }
    return lines
}

/** 读应答的字段名称（按功能码口径补齐，旧数据留空则用默认名） */
private fun readFieldNames(command: ModbusCommand): List<String> = fitFieldNames(
    command.fieldNames,
    expectedFieldCount(command.fc, command.quantity, command.dataType),
    fieldBaseName(command.name)
)

/**
 * 读位命令的位名称：偏移 → 位名称（只含命名了的位）。
 * 与生成服务时 `response[].bit-list` 同一套。
 */
private fun readBitNames(command: ModbusCommand): Map<Int, String> {
    val names = mutableMapOf<Int, String>()
    for (bit in fitBitNames(command.bitNames, expectedBitCount(command.fc, command.quantity))) {
        names[bit.offset ?: 0] = bit.name.orEmpty()
    }
    return names
}

/**
 * 读应答数据区 → 逐项解读：读位按位、读寄存器按点表声明的类型与字节序切分。
 *
 * 值来自示例数据区（真实数据由设备返回），故这里展示的是「将来怎么解」；
 * 多值读（数量 > 1）每行前面标上应答字段名（读位只标命名了的位），与生成的服务字段一一对照。
 */
private fun readDataLines(command: ModbusCommand, data: List<Int>): List<String> {
    val quantity = command.quantity ?: 1
    val fc = command.fc?.toIntOrNull(16)
    if (fc != null && fc in READ_BIT_FC_NUMBERS) {
        val names = readBitNames(command)
        // 第 i 行就是偏移 i 那一位（coilDecodeLines 从 0 顺序生成），不必回头解析 at
        return coilDecodeLines(data, quantity).mapIndexed { index, line ->
            val label = names[index]
            if (label.isNullOrEmpty()) {
                "${line.at}=${line.value}"
            } else {
                "$label  ${line.at}=${line.value}"
            }
        }
    }

    val type = command.dataType ?: "int16"
    if (type == "string") {
        // string 不按寄存器切分，示例值（全 0）也没有展示意义，只给跨度
        val spanText = if (quantity > 1) "0-${quantity - 1}" else "0"
        return listOf("$spanText  string ${quantity * 2}B")
    }

    val names = readFieldNames(command)
    val order = command.byteOrder ?: "ABCD"
    val suffix = listOfNotNull(
        // scale 走 numberText：Gson 把 JSON 数字都解成 Double，1 会显示成 ×1.0，而 web 是 ×1
        command.scale?.let { "×${numberText(it)}" },
        command.unit?.takeIf { it.isNotEmpty() }
    ).joinToString(" ")

    val span = registerSpan(type) ?: 1
    val lines = mutableListOf<String>()
    var address = 0
    var index = 0
    var cursor = 0
    while (cursor + span * 2 <= data.size) {
        val chunk = slice(data, cursor, cursor + span * 2)
        val value = decodeValue(fromWireBytes(chunk, span, order), type, span * 8)
        val atLabel = if (span > 1) "$address-${address + span - 1}" else "$address"
        val label = if (names.size > 1) "${names.getOrNull(index).orEmpty()}  " else ""
        lines += "$label$atLabel  $type $order${if (suffix.isEmpty()) "" else " $suffix"} = $value"
        address += span
        index += 1
        cursor += span * 2
    }
    return lines
}

/**
 * 解析请求帧结构：从站/功能码 + 按功能码的地址/数量/数据区（0F/10 含逐位、逐寄存器解读）+ CRC16。
 */
fun describeRequestFrame(command: ModbusCommand, frame: RequestFrame): List<FramePart> {
    val bytes = frame.bytes
    val parts = mutableListOf<FramePart>()

    parts += FramePart("从站地址", toHex(listOf(bytes[0])), text = bytes[0].toString())
    parts += FramePart("功能码", toHex(listOf(bytes[1])), text = fcLabel(command.fc))
    // 起始地址（所有功能码都有）
    parts += FramePart(
        "起始地址",
        toHex(listOf(bytes[2], bytes[3])),
        text = (((bytes[2] shl 8) or bytes[3]) and 0xFFFF).toString()
    )

    when (command.fc) {
        "01", "02", "03", "04" -> {
            val quantity = ((bytes[4] shl 8) or bytes[5]) and 0xFFFF
            parts += FramePart("数量", toHex(listOf(bytes[4], bytes[5])), text = quantity.toString())
        }

        "05" -> {
            val on = bytes[4] == 0xFF && bytes[5] == 0x00
            parts += FramePart(
                "线圈状态",
                toHex(listOf(bytes[4], bytes[5])),
                text = if (on) "ON" else "OFF"
            )
        }

        "06" -> parts += FramePart(
            "寄存器值",
            toHex(listOf(bytes[4], bytes[5])),
            text = (((bytes[4] shl 8) or bytes[5]) and 0xFFFF).toString()
        )

        "0F" -> {
            val quantity = ((bytes[4] shl 8) or bytes[5]) and 0xFFFF
            parts += FramePart("数量", toHex(listOf(bytes[4], bytes[5])), text = quantity.toString())
            parts += FramePart("字节数", toHex(listOf(bytes[6])), text = bytes[6].toString())
            val data = slice(bytes, 7, 7 + bytes[6])
            parts += FramePart(
                "线圈数据",
                toHex(data),
                lines = coilDecodeLines(data, quantity).map { "${it.at}=${it.value}" }
            )
        }

        "10" -> {
            val quantity = ((bytes[4] shl 8) or bytes[5]) and 0xFFFF
            parts += FramePart("数量", toHex(listOf(bytes[4], bytes[5])), text = quantity.toString())
            parts += FramePart("字节数", toHex(listOf(bytes[6])), text = bytes[6].toString())
            val data = slice(bytes, 7, 7 + bytes[6])
            parts += FramePart(
                "寄存器数据",
                toHex(data),
                lines = registerDecodeLines(command, data).map { "${it.at}  ${it.kind} = ${it.value}" }
            )
        }
    }

    parts += crcPart(bytes.map { it })
    return parts
}

/**
 * 解析应答帧结构：
 * - read：从站地址 / 功能码 / 字节数 / 数据区（按点表声明的类型与字节序逐项解读）/ CRC16；
 * - echo：05/06 的应答与请求逐字节相同，直接复用请求帧那套字段解析；
 * - ack：0F/10 只回显 起始地址 + 数量；
 * - exception：从站地址 / 功能码（名称 + 异常）/ 异常码（`??`，附常见含义）/ CRC16。
 */
fun describeResponseFrame(command: ModbusCommand, frame: ResponseFrame): List<FramePart> {
    val bytes = frame.bytes

    // 逐字节回显的帧不含未知字节，按请求帧同一套字段解析
    if (frame.kind == ResponseKind.ECHO) {
        return describeRequestFrame(
            command,
            RequestFrame(bytes.filterNotNull(), frame.hex, frame.count)
        )
    }

    val parts = mutableListOf(
        FramePart("从站地址", toHex(listOf(at(bytes, 0))), text = at(bytes, 0).toString())
    )

    if (frame.kind == ResponseKind.EXCEPTION) {
        parts += FramePart(
            "功能码",
            toHex(listOf(at(bytes, 1))),
            text = "${fcLabel(command.fc)} + 异常"
        )
        parts += FramePart("异常码", "??", text = FRAME_EXCEPTION_CODES)
        val crc = crcPart(bytes)
        parts += if (crc.text == null) crc.copy(text = "待设备返回") else crc
        return parts
    }

    parts += FramePart("功能码", toHex(listOf(at(bytes, 1))), text = fcLabel(command.fc))

    if (frame.kind == ResponseKind.ACK) {
        parts += FramePart(
            "起始地址",
            toHex(listOf(at(bytes, 2), at(bytes, 3))),
            text = (((at(bytes, 2) shl 8) or at(bytes, 3)) and 0xFFFF).toString()
        )
        parts += FramePart(
            "数量",
            toHex(listOf(at(bytes, 4), at(bytes, 5))),
            text = (((at(bytes, 4) shl 8) or at(bytes, 5)) and 0xFFFF).toString()
        )
        parts += crcPart(bytes)
        return parts
    }

    // read：字节数 + 数据区 + CRC16
    val byteCount = at(bytes, 2)
    parts += FramePart("字节数", toHex(listOf(byteCount)), text = byteCount.toString())
    val data = slice(bytes.map { it ?: 0 }, 3, 3 + byteCount)
    parts += FramePart("数据区", toHex(data), lines = readDataLines(command, data))
    parts += crcPart(bytes)
    return parts
}
