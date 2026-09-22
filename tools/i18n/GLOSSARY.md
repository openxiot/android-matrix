# 术语表（翻译时逐条遵守）

**这不是我发明的术语，是从 `values/` 里反推出来的既有选择。** `values/` 那 551 条英文
已经是权威 UI 文案，下面每条都标注了它在英文侧实际用的词 —— 术语表的作用只是**把这个
既成事实写下来**，免得 62 个语言各译各的（同一个「空间」在德语里一半是 `Bereich`
一半是 `Raum`）。

规则三条：

1. **概念一致 > 字面漂亮。** 同一列里的词，在任何上下文都用**同一个**目标语词，不要为了
   句子通顺换同义词。用户会把「空间」「组织」「项目」当成三个不同的东西来记。
2. **专有名词不译。** 见文末「不译清单」。
3. **英文列是权威。** 中文列只用来消歧义（「服务」在中文里既可指 Modbus 服务也可指
   服务器，看英文列就知道是前者）。译文以**英文**为准，不要照中文的字面结构译。

---

## 一、领域名词

| 英文（权威） | 中文 | 说明 / 目标语要点 |
|---|---|---|
| device | 设备 | 平台的受管终端。**不要**译成「仪器 / 装置 / 机器」 |
| space | 空间 | 设备的归属容器，层级在 project 之下。是个**专有概念**，不是物理空间 |
| organization | 组织 | 顶层租户 |
| project | 项目 | 层级：organization → project → space → device |
| member | 成员 | project 的成员 |
| register map | 点表 | 中文叫点表、英文叫 register map。**不要**望文生义译成 table/diagram |
| service | 服务 | Modbus 服务（一组功能码的集合），**不是** server/service daemon |
| function code | 功能码 | Modbus 协议概念，FC01/03/05/06… |
| coil | 线圈 | Modbus 协议术语。各语言多用本地既定译法（见下「目标语既定译法」） |
| holding register | 保持寄存器 | Modbus 协议术语 |
| slave address | 从站地址 | Modbus 主从模型。用协议规范的既定译法 |
| sampling | 采集 | 周期性拉取设备数据 |
| alarm | 告警 | **不是**警报/预警的日用词义，是平台的告警记录 |
| dashboard | 看板 | 用户自定义的卡片布局 |
| field | 字段 | 采集数据里的一个测点 |
| reading | 读数 | 单个采集到的值 |
| history | 历史 | 历史数据 |
| curve / chart | 曲线 / 图表 | 英文侧统一用 chart（动词短语 "can be charted"）；标题类可用 curve |
| threshold | 阈值 | 注意：英文侧大量用 include/exclude 表达，未见 threshold 出现，若遇中文「阈值」按英文侧对应词走 |
| scale | 缩放 | 采集值的换算系数 |
| unit | 单位 | 工程单位（V / A / °C），**不译**，见不译清单 |
| decimal places | 小数位 | |
| product | 产品 | 设备型号 |
| rule | 规则 | 联动规则 |
| QR code | 二维码 | |
| torch | 手电筒 | 扫码页的补光灯。英式英语已选 torch（非 flashlight），其他语言按本地惯例 |
| permission | 权限 | |
| role | 角色 | owner / admin / member 等 |

## 二、界面动作（都是高频动词，务必全语言一致）

| 英文 | 中文 | 备注 |
|---|---|---|
| refresh | 刷新 | |
| retry | 重试 | |
| cancel | 取消 | |
| confirm / OK | 确认 | |
| save | 保存 | |
| delete | 删除 | |
| add | 添加 | |
| edit | 编辑 | |
| rename | 重命名 | 滑动行动作，见下 |
| sign in | 登录 | |
| download | 下载 | |
| install | 安装 | |
| update | 更新 | |
| select | 选择 | 下拉/选择器 |
| search | 搜索 | |
| copy | 复制 | |

## 三、界面名词

| 英文 | 中文 | 备注 |
|---|---|---|
| name | 名称 | |
| type | 类型 | |
| status | 状态 | |
| version | 版本 | |
| account | 账号 | |
| password | 密码 | |
| address | 地址 | |
| port | 端口 | |
| max / min / average | 最大 / 最小 / 平均 | 图例用词 |
| input / output | 输入 / 输出 | |
| succeeded / failed | 成功 / 失败 | 英文侧错误一律 `Couldn't …` 句式，见下 |

---

## 四、句式约定

这几条比词更影响观感，**每条都要照做**：

1. **错误文案统一 `Couldn't …` 句式。** 英文侧 88 条错误全部形如
   `Couldn't load the register map` / `Couldn't load spaces`。译文照这个**语气**
   （简短、口语、不用「错误：」开头、不加感叹号），不要译成正式的
   `Failed to retrieve the list of organizations`。
2. **占位符逐字保留。** `%1$s` `%2$d` `%%` 原样搬过去，一个字符都不能改
   （`%s` 不能改成 `%d`）。参数**顺序**可以按目标语语法调整（这正是 `%1$s` 带序号的原因），
   但序号与类型的对应关系不能变。**不许多加占位符，也不许少。**
3. **句首字母大小写** 按目标语习惯（德语名词大写、法语小写），不要照抄英文的
   Title Case。
4. **标点**用目标语的（中文「，」。法语用不间断空格 + `!?;:`，阿拉伯语用 `،`）。
   不要中英标点混用。
5. **长度**：移动端。英文原句已经是精简过的，译文不要比英文长 50% 以上；
   控件标题（tab、按钮）尽量与英文等长或更短。
6. **不要译出英文里没有的东西**：不加「请」「您」「抱歉」这类礼貌填充，
   英文侧没有 please/sorry。

## 五、不译清单（保留原文）

| 类别 | 例子 |
|---|---|
| 品牌 | WeMatrix、WeMatrix 相关的所有自指 |
| 协议 | Modbus、Modbus TCP、Modbus RTU、MQTT、HTTP、HTTPS、TCP、IP、WebSocket |
| 专有名词 | JSON、UTF-8、CRC、ASCII、Hex |
| 单位 / 符号 | V、A、mA、kV、kW、°C、Hz、Ω、s、ms、%、/ |
| 技术标识 | 寄存器地址数字、功能码 `FC01`、二维码里的内容 |
| 缩写 | ID、URL、QR、IP、MAC、UUID |
| 已经是符号的 | `OK`、`——`、`·`、`…`、逗号分隔符 |

**例外**：`ID` 在部分语言有既定译法（法语 `identifiant`、德语 `ID`），
按该语言惯例走；但同一个语言内部必须统一。

## 六、目标语既定译法（协议术语 —— 有标准答案，别自创）

Modbus 是有官方译法/行业惯用译法的协议，这几个词**优先用下表**，没有对应语言的
再按该语言工程界惯例：

| 英文 | 中 | 日 | 韩 | 德 | 法 | 西 | 葡 | 俄 |
|---|---|---|---|---|---|---|---|---|
| coil | 线圈 | コイル | 코일 | Spule | bobine | bobina | bobina | катушка |
| holding register | 保持寄存器 | 保持レジスタ | 홀딩 레지스터 | Holding-Register | registre de maintien | registro de retención | registo de retenção | регистр хранения |
| slave | 从站 | スレーブ | 슬레이브 | Slave | esclave | esclavo | escravo | слейв |
| function code | 功能码 | ファンクションコード | 기능 코드 | Funktionscode | code de fonction | código de función | código de função | код функции |

其余语言（阿拉伯语、泰语、越南语等）同样**优先查 Modbus 规范在该语言的通行译法**；
实在没有通行译法的，保留英文原词比自创好 —— 工程师看得懂 `coil`，看不懂生造词。

---

## 附：这份表怎么用

翻译代理拿到的提示词会**整份附上本文件**，外加：

- 源包（`key | 英文 | 中文` 三元组，见 `out/todo.json`）
- 该语言的 CLDR 复数类别（见 `langs.py` 的 `plurals` 字段）
- 输出契约（只吐 `{key: 译文}`，键集必须与源包完全相等）

译文只产出 JSON，**XML 由 `build_xml.py` 统一生成** —— 转义、占位符、文件切分都不经人手。
