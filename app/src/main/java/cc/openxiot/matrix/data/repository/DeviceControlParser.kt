package cc.openxiot.matrix.data.repository

/**
 * 通用设备控制：把产品实例（`GET /product/instance/one/{type}` 返回的裸 Map）手动解析成
 * 可渲染的只读视图模型，控制映射与 webapp-matrix `DeviceControllerComponent.buildProp` 逐字对齐
 * （guide §5.2）：仅可写属性给控件，不可写一律只读文本。
 *
 *   bool → 开关；value-list >2 → 下拉；≤2 → 分段；value-range → −/+ 步进；hex → HEX 文本；
 *   string → 文本；其余数值 → 数字输入。
 *
 * 为什么手动解析而不是用 `cc.openxiot:xiot-spec` 的类型模型：那个库只有模型类 + Tlv8 解码器，
 * **没有产品实例的 JSON codec** —— 硬凑要么没的解码入口，要么得造轮子。本工程其余地方
 * （[ProductSpecRepository]）本来就在用裸 Map 解析实例，这里照同一套来最贴近现状。解析是纯
 * Kotlin、无副作用，便于单测和复用。
 *
 * 命名与 [ProductSpecRepository] 的既有口径一致：`description` 按 zh-CN 取，没有退回任一语言。
 * 服务端下发的 name / description / unit / enum 标签都是**数据，原样上屏、永不翻译**（AGENTS.md）。
 */

/** 可写属性的控件形态；null = 只读展示。 */
enum class ControlKind { SWITCH, SEG, SEL, STEP, NUMBER, TEXT, HEX }

/** 一个枚举项：`value` 保持服务端原类型，`label` 是展示名。 */
data class EnumValue(val value: Any?, val label: String)

/** 区间约束（wire 是 `[min]` 或 `[min,max]` 或 `[min,max,step]` 数组）。 */
data class ValueRange(val min: Double, val max: Double, val step: Double?)

/** 一个可渲染属性。`kind == null` = 只读文本。 */
data class ControlProp(
    val siid: Int,
    val iid: Int,
    /** 状态键 `${siid}.${iid}`，与 web 端同口径 */
    val key: String,
    val name: String,
    val format: String,
    val writable: Boolean,
    val readable: Boolean,
    /** 信息卡「点亮设备」属性（type 为 identify） */
    val identify: Boolean,
    val unit: String?,
    val list: List<EnumValue>?,
    val range: ValueRange?,
    val kind: ControlKind?,
)

/** 一个方法入参。 */
data class ControlArg(
    val piid: Int,
    val name: String,
    val format: String,
    val list: List<EnumValue>?,
)

/** 一个可执行方法。 */
data class ControlAction(
    val siid: Int,
    val iid: Int,
    val key: String,
    val name: String,
    val args: List<ControlArg>,
)

/** 一个服务 = 一张卡片。 */
data class ControlService(
    val iid: Int,
    val name: String,
    val isInfo: Boolean,
    /** 信息卡英文副标题（Device / Accessory Information） */
    val en: String,
    val props: List<ControlProp>,
    val actions: List<ControlAction>,
)

/**
 * 解析器入口。[parse] 返回按服务展开的卡片列表。
 */
object DeviceControlParser {

    private val INFO_SERVICE = Regex("(accessory|device)-information")
    private val DEVICE_INFO = Regex("(^|-)device-information$")

    fun parse(data: Map<String, Any?>?): List<ControlService> {
        // wire 上 services/properties/actions 都是**数组**（`ServiceCodec.decodeArray` /
        // `PropertyCodec.decodeArray`），每一项是一个对象；库把数组 wrap 成 Map，但线上是数组。
        val services = data?.get("services") as? List<*> ?: return emptyList()
        val out = mutableListOf<ControlService>()
        for (anySvc in services) {
            if (anySvc !is Map<*, *>) continue
            val svc = parseService(anySvc) ?: continue
            out += svc
        }
        return out
    }

    private fun parseService(svc: Map<*, *>): ControlService? {
        val siid = (svc["iid"] as? Number)?.toInt() ?: return null
        val typeName = (svc["type"] as? String).orEmpty()
        val isInfo = INFO_SERVICE.containsMatchIn(typeName)
        val en = if (DEVICE_INFO.containsMatchIn(typeName)) {
            "Device Information"
        } else {
            "Accessory Information"
        }
        val name = labelOf(svc["description"] as? Map<*, *>)?.takeIf { it.isNotBlank() } ?: "Service $siid"

        val props = mutableListOf<ControlProp>()
        (svc["properties"] as? List<*>)?.forEach { anyP ->
            if (anyP !is Map<*, *>) return@forEach
            val p = parseProp(siid, anyP) ?: return@forEach
            if (p.writable || p.readable) props += p
        }
        // 入参只带 piid（wire 由 ArgumentCodec 编解码，见 web buildAction）；name/format/list
        // 要从同服务的属性（按 piid 命中）解析。
        val propByPiid = props.associateBy { it.iid }

        val actions = mutableListOf<ControlAction>()
        (svc["actions"] as? List<*>)?.forEach { anyA ->
            if (anyA !is Map<*, *>) return@forEach
            val a = parseAction(siid, anyA, propByPiid) ?: return@forEach
            actions += a
        }

        return ControlService(siid, name, isInfo, en, props, actions)
    }

    private fun parseProp(siid: Int, prop: Map<*, *>): ControlProp? {
        val piid = (prop["iid"] as? Number)?.toInt() ?: return null
        val access = (prop["access"] as? List<*>)?.mapNotNull { it as? String }.orEmpty().toSet()
        val writable = "write" in access
        val readable = "read" in access || "notify" in access
        if (!writable && !readable) return null // 仅作方法入参的属性不占行

        val format = (prop["format"] as? String) ?: "string"
        val isIdentify = (prop["type"] as? String) == "identify"
        val list = parseValueList(prop["value-list"])
        val range = parseValueRange(prop["value-range"])
        val unit = prop["unit"] as? String
        val name = labelOf(prop["description"] as? Map<*, *>)?.takeIf { it.isNotBlank() } ?: "Property $piid"

        val kind = if (writable) controlKindOf(format, list, range) else null
        return ControlProp(
            siid = siid,
            iid = piid,
            key = "$siid.$piid",
            name = name,
            format = format,
            writable = writable,
            readable = readable,
            identify = isIdentify,
            unit = unit,
            list = list,
            range = range,
            kind = kind,
        )
    }

    private fun parseAction(siid: Int, action: Map<*, *>, propByPiid: Map<Int, ControlProp>): ControlAction? {
        val aiid = (action["iid"] as? Number)?.toInt() ?: return null
        val name = labelOf(action["description"] as? Map<*, *>)?.takeIf { it.isNotBlank() } ?: "Action $aiid"
        val args = mutableListOf<ControlArg>()
        (action["in"] as? List<*>)?.forEach { anyArg ->
            val piid = piidOfArg(anyArg) ?: return@forEach
            // 入参是引用属性（web buildAction：`s.properties.get(arg.piid)`），元数据从属性解析
            val prop = propByPiid[piid]
            args += ControlArg(
                piid = piid,
                name = prop?.name ?: "Argument $piid",
                format = prop?.format ?: "string",
                list = prop?.list,
            )
        }
        return ControlAction(siid, aiid, "$siid.$aiid", name, args)
    }

    /** 入参 id：对象 `{piid, repeat}` 或裸数字（ArgumentCodec 两种都收）。 */
    private fun piidOfArg(anyArg: Any?): Int? = when (anyArg) {
        is Number -> anyArg.toInt()
        is Map<*, *> -> (anyArg["piid"] as? Number)?.toInt()
        else -> null
    }

    /** 控制映射（web `buildProp` 同序）：bool→开关；list→seg/sel；range→step；hex→HEX；string→文本。 */
    private fun controlKindOf(format: String, list: List<EnumValue>?, range: ValueRange?): ControlKind {
        if (format == "bool") return ControlKind.SWITCH
        if (!list.isNullOrEmpty()) return if (list.size > 2) ControlKind.SEL else ControlKind.SEG
        if (range != null) return ControlKind.STEP
        if (format == "hex") return ControlKind.HEX
        if (format == "string") return ControlKind.TEXT
        return ControlKind.NUMBER
    }

    private fun parseValueList(raw: Any?): List<EnumValue>? {
        val arr = raw as? List<*> ?: return null
        if (arr.isEmpty()) return null
        val out = mutableListOf<EnumValue>()
        for (v in arr) {
            if (v !is Map<*, *>) continue
            val value = v["value"]
            val label = labelOf(v["description"] as? Map<*, *>)
                ?.takeIf { it.isNotBlank() } ?: (value?.toString().orEmpty())
            out += EnumValue(value, label)
        }
        return if (out.isEmpty()) null else out
    }

    private fun parseValueRange(raw: Any?): ValueRange? {
        val arr = raw as? List<*> ?: return null
        if (arr.size < 2) return null
        val min = (arr[0] as? Number)?.toDouble() ?: return null
        val max = (arr[1] as? Number)?.toDouble() ?: return null
        val step = if (arr.size >= 3) (arr[2] as? Number)?.toDouble() else null
        return ValueRange(min, max, step)
    }

    /** 可写属性的默认值：让控件有可提交的初始状态（不自动写出）。 */
    fun defaultValue(p: ControlProp): Any? = when {
        p.format == "bool" -> false
        !p.list.isNullOrEmpty() -> p.list!!.first().value
        p.range != null -> p.range!!.min
        p.format == "string" || p.format == "hex" -> ""
        else -> 0
    }

    /** 枚举项里命中的标签。 */
    fun listLabel(list: List<EnumValue>?, value: Any?): String? {
        if (list.isNullOrEmpty() || value == null) return null
        return list.firstOrNull { it.value?.toString() == value.toString() }?.label
    }

    private fun labelOf(description: Map<*, *>?): String? {
        if (description.isNullOrEmpty()) return null
        (description["zh-CN"] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        // 没有中文就退回第一个非空语言
        return description.values.asSequence().mapNotNull { it as? String }
            .firstOrNull { it.isNotBlank() }
    }
}