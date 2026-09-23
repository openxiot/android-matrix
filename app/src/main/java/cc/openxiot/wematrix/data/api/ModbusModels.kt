package cc.openxiot.wematrix.data.api

import com.google.gson.annotations.SerializedName

/**
 * Modbus 设备点表（以功能码为中心），字段对齐 webapp-matrix 的
 * `src/app/typedef/define/modbus/Modbus.ts` 与后端 `ModbusConfig`。
 *
 * 一行 = 一个功能码动作：读（quantity = 该参数占用的寄存器/位个数）或写
 * （05 单线圈 / 06 单寄存器 / 0F 多线圈 / 10 多寄存器）。读写方向与寄存器区由功能码决定，
 * 逻辑地址不落库，由 fc + start 实时换算（见 ModbusFormat.logicalAddressOf）。
 *
 * 可空字段一律给默认值：后端只在有值时下发（web 侧同样到处用 `?.` 兜）。
 */

/** 0F 写多线圈的单个线圈 */
data class ModbusCoilItem(
    /** 相对偏移（0 基，< 线圈数量） */
    @SerializedName("offset") val offset: Int? = null,
    /** true=ON 打开 / false=OFF 关闭 */
    @SerializedName("on") val on: Boolean? = null
)

/**
 * 01/02 读位的单个位名称（位区按位打包，逐位取值靠它命名）。
 *
 * 与 [ModbusCoilItem.offset] 同口径（0 基、从起始地址起）：一边是写多线圈的偏移，
 * 一边是读位区的偏移，同一个位区里这两套偏移是能对上的。
 */
data class ModbusBitName(
    /** 位偏移（0 基，从起始地址起、< 位/线圈个数） */
    @SerializedName("offset") val offset: Int? = null,
    /** 位名称（生成服务时作为该位的应答字段名，取值 0/1） */
    @SerializedName("name") val name: String? = null
)

/** 10 写多寄存器的单个寄存器条目 */
data class ModbusRegisterItem(
    /** 数据地址（0 基） */
    @SerializedName("address") val address: Int? = null,
    /** int16 | uint16 | int32 | uint32 | float32 */
    @SerializedName("dataType") val dataType: String? = null,
    @SerializedName("byteOrder") val byteOrder: String? = null,
    /**
     * 原始数值。用 Long 而不是 Int：uint32/float32 允许 0..0xFFFFFFFF 的位模式，
     * 4294967295 放进 Int 会溢出。
     */
    @SerializedName("value") val value: Long? = null
)

/**
 * 单个功能码动作（字段按功能码分组出现，用不到的为空）。
 *
 * 字段归属：
 * - 01/02 读位：`quantity`（位/线圈个数）/ `bitNames`
 * - 03/04 读寄存器：`quantity`（**值的个数**）/ `fieldNames` / `dataType` / `byteOrder` / `scale` / `unit`
 * - 05 写单线圈：`coilState`
 * - 06 写单寄存器：`registerValue`
 * - 0F 写多线圈：`coils`
 * - 10 写多寄存器：`registers`
 */
data class ModbusCommand(
    @SerializedName("name") val name: String? = null,
    /** 01|02|03|04|05|06|0F|10 */
    @SerializedName("fc") val fc: String? = null,
    /** 序号（1 起）：功能码动作在点表内的顺序，列表按它排 */
    @SerializedName("index") val index: Int? = null,
    /** 起始地址 = 0 基数据地址（线上值） */
    @SerializedName("start") val start: Int? = null,
    /**
     * 数量：03/04 为**值的个数**（每个值占数据格式的寄存器跨度 int16/uint16→1、
     * int32/uint32/float32→2；string 时即字符串长度、整段算一个值，故恒为 1 个字段）；
     * 01/02 为位/线圈个数。
     *
     * **请求帧里的数量不等于本值**：03/04 非 string 时要乘跨度（见 `frameQuantityOf`）。
     */
    @SerializedName("quantity") val quantity: Int? = null,
    /**
     * 应答字段名称（03/04 读寄存器）：个数即应答字段数 —— 非 string 为 quantity 个、string 为 1 个。
     * 01/02 读位改用 [bitNames]；写操作没有应答字段。
     */
    @SerializedName("fieldNames") val fieldNames: List<String> = emptyList(),
    /**
     * 位名称（01/02 读位）：给读回的位区里的若干位各自命名，生成服务时逐位填进应答字段的位清单；
     * 缺省表示只按整段位掩码出一个字段。
     */
    @SerializedName("bitNames") val bitNames: List<ModbusBitName> = emptyList(),
    @SerializedName("dataType") val dataType: String? = null,
    /** ABCD | DCBA | BADC | CDAB */
    @SerializedName("byteOrder") val byteOrder: String? = null,
    /** 缩放系数，可为小数（如 0.1） */
    @SerializedName("scale") val scale: Double? = null,
    @SerializedName("unit") val unit: String? = null,
    /** on | off，仅 05 */
    @SerializedName("coilState") val coilState: String? = null,
    /** 仅 06。同 ModbusRegisterItem.value：按 32 位位模式取值，用 Long */
    @SerializedName("registerValue") val registerValue: Long? = null,
    /** 仅 0F */
    @SerializedName("coils") val coils: List<ModbusCoilItem> = emptyList(),
    /** 仅 10 */
    @SerializedName("registers") val registers: List<ModbusRegisterItem> = emptyList()
)

/** 从站设备信息（厂家/型号/设备类型/从站地址/描述），服务端收拢在 slave 子对象下 */
data class ModbusSlave(
    /** 厂家/品牌，如 特灵/开利/麦克维尔 */
    @SerializedName("manufacturer") val manufacturer: String? = null,
    /** 设备型号，如 19XRV/CVHG */
    @SerializedName("model") val model: String? = null,
    /** 设备类型（可选，自由字符串，如 离心式冷水机组） */
    @SerializedName("type") val type: String? = null,
    /** Modbus 从站地址 0-247 */
    @SerializedName("slaveId") val slaveId: Int? = null,
    @SerializedName("description") val description: String? = null
)

/** 点表归属主体：一条点表可属于一个组织，也可属于某个人（无组织个人点表）。 */
data class ModbusConfigOwner(
    /** 主体 ID：组织编码 或 账号 ID */
    @SerializedName("id") val id: String? = null,
    /** organization 组织 / user 个人 */
    @SerializedName("type") val type: String? = null,
    /** 名称快照（展示用） */
    @SerializedName("name") val name: String? = null
)

/** 操作人记录：创建者 / 最后更新者 */
data class ModbusPerson(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    /** epoch 毫秒时间戳 */
    @SerializedName("timestamp") val timestamp: Long? = null
)

/** 设备点表配置 */
data class ModbusConfig(
    @SerializedName("id") val id: String? = null,
    /** 归属主体（organization/user；后端以它为鉴权锚点） */
    @SerializedName("owner") val owner: ModbusConfigOwner? = null,
    /** 从站设备信息 */
    @SerializedName("slave") val slave: ModbusSlave? = null,
    /** private 私有 / public 公开 */
    @SerializedName("visibility") val visibility: String? = null,
    /** development 开发 / preview 预览 / released 已发布；缺省按开发展示 */
    @SerializedName("lifecycle") val lifecycle: String? = null,
    @SerializedName("commands") val commands: List<ModbusCommand> = emptyList(),
    @SerializedName("creator") val creator: ModbusPerson? = null,
    @SerializedName("updater") val updater: ModbusPerson? = null
) {
    /**
     * 列表标题：`厂家 型号`，两者都缺时退回 id，id 也没有则给 null
     * （口径同 web 表格的两列合并显示）。
     *
     * 兜底那半句「未命名点表」是**要翻译的文案**，而这一层既拿不到 Context、
     * 也不该引 ui/core/UiText（分层），故留成 null 由界面补
     * —— 与 [Product.displayName] 同一处理。
     */
    val displayName: String?
        get() {
            val title = listOfNotNull(slave?.manufacturer, slave?.model)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (title.isNotBlank()) return title
            return id?.takeIf { it.isNotBlank() }
        }
}
