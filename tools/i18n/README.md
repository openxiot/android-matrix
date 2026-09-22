# 多语言工具链

把 App 的文案从 2 种语言扩到 **66 种**（对照 `webapp-matrix/public/i18n/` 的语言清单）。
设计依据见 `~/.claude/plans/enumerated-twirling-parnas.md`，本文件只讲**怎么跑**。

## 一句话流程

```
langs.py ──→ extract.py ──→ bundle.py ──→ 【62 个翻译代理】──→ build_xml.py ──→ validate.py
 语言清单      抽源+搬运        摊成 TSV        产出 JSON 译文        合并生成 XML       独立校验
```

## 跑一遍

```bash
cd tools/i18n

python3 langs.py                 # 自检语言清单（66/63/62、目录名唯一、三字母码用 b+）
python3 xmlio.py                 # 自检转义往返（对现有 values/ 全量比对）
python3 build_xml.py --selfcheck # 自检生成链路（用 source.json 重渲染 values-zh 并逐条比）
python3 selftest.py              # 端到端彩排（造假译文跑通 build+validate，含 3 类坏数据）

python3 extract.py               # → out/{skeleton,source,todo}.json + out/reused/<tag>.json
python3 bundle.py                # → out/bundle/*.tsv（喂给翻译代理）
python3 build_xml.py ja de ru    # 生成指定语言（不给参数=全部 62 种）
python3 validate.py              # 独立校验全部语言目录
```

## 各文件的职责

| 文件 | 作用 |
|---|---|
| `langs.py` | **唯一真源**：66 种语言的 web 键 / BCP-47 / 资源目录 / 复数档位 / RTL。下游全部从这里派生，不要另抄一份 |
| `xmlio.py` | Android 字符串资源的读写与转义。**转义只由它做一次**，人手不碰 |
| `extract.py` | 从 `values/`+`values-zh/` 抽源；用「中文值 == web 词典的 key」找出能机械搬运的 223 条 |
| `bundle.py` | 把 328 条待译摊成 TSV，并给出每语言必须产出的复数档位 |
| `build_xml.py` | 合并「搬运的 + 新译的」→ 62 个 `values-*/strings*.xml` |
| `validate.py` | 独立校验：结构 / 键 / 占位符 / 复数档位 / 中文残留 / 繁体 / 抄英文比例 |
| `selftest.py` | 灌真数据前的彩排 |
| `GLOSSARY.md` | 术语表 —— 翻译代理的必读输入，**发给代理时要整份附上** |

## 三条不能忘的约定

**1. 转义永远不经人手。** 翻译代理只产出 JSON 文本，`%` → `%%`、`'` → `\'` 全由
`xmlio.escape` 统一处理。手工写 XML 时漏转一个 `%`，只在真机跑到那一屏才崩
（`UnknownFormatConversionException`），测试抓不到。

**2. 复数档位缺了不会报错。** Android 在语言 XML 里找不到 `few` 档会**静默**回落 `other`，
于是俄语的 5 显示成 «5 товаров» 而不是 «5 товара» —— 语法错的译文，编译、测试、跑起来都正常。
所以 `langs.py` 的 `plurals` 字段是硬约束，`build_xml.py` 与 `validate.py` 都会拦。

**3. Gradle 的 `UP-TO-DATE` 会让门禁静默不跑。** 看到 `BUILD SUCCESSFUL` 不等于测试执行了 ——
要读 `app/build/test-results/testDebugUnitTest/*.xml` 的**计数与时间戳**确认。
`validate.py` 就是这层门禁的独立对照物。

## out/ 里什么入库

`translated/<tag>.json` **要入库**（2 万条译文没有第二个来源，丢了得重跑 62 个代理），
其余都是可重跑的派生物，见 `out/.gitignore`。

## 加一条新文案之后

1. 在 `app/src/main/res/values/` 与 `values-zh/` 各加一条（key 要成对）
2. `python3 extract.py && python3 bundle.py` —— 新条目会落进 `todo.json`
3. 只补译**新增的那几条**（已有的译文在 `out/translated/` 里还在），`build_xml.py` 重新合并
4. `python3 validate.py && ./gradlew testDebugUnitTest`

这也是 `translated/` 入库换来的好处：增量补译，不必全量重来。
