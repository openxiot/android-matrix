"""Android 字符串资源的读写 + 转义。

**为什么转义要单独成层**：aapt2 的转义（`\\'` `\\"` `%%` `\\n` `\\@`）不是 XML 实体，
`ElementTree` 只解 XML 那一层（`&amp;` `&lt;`），Android 那层原样留在 `.text` 里。
所以读取要「ElementTree + 手工解 Android 转义」，写入要反过来。

**译文一律先解转义再交给翻译**，翻译看到的是干净文本（`Don't`，不是 `Don\'t`）；
写回时由本模块统一重新转义。转义永远不经人手 —— `%` 漏转成 `%%` 这类错误只在
真机跑到那一屏才崩（`UnknownFormatConversionException`），没人会提前发现。

自检见 `python3 -m xmlio`（对现有 values/ 做往返比对）。
"""

import re
import xml.etree.ElementTree as ET
from pathlib import Path

#: 合法的格式说明符。`%%`（字面百分号）不在其中 —— 这正是要与它区分的原因。
SPEC = re.compile(r"%(\d+\$)?[sd]")

#: 语言目录里参与比对的文件（`strings.xml` 也算，别漏）
FILE_GLOB = "strings*.xml"

INDENT = "    "


# ────────────────────────────── 解转义 ──────────────────────────────

_UNESCAPE = {
    "\\": "\\",
    "'": "'",
    '"': '"',
    "n": "\n",
    "t": "\t",
    "@": "@",
    "?": "?",
    "u": None,  # \uXXXX 由下面单独处理
}

_UNICODE = re.compile(r"\\u([0-9a-fA-F]{4})")


def unescape(value: str) -> str:
    """Android 转义 → 干净文本。左到右扫描，避免 `\\\\'` 这类连写被吃错。

    外层双引号**先剥**（在反斜杠处理之前）：aapt 的规则是「首尾都是 `"` 时，这两个
    引号是定界符、不算内容」。若放到反斜杠之后再判，`\\"x\\"`（值本身就是带引号的
    `"x"`）处理完是 `"x"`，会被误当成定界符剥成 `x`。判首尾时要确认闭合引号没被转义。
    """
    if len(value) >= 2 and value[0] == '"' and value[-1] == '"':
        # 闭合引号前若有奇数个反斜杠，说明它本身是被转义的
        backslashes = len(value) - 1 - len(value[:-1].rstrip("\\"))
        if backslashes % 2 == 0:
            value = value[1:-1]
    value = _UNICODE.sub(lambda m: chr(int(m.group(1), 16)), value)
    out = []
    i = 0
    while i < len(value):
        c = value[i]
        if c == "\\" and i + 1 < len(value):
            nxt = value[i + 1]
            if nxt in _UNESCAPE and _UNESCAPE[nxt] is not None:
                out.append(_UNESCAPE[nxt])
                i += 2
                continue
        out.append(c)
        i += 1
    # `%%` 是字面百分号；格式说明符里不会出现 `%%`，故整体替换是安全的
    return "".join(out).replace("%%", "%")


# ────────────────────────────── 转义 ──────────────────────────────

def _escape_percent(value: str) -> str:
    """把**不属于格式说明符**的 `%` 变成 `%%`。`%1$d` 原样保留。"""
    out = []
    i = 0
    for m in SPEC.finditer(value):
        out.append(value[i:m.start()].replace("%", "%%"))
        out.append(m.group(0))
        i = m.end()
    out.append(value[i:].replace("%", "%%"))
    return "".join(out)


def escape(value: str) -> str:
    """干净文本 → 可放进 <string> 的文本（不含外层标签）。

    顺序有讲究：反斜杠必须最先转义，否则后面加进去的转义符会被二次处理。
    """
    value = value.replace("\\", "\\\\")
    value = value.replace("&", "&amp;")
    value = value.replace("<", "&lt;")
    value = value.replace(">", "&gt;")
    value = value.replace('"', '\\"')
    value = value.replace("'", "\\'")
    value = value.replace("\n", "\\n")
    value = value.replace("\t", "\\t")
    value = _escape_percent(value)
    # 行首的 @ / ? 会被 aapt 当成资源引用，必须转义
    if value[:1] in ("@", "?"):
        value = "\\" + value
    # 首尾空白会被 XML 解析器吃掉，用双引号包起来才保得住
    if value != value.strip():
        value = '"' + value + '"'
    return value


# ────────────────────────────── 解析 ──────────────────────────────

class Res:
    """一个语言目录里的全部条目。

    strings: name -> 干净文本
    plurals: name -> {quantity: 干净文本}
    """

    def __init__(self):
        self.strings: dict[str, str] = {}
        self.plurals: dict[str, dict[str, str]] = {}
        self.files: list[str] = []

    def keys(self) -> set[str]:
        return set(self.strings) | set(self.plurals)

    def entries(self) -> int:
        return len(self.strings) + sum(len(v) for v in self.plurals.values())


def parse_dir(path: Path, skip_untranslatable: bool = True) -> Res:
    """读一个 values* 目录下的全部 strings*.xml。

    `skip_untranslatable=True` 时剔除 `translatable="false"` —— 那些不该进语言目录。
    """
    res = Res()
    for f in sorted(path.glob(FILE_GLOB)):
        res.files.append(f.name)
        root = ET.parse(f).getroot()
        for el in root:
            name = el.get("name")
            if not name:
                continue
            if el.tag == "string":
                if skip_untranslatable and el.get("translatable") == "false":
                    continue
                res.strings[name] = unescape(el.text or "")
            elif el.tag == "plurals":
                items = {}
                for item in el:
                    items[item.get("quantity")] = unescape(item.text or "")
                res.plurals[name] = items
    return res


def parse_file(path: Path) -> tuple[list[str], Res]:
    """按文件读，保留「哪个 key 在哪个文件」的归属 —— 生成时要照这个切分。"""
    order: list[str] = []
    res = Res()
    root = ET.parse(path).getroot()
    for el in root:
        name = el.get("name")
        if not name:
            continue
        if el.tag == "string":
            if el.get("translatable") == "false":
                continue
            res.strings[name] = unescape(el.text or "")
            order.append(name)
        elif el.tag == "plurals":
            res.plurals[name] = {i.get("quantity"): unescape(i.text or "") for i in el}
            order.append(name)
    return order, res


# ────────────────────────────── 写出 ──────────────────────────────

def render(files: dict[str, tuple[list[str], Res]], res: Res, header: str) -> dict[str, str]:
    """按「文件名 -> (key 顺序, 空壳)」的骨架，用 `res` 里的值渲染出 XML 文本。

    只写 `res` 里有的 key；缺哪个由 validate.py 去报，这里不静默补英文。
    """
    out = {}
    for fname, (order, _) in files.items():
        lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            "<!--",
            header,
            "-->",
            "<resources>",
        ]
        for key in order:
            if key in res.plurals:
                items = res.plurals[key]
                lines.append(f'{INDENT}<plurals name="{key}">')
                # other 永远排最后 —— Android 要求它必须存在，放末尾便于人眼扫
                for q in sorted(items, key=lambda q: (q == "other", q)):
                    lines.append(
                        f'{INDENT}{INDENT}<item quantity="{q}">{escape(items[q])}</item>'
                    )
                lines.append(f"{INDENT}</plurals>")
            elif key in res.strings:
                lines.append(
                    f'{INDENT}<string name="{key}">{escape(res.strings[key])}</string>'
                )
        lines.append("</resources>")
        out[fname] = "\n".join(lines) + "\n"
    return out


# ────────────────────────────── 自检 ──────────────────────────────

def _self_check(values_dir: Path) -> None:
    """对现有 values/ 做往返：解析出**值** → 重新转义 → 再解回来 → 必须相等。

    这是转义正确性的**证明**，不是抽样。任何一条对不上都说明 escape/unescape 有洞，
    此时生成 62 个目录等于把洞复制 62 份。

    比的是**值**不是原文：`modbus_service_alarm_separator` 的值是 `", "`（逗号 + 尾随
    空格），现有文件写成不带头尾引号的 `, `，而本模块会写成 `", "` —— 两种写法 aapt
    解析出来都是 `, `，后者是显式表达、不依赖「aapt 会保住尾随空格」这条隐含前提。
    要求逐字节相同会把这种「更稳的等价写法」误判成 bug。
    """
    bad = []
    total = 0
    for f in sorted(values_dir.glob(FILE_GLOB)):
        root = ET.parse(f).getroot()
        for el in root:
            name = el.get("name")
            pairs = []
            if el.tag == "string":
                pairs = [(name, el.text or "")]
            elif el.tag == "plurals":
                pairs = [(f"{name}[{i.get('quantity')}]", i.text or "") for i in el]
            for key, raw in pairs:
                total += 1
                value = unescape(raw)
                if unescape(escape(value)) != value:
                    bad.append((f.name, key, value, escape(value)))
    if bad:
        print(f"✗ 转义往返丢信息 {len(bad)}/{total} 条：")
        for fn, k, v, e in bad[:20]:
            print(f"    {fn} {k}\n      值:   {v!r}\n      转义: {e!r}")
        raise SystemExit(1)
    print(f"✓ 转义往返无损：{total} 条（{values_dir}）")


if __name__ == "__main__":
    _self_check(Path(__file__).resolve().parents[2] / "app/src/main/res/values")
