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
 * 一个出值的阈值告警规则，是**一组**（[ModbusServiceField.alarms]）里的一条。
 * 分级配置就靠这个数组表达：温度「低于 20 告警 / 超过 26 提示 / 超过 28 警告 / 超过 30 严重」
 * 是同一个出值上的四条规则。
 *
 * 客户端**只读**：增删改都在 web 的点表/服务编辑器里做，本端仅把配了什么显示出来。
 * 尤其 [id] 别拿去当 key 之外用 —— 它是「这条开着的告警是哪条规则开的」的身份凭据。
 */
data class ModbusServiceFieldAlarm(
    /** 规则身份（前端生成、随配置落库）；后端只校验、不下发回前端 */
    @SerializedName("id") val id: String? = null,
    /** 缺省 / false = 不告警，**配置原样留着**（与 interval 配了却暂停轮询同口径） */
    @SerializedName("enabled") val enabled: Boolean? = null,
    /** `>` 超过 / `>=` 达到 / `<` 低于 / `<=` 低于等于 / `=` 等于（符号不翻译） */
    @SerializedName("compare") val compare: String? = null,
    /** 数值阈值；`=` 且字段带取值表时改用 [state] */
    @SerializedName("threshold") val threshold: Double? = null,
    /** `=` 的比较目标：取值表里的 description（**服务端数据，原样显示**） */
    @SerializedName("state") val state: String? = null,
    /** INFO 提示 / WARN 警告 / CRITICAL 严重 */
    @SerializedName("level") val level: String? = null,
    /** 告警文本（用户自己填的，如「温度过高」）—— **用户数据，永不翻译** */
    @SerializedName("text") val text: String? = null
)

/**
 * 位区字段里的具名位：除字段自身那份整段位掩码外，把该位单独作为一个 0/1 取值输出（key 即 [field]）。
 *
 * 偏移是 0 基、从位区起点（请求的起始地址）算起；帧内按 LSB-first 取位，
 * 即第 `offset/8` 个数据字节的第 `offset%8` 位。
 */
data class ModbusServiceFieldBit(
    @SerializedName("offset") val offset: Int? = null,
    /** 该位的取值名：invoke 返回值里这个位的 key（**服务端数据，原样显示**） */
    @SerializedName("field") val field: String? = null,
    /** 该位自己的一组告警规则（位是独立的结果键，比的是那一位的 0/1） */
    @SerializedName("alarms") val alarms: List<ModbusServiceFieldAlarm> = emptyList()
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
    @SerializedName("value-list") val valueList: List<ModbusServiceFieldValue> = emptyList(),
    /** 线上键名 `bit-list`（01/02 位区逐位取值；缺省 = 只按整段位掩码出一个字段） */
    @SerializedName("bit-list") val bitList: List<ModbusServiceFieldBit> = emptyList(),
    /** 该字段出值的一组阈值告警规则，按声明顺序排列（缺省 / 空 = 没配） */
    @SerializedName("alarms") val alarms: List<ModbusServiceFieldAlarm> = emptyList()
)

/**
 * v2（version == 2）起：请求是**结构化定义**，不再是整串 hex 帧。
 * 帧由后端在 invoke 时现组（含 CRC16），前端不再自己算。方向由 [fc] 直接得出
 * （01/02/03/04 读、05/06/0F/10 写），不再解析 hex 串。
 */
data class ModbusFunctionRequest(
    /** 从站地址（帧首字节） */
    @SerializedName("slaveId") val slaveId: Int? = null,
    /** 功能码（读 01/02/03/04、写 05/06/0F/10） */
    @SerializedName("fc") val fc: String? = null,
    /** 起始地址（0 基数据地址） */
    @SerializedName("start") val start: Int? = null,
    /** 帧里那个数量字段的**字面值**：读方法（03/04 = 寄存器数、01/02 = 位数）才有；写方法不写 */
    @SerializedName("quantity") val quantity: Int? = null,
    /** 写方法的写入字段；读方法不写 */
    @SerializedName("fields") val fields: List<ModbusFunctionRequestField>? = null
)

/** 写方法的一个写入字段（fc 05/06/0F/10）。[value] 是缺省值：invoke 时人没填就用它。 */
data class ModbusFunctionRequestField(
    /** 字段序号（1 起自然数，写入字段内唯一） */
    @SerializedName("index") val index: Int? = null,
    /** 字段名称（invoke 时 `values` 里的 key） */
    @SerializedName("field") val field: String? = null,
    /** 偏移（0F 位偏移 / 10 寄存器偏移，相对 start，从 0 起）；05/06 写单值，不许填 */
    @SerializedName("offset") val offset: Int? = null,
    /** bit | int16 | uint16 | ...（05/06 只有 bit / int16 / uint16） */
    @SerializedName("format") val format: String? = null,
    /** 字节序（跨度 4 字节时才要求）；bits 无此键 */
    @SerializedName("byteOrder") val byteOrder: String? = null,
    /**
     * 缺省值：`Boolean`（bit）或 `Number`（寄存器）。**别用 `||` 判空** —— `false` / `0` 是有效值。
     * 类型不固定故收 [Any]；invoke 时原样回填。
     */
    @SerializedName("value") val value: Any? = null
)

/**
 * 应答定义（与 request 对称）：boolean-ish 的 `{fields:[...]}`。
 * 写方法的应答是请求回显、没有读值，**整个 response 键都不存在** → [ModbusServiceFunction.response] == null。
 * [fields] 为 null / 空时 codec 不出键。
 */
data class ModbusFunctionResponse(
    /** 应答解析规则；空表表示读方法没有可解析的出值 */
    @SerializedName("fields") val fields: List<ModbusServiceField>? = null
)

/**
 * 服务里的一个方法：一次依赖设备调用 = 一帧请求 + 一条应答解析规则。
 *
 * 读方法（点表 fc 01/02/03/04）有 [response]；写方法（05/06/0F/10）的应答是请求回显、
 * 没有读值，[response] 整个没有（null）。
 */
data class ModbusServiceFunction(
    /** 方法序号（1 起自然数，服务内唯一；通常取点表功能码动作的 index） */
    @SerializedName("index") val index: Int? = null,
    /** 方法名称（展示用，如 读蒸发器进水温度） */
    @SerializedName("name") val name: String? = null,
    /** 请求帧的结构化定义（v2）；不再是整串 hex */
    @SerializedName("request") val request: ModbusFunctionRequest? = null,
    /**
     * 服务端自动调用本方法的周期（秒）：到点自动 invoke 一次，再按 response 解出字段值；
     * 缺省表示没配周期 —— 后端用 null 表达同一件事，不用 0。取值 5 ~ 3600 秒。
     *
     * **配了周期不等于会跑**：跑不跑看 [polling]。只对读方法有意义（写方法上不会出现）。
     */
    @SerializedName("interval") val interval: Int? = null,
    /**
     * 是否启用自动轮询：true = 按 [interval] 周期调用；false = 保留周期但暂停（随时可再开）；
     * 缺省 = **按 [interval] 判定**，配了周期即启用 —— 与加这个字段之前的定义一致。
     */
    @SerializedName("polling") val polling: Boolean? = null,
    /** 应答定义；null = 写方法（request 回显，无读值） */
    @SerializedName("response") val response: ModbusFunctionResponse? = null
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
 * [values] 只在写方法上传（读方法传了后端会拒）；缺省值随定义下发，人没填时后端回落、
 * 这里不补。
 */
data class InvokeModbusServiceRequest(
    /** 服务 id（十六进制字符串） */
    @SerializedName("service") val service: String,
    /** 方法序号（服务内唯一，1 起） */
    @SerializedName("function") val function: Int,
    /** 写入值（字段名 → 原始值，如 {"开机": true}）；只写方法用 */
    @SerializedName("values") val values: Map<String, Any?>? = null
)
