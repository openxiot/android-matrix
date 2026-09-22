"""从 langs.py 生成 AppLanguages.kt —— 64 条语言表。

**为什么要生成而不是手写**：64 条 ×（自称 + 英文名）共 128 个字符串，全是
`Nederlands (België)` `کوردی (Sorani)` `Македонски` 这类没法凭直觉拼的词。
手抄一遍必然有错，而错的后果是**用户看到自己语言的名字是错的** —— 这种错不会
被任何测试抓住（它只是「另一个合法的字符串」），只能靠人恰好认识那种语言。

所以 langs.py 是唯一真源，Kotlin 侧是它的投影。改语言清单改 langs.py，再重跑本脚本。

用法：python3 gen_kotlin.py
"""

import re
from pathlib import Path

import langs

HERE = Path(__file__).resolve().parent
TARGET = HERE.parents[1] / "app/src/main/java/cc/openxiot/wematrix/AppLanguages.kt"

#: 与 NoHardcodedChineseTest 的判定同一个区间
CJK = re.compile(r"[一-鿿]")

#: 自称含汉字的语言要挂这个标记。漏抽门禁只认这一种标记，且**豁免它的下一行**，
#: 所以必须紧贴在那行上面 —— 别在中间插空行或注释。
IGNORE = "// i18n-ignore: 语言自称是数据不是界面文案 —— 任何语言的界面下都显示原文，不抽资源"

HEAD = '''package cc.openxiot.wematrix

/**
 * 支持的语言表。
 *
 * ⚠️ **本文件由 `tools/i18n/gen_kotlin.py` 生成，请勿手改。**
 * 要增删语言或订正语言名，改 `tools/i18n/langs.py` 后重跑该脚本
 * （语言名有 128 个非拉丁词，手抄必错，且错了没有任何测试能发现）。
 *
 * [tag] 是 BCP-47，既用于落盘（`SharedPreferences`）也用于
 * `Locale.forLanguageTag`，与资源目录名是两回事：
 * 三字母代码的资源目录要写 `values-b+kmr`，而 tag 就是 `kmr`。
 */
data class AppLanguage(
    /** BCP-47，落盘与解析都用它 */
    val tag: String,
    /** 自称。本地化界面的惯例：语言列表用**该语言自己的写法**，不翻译 */
    val endonym: String,
    /** 英文名。仅用于搜索命中 —— 让「Japan」「Japanese」都能找到「日本語」 */
    val englishName: String,
    /** 书写方向。与 ICU 的判断由 `AppLanguagesTest` 对照，别只信这里 */
    val isRtl: Boolean,
)

/** 可选语言（64 种）。不含 `en_GB` / `en_AU` —— 与英文 93–96% 相同，有意不做。 */
val APP_LANGUAGES: List<AppLanguage> = listOf(
'''

TAIL = ''')

private val BY_TAG: Map<String, AppLanguage> = APP_LANGUAGES.associateBy { it.tag }

/** 按 tag 查语言；未收录（含 [AppLocale.SYSTEM]）返回 null，由调用方决定兜底文案。 */
fun appLanguage(tag: String): AppLanguage? = BY_TAG[tag]
'''

ROW = '    AppLanguage({tag}, {endo}, {en}, {rtl}),'


def kt(value: str) -> str:
    """Kotlin 字符串字面量。这些语言名里不该有引号或反斜杠 —— 有就报错，别悄悄转义。"""
    if '"' in value or "\\" in value:
        raise ValueError(f"语言名里有需要转义的字符，Kotlin 字面量得另想办法：{value!r}")
    if "$" in value:
        raise ValueError(f"语言名里有 $，会被当成 Kotlin 模板：{value!r}")
    return f'"{value}"'


def check_comments() -> None:
    """KDoc 里不能出现 `*/` —— 它会**提前闭合注释**，后面整段变成语法错误。

    这个坑很自然：本项目到处在讲资源目录，`values-*/` 是个顺手的写法。踩过一次
    （`与资源目录名（`values-*/`）是两回事` → 整个文件报 15 个顶层的语法错误，
    而错误位置指向注释的下一行，看不出是注释的锅）。生成器在这里挡一道，
    免得下次改文案时再踩。
    """
    for name, block in (("HEAD", HEAD), ("TAIL", TAIL)):
        for line in block.splitlines():
            s = line.strip()
            if s.startswith("/**") and s.endswith("*/"):
                continue  # 单行 KDoc，自成一体，不参与配对
            if "*/" in line and s != "*/":
                raise ValueError(f"{name} 这一行的 `*/` 会提前闭合块注释：{line!r}")
            if "/*" in line and s not in ("/*", "/**"):
                raise ValueError(f"{name} 这一行的 `/*` 会嵌套出问题：{line!r}")


def main() -> None:
    check_comments()
    rows = []
    ignored = []
    for x in langs.LANGS:
        if x.skip:
            continue
        # 汉字自称（只有 zh / zh-TW / zh-HK 三种）会被漏抽门禁当成没抽的界面文案拦下。
        # 它确实是**数据**：语言列表按惯例用该语言自己的写法，永远不翻译。
        if CJK.search(x.endonym):
            rows.append(IGNORE)
            ignored.append(x.bcp47)
        rows.append(
            ROW.format(
                tag=kt(x.bcp47),
                endo=kt(x.endonym),
                en=kt(x.english),
                rtl="true" if x.rtl else "false",
            )
        )

    # 与 app 目录里的复数档位无关，这里只求「可选语言」这一个数对得上
    assert len(rows) == 64 + len(ignored), f"应为 64 种可选语言，实为 {len(rows) - len(ignored)}"

    TARGET.write_text(HEAD + "\n".join(rows) + "\n" + TAIL, encoding="utf-8")
    print(f"✓ 生成 {TARGET.relative_to(HERE.parents[1])}")
    print(f"  {len(rows) - len(ignored)} 种可选语言，"
          f"其中 RTL {sum(1 for x in langs.LANGS if x.rtl and not x.skip)} 种")
    print(f"  挂 i18n-ignore 的汉字自称：{', '.join(ignored)}（共 {len(ignored)} 处）")
    print(f"  跳过：{', '.join(x.bcp47 for x in langs.LANGS if x.skip)}")


if __name__ == "__main__":
    main()
