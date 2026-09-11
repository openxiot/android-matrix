package cc.openxiot.wematrix.data.api

import com.google.gson.annotations.SerializedName

/**
 * Modbus 服务：把一条设备点表映射成一组可直接调用的「方法」，字段对齐 webapp-matrix 的
 * `src/app/typedef/define/modbus/ModbusService.ts` 与后端 `ModbusService`。
 *
 * 一次调用 = 一帧请求 + 一条应答解析规则，整份定义在**创建时**由 web 前端一次性展开落库
 * （含依赖设备坐标、含 CRC16 的完整请求帧、应答解析规则），所以本端调用时只读这一条记录，
 * 不回溯点表 / 父设备 / 产品实例，**也不需要自己组 Modbus 帧**。
 *
 * 可空字段一律给默认值：后端只在有值时下发（web 侧同样到处用 `?.` 兜）。
 */

/**
 * 空间图里服务行的精简视图（`ModbusServiceCodec.encodeBrief`）。
 *
 * 与完整定义的差别：字段是**扁平**的（`did` / `spaceId` 不嵌在 `device` 下），
 * 且不含 functions / response —— Mongo 侧投影就挡掉了，几十 KB 的定义不会进空间图响应。
 */
data class ModbusServiceBrief(
    /** 十六进制字符串主键 */
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    /** 固定 "modbus"：与设备混在一张表里渲染时靠它区分 */
    @SerializedName("type") val type: String? = null,
    /** 依赖设备（承载 Modbus 数据的 DTU）的 did */
    @SerializedName("did") val did: String? = null,
    /** 依赖设备落点的空间 id */
    @SerializedName("spaceId") val spaceId: String? = null
)

/**
 * 服务依赖的设备：告诉服务「帧要发给谁、填在哪个入参上」。
 *
 * [argument] 是该方法**入参的 piid**：DTU 承载数据的动作只有一个入参，故提到这一级、
 * 对该服务的所有方法共用（不同型号 piid 不同）。
 */
data class ModbusServiceDevice(
    /** 承载 Modbus 数据的 DTU 的 did */
    @SerializedName("did") val did: String? = null,
    /** 该设备服务 ID（siid） */
    @SerializedName("siid") val siid: Int? = null,
    /** 该设备方法 ID（aiid，通常是 DTU 的“发送”） */
    @SerializedName("aiid") val aiid: Int? = null,
    /** 入参 piid：16 进制请求帧填在这个入参上 */
    @SerializedName("argument") val argument: Int? = null,
    /** 该设备所在的空间（按空间查服务用；不参与校验） */
    @SerializedName("space") val space: DeviceSpaceRef? = null
)

/** 取值表条目：原始值命中 value 时，后端直接以 description 作为字段值返回（不再应用 scale） */
data class ModbusServiceFieldValue(
    @SerializedName("value") val value: Long? = null,
    @SerializedName("description") val description: String? = null
)

/**
 * 应答帧里的一个字段：描述「从数据区第几段开始、多少字节、怎么解」。
 *
 * 一个方法的应答数据区按 [index] 升序、以 [bytes] 依次累加偏移切分。
 * 注意 [scale] 与 [valueList] **由后端在 invoke 时应用**，客户端拿到的是解好的值，
 * 只把 [unit] 取来做展示 —— 不要再缩放一次。
 */
data class ModbusServiceField(
    /** 字段序号（1 起自然数，同一应答内唯一）：决定该字段在数据区里的先后位置 */
    @SerializedName("index") val index: Int? = null,
    /** 字段名称：invoke 返回值的 key */
    @SerializedName("field") val field: String? = null,
    /** 该字段占用的字节数 */
    @SerializedName("bytes") val bytes: Int? = null,
    /** int8 | uint8 | int16 | uint16 | int32 | uint32 | float32 | string */
    @SerializedName("format") val format: String? = null,
    /** 字节序（bytes > 1 时才有）：ABCD 大端 / DCBA 小端 / BADC 字内字节交换 / CDAB 字交换 */
    @SerializedName("byteOrder") val byteOrder: String? = null,
    /** 缩放系数（未命中 valueList 时对读值生效，缺省 1 不缩放） */
    @SerializedName("scale") val scale: Double? = null,
    /** 单位（展示用，如 ℃ / %），不影响取值 */
    @SerializedName("unit") val unit: String? = null,
    /** 线上键名 `value-list` */
    @SerializedName("value-list") val valueList: List<ModbusServiceFieldValue> = emptyList()
)

/**
 * 服务里的一个方法：一次依赖设备调用 = 一帧请求 + 一条应答解析规则。
 *
 * 读方法（点表 fc 01/02/03/04）有 [response]；写方法（05/06/0F/10）的应答是请求回显、
 * 没有读值，[response] 为空数组。
 */
data class ModbusServiceFunction(
    /** 方法序号（1 起自然数，服务内唯一；通常取点表功能码动作的 index） */
    @SerializedName("index") val index: Int? = null,
    /** 方法名称（展示用，如 读蒸发器进水温度） */
    @SerializedName("name") val name: String? = null,
    /** 请求帧：完整的 Modbus RTU 帧 16 进制字符串（含 CRC16），原样交给设备发送 */
    @SerializedName("request") val request: String? = null,
    /** 应答解析规则；空数组表示写方法 */
    @SerializedName("response") val response: List<ModbusServiceField> = emptyList()
)

/** Modbus 服务完整定义（后端 /matrix/v1/modbus/service） */
data class ModbusService(
    /** 十六进制字符串主键（后端生成） */
    @SerializedName("id") val id: String? = null,
    /** 所属组织（取 X-Org-Id，不由请求体决定） */
    @SerializedName("orgId") val orgId: String? = null,
    /** 服务名称（展示用，如 1 号冷水机组） */
    @SerializedName("name") val name: String? = null,
    /** 定义格式版本号：functions 内部结构演进时递增 */
    @SerializedName("version") val version: Int? = null,
    /** 源点表配置 ID（溯源用：本服务由哪条点表映射而来） */
    @SerializedName("configId") val configId: String? = null,
    /** 依赖设备的调用坐标 + 所在空间 */
    @SerializedName("device") val device: ModbusServiceDevice? = null,
    /** 方法列表 */
    @SerializedName("functions") val functions: List<ModbusServiceFunction> = emptyList(),
    @SerializedName("creator") val creator: ModbusPerson? = null,
    @SerializedName("updater") val updater: ModbusPerson? = null
)

/**
 * 调用方法的请求体（对齐后端 `InvokeModbusServiceRequest`）。
 *
 * 线上键名就是 Kotlin 属性名，无需 `@SerializedName`，故照 `MoveDeviceRequest` 的写法放在这里。
 */
data class InvokeModbusServiceRequest(
    /** 服务 id（十六进制字符串） */
    @SerializedName("service") val service: String,
    /** 方法序号（服务内唯一，1 起） */
    @SerializedName("function") val function: Int
)
