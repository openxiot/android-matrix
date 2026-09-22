"""语言清单 —— 本次多语言扩展的**唯一真源**。

下游全部从这里派生，不要另抄一份：
  - 生成的 `values-*` 目录名（DIR）
  - 落盘与 `Locale.forLanguageTag` 用的 tag（BCP47）
  - 选择页的数据表（ENDONYM / ENGLISH / RTL）
  - 复数门禁要求的档位（PLURALS）

对照的是 web 工程 `webapp-matrix/public/i18n/*.json` 的 66 份词典。
"""

from dataclasses import dataclass, field
from typing import List

#: 每个语言按 CLDR 基数规则**应有**的复数档位。
#:
#: 取的是「CLDR 为该语言定义的全部基数类别」，不是「本 App 的计数会命中的类别」——
#: 多写一档只是死条目，少写一档则是可见的语法错误（Android 缺档会**静默**回落 `other`，
#: 俄语的 5 会显示成 «5 товаров» 而不是 «5 товара»）。fr/es/it/pt/ca 的 `many` 只在
#: 1e6 以上命中，本 App 到不了，照样写上，为的是不必逐个语言论证。
OTHER = ["other"]
ONE_OTHER = ["one", "other"]


@dataclass(frozen=True)
class Lang:
    #: web 词典的文件名 / SUPPORTED_LANGUAGES 的键
    web: str
    #: 落盘与 Locale 解析用的 BCP-47（与 web 键**不一定**相同，见 by/kmr/ku）
    bcp47: str
    #: Android 资源目录后缀；None 表示不建目录
    dir: str = None
    #: 自称（语言选择页展示用的名字，本身不翻译）
    endonym: str = ""
    #: 英文名（仅供选择页搜索命中，如输入 "Japanese" 也能找到日本語）
    english: str = ""
    rtl: bool = False
    plurals: List[str] = field(default_factory=lambda: list(ONE_OTHER))
    #: 不建目录的原因（写清楚，免得后人以为是漏了）
    skip: str = ""


L = Lang

# fmt: off
LANGS: List[Lang] = [
    # ⚠️ 英文是 ONE_OTHER，不是 OTHER ——「1 device」和「2 devices」必须分开写。
    # 这一条曾写错成 OTHER 且**没有任何测试发现**：en 的 dir 是 None（英文的资源目录
    # 就是默认的 values/），它既不进 shipped() 也不进 to_create()，于是生成与校验
    # 两条链路都绕开了它。是 cldr_check.py 拿 CLDR 对照时才炸出来的。
    L("en",    "en",    None,            "English",             "English",             False, ONE_OTHER),
    L("zh",    "zh",    "values-zh",     "中文(简体)",           "Chinese (Simplified)",  False, OTHER),
    L("zh_TW", "zh-TW", "values-zh-rTW", "中文(繁體)",           "Chinese (Traditional)", False, OTHER),
    L("zh_HK", "zh-HK", "values-zh-rHK", "中文(香港)",           "Chinese (Hong Kong)",   False, OTHER),

    L("ja",    "ja",    "values-ja",     "日本語",               "Japanese",            False, OTHER),
    L("ko",    "ko",    "values-ko",     "한국어",                "Korean",              False, OTHER),
    L("vi",    "vi",    "values-vi",     "Tiếng Việt",          "Vietnamese",          False, OTHER),
    L("th",    "th",    "values-th",     "ไทย",                 "Thai",                False, OTHER),
    L("id",    "id",    "values-id",     "Bahasa Indonesia",    "Indonesian",          False, OTHER),
    L("ms",    "ms",    "values-ms",     "Bahasa Melayu",       "Malay",               False, OTHER),
    L("km",    "km",    "values-km",     "ភាសាខ្មែរ",             "Khmer",               False, OTHER),

    L("de",    "de",    "values-de",     "Deutsch",             "German",              False, ONE_OTHER),
    L("fr",    "fr",    "values-fr",     "Français",            "French",              False, ["one", "many", "other"]),
    L("fr_BE", "fr-BE", "values-fr-rBE", "Français (Belgique)", "French (Belgium)",    False, ["one", "many", "other"]),
    L("fr_CA", "fr-CA", "values-fr-rCA", "Français (Canada)",   "French (Canada)",     False, ["one", "many", "other"]),
    L("it",    "it",    "values-it",     "Italiano",            "Italian",             False, ["one", "many", "other"]),
    L("es",    "es",    "values-es",     "Español",             "Spanish",             False, ["one", "many", "other"]),
    L("pt",    "pt",    "values-pt",     "Português",           "Portuguese",          False, ["one", "many", "other"]),
    L("pt_BR", "pt-BR", "values-pt-rBR", "Português (Brasil)",  "Portuguese (Brazil)", False, ["one", "many", "other"]),
    L("nl",    "nl",    "values-nl",     "Nederlands",          "Dutch",               False, ONE_OTHER),
    L("nl_BE", "nl-BE", "values-nl-rBE", "Nederlands (België)", "Dutch (Belgium)",     False, ONE_OTHER),
    L("ca",    "ca",    "values-ca",     "Català",              "Catalan",             False, ["one", "many", "other"]),
    L("gl",    "gl",    "values-gl",     "Galego",              "Galician",            False, ONE_OTHER),
    L("da",    "da",    "values-da",     "Dansk",               "Danish",              False, ONE_OTHER),
    L("sv",    "sv",    "values-sv",     "Svenska",             "Swedish",             False, ONE_OTHER),
    L("nb",    "nb",    "values-nb",     "Norsk (Bokmål)",      "Norwegian Bokmål",    False, ONE_OTHER),
    L("is",    "is",    "values-is",     "Íslenska",            "Icelandic",           False, ONE_OTHER),
    L("fi",    "fi",    "values-fi",     "Suomi",               "Finnish",             False, ONE_OTHER),
    L("et",    "et",    "values-et",     "Eesti",               "Estonian",            False, ONE_OTHER),
    L("lv",    "lv",    "values-lv",     "Latviešu",            "Latvian",             False, ["zero", "one", "other"]),
    L("lt",    "lt",    "values-lt",     "Lietuvių",            "Lithuanian",          False, ["one", "few", "many", "other"]),
    L("ga",    "ga",    "values-ga",     "Gaeilge",             "Irish",               False, ["one", "two", "few", "many", "other"]),
    L("el",    "el",    "values-el",     "Ελληνικά",            "Greek",               False, ONE_OTHER),
    L("hu",    "hu",    "values-hu",     "Magyar",              "Hungarian",           False, ONE_OTHER),
    L("ro",    "ro",    "values-ro",     "Română",              "Romanian",            False, ["one", "few", "other"]),
    L("cs",    "cs",    "values-cs",     "Čeština",             "Czech",               False, ["one", "few", "many", "other"]),
    L("sk",    "sk",    "values-sk",     "Slovenčina",          "Slovak",              False, ["one", "few", "many", "other"]),
    L("sl",    "sl",    "values-sl",     "Slovenščina",         "Slovenian",           False, ["one", "two", "few", "other"]),
    L("pl",    "pl",    "values-pl",     "Polski",              "Polish",              False, ["one", "few", "many", "other"]),
    L("hr",    "hr",    "values-hr",     "Hrvatski",            "Croatian",            False, ["one", "few", "other"]),
    L("sr",    "sr",    "values-sr",     "Српски",              "Serbian",             False, ["one", "few", "other"]),
    L("bg",    "bg",    "values-bg",     "Български",           "Bulgarian",           False, ONE_OTHER),
    L("mk",    "mk",    "values-mk",     "Македонски",          "Macedonian",          False, ONE_OTHER),
    L("ru",    "ru",    "values-ru",     "Русский",             "Russian",             False, ["one", "few", "many", "other"]),
    L("uk",    "uk",    "values-uk",     "Українська",          "Ukrainian",           False, ["one", "few", "many", "other"]),
    L("by",    "be",    "values-be",     "Беларуская",          "Belarusian",          False, ["one", "few", "many", "other"]),

    L("tr",    "tr",    "values-tr",     "Türkçe",              "Turkish",             False, ONE_OTHER),
    L("az",    "az",    "values-az",     "Azərbaycan dili",     "Azerbaijani",         False, ONE_OTHER),
    L("kk",    "kk",    "values-kk",     "Қазақ тілі",          "Kazakh",              False, ONE_OTHER),
    L("mn",    "mn",    "values-mn",     "Монгол",              "Mongolian",           False, ONE_OTHER),
    L("hy",    "hy",    "values-hy",     "Հայերեն",             "Armenian",            False, ONE_OTHER),
    L("ka",    "ka",    "values-ka",     "ქართული",             "Georgian",            False, ONE_OTHER),

    L("ar",    "ar",    "values-ar",     "العربية",             "Arabic",              True,  ["zero", "one", "two", "few", "many", "other"]),
    L("he",    "he",    "values-he",     "עברית",               "Hebrew",              True,  ["one", "two", "many", "other"]),
    L("fa",    "fa",    "values-fa",     "فارسی",               "Persian",             True,  ONE_OTHER),
    L("ur",    "ur",    "values-ur",     "اردو",                "Urdu",                True,  ONE_OTHER),
    L("ku",    "ckb",   "values-b+ckb",  "کوردی (Sorani)",      "Kurdish (Sorani)",    True,  ONE_OTHER),
    L("kmr",   "kmr",   "values-b+kmr",  "Kurdî (Kurmancî)",    "Kurdish (Kurmanji)",  False, ONE_OTHER),

    L("hi",    "hi",    "values-hi",     "हिन्दी",               "Hindi",               False, ONE_OTHER),
    L("bn",    "bn",    "values-bn",     "বাংলা",               "Bengali",             False, ONE_OTHER),
    L("ne",    "ne",    "values-ne",     "नेपाली",              "Nepali",              False, ONE_OTHER),
    L("ta",    "ta",    "values-ta",     "தமிழ்",                "Tamil",               False, ONE_OTHER),
    L("kn",    "kn",    "values-kn",     "ಕನ್ನಡ",                "Kannada",             False, ONE_OTHER),
    L("ml",    "ml",    "values-ml",     "മലയാളം",               "Malayalam",           False, ONE_OTHER),

    # 与英文逐字相同率 96% / 93%，等于把英文抄两份；且会直接踩死门禁里
    # 「该语言确实翻了」的比例规则。收益近零，有意不做。
    L("en_GB", "en-GB", None, "English (UK)",        "English (UK)",        False, ONE_OTHER, skip="与英文逐字相同率 96%"),
    L("en_AU", "en-AU", None, "English (Australia)", "English (Australia)", False, ONE_OTHER, skip="与英文逐字相同率 93%"),
]
# fmt: on


def shipped() -> List[Lang]:
    """需要落盘的语言（含英文默认与已存在的 values-zh）。"""
    return [x for x in LANGS if x.dir is not None]


def to_create() -> List[Lang]:
    """本次**新建目录**的语言（排除英文默认与已有 values-zh）。"""
    return [x for x in shipped() if x.web not in ("en", "zh")]


def by_bcp47(tag: str):
    for x in LANGS:
        if x.bcp47 == tag:
            return x
    return None


if __name__ == "__main__":
    assert len(LANGS) == 66, f"应为 66 种，实为 {len(LANGS)}"
    assert len(shipped()) == 63, f"应落盘 63 种，实为 {len(shipped())}"
    assert len(to_create()) == 62, f"应新建 62 个目录，实为 {len(to_create())}"
    # 目录名与 tag 都不许重复，否则生成的 XML 会互相覆盖
    for attr in ("web", "bcp47"):
        vals = [getattr(x, attr) for x in LANGS]
        dup = {v for v in vals if vals.count(v) > 1}
        assert not dup, f"{attr} 有重复：{dup}"
    dirs = [x.dir for x in shipped()]
    assert len(set(dirs)) == len(dirs), "目录名有重复"
    # 三字母代码必须走 b+ 形式，否则 aapt2 不认
    for x in shipped():
        code = x.bcp47.split("-")[0]
        if len(code) == 3:
            assert x.dir.startswith("values-b+"), f"{x.bcp47} 是三字母码，目录须为 values-b+ 形式"
    print(f"66 种语言，落盘 {len(shipped())} 种，新建 {len(to_create())} 个目录")
    print("RTL：" + ", ".join(x.bcp47 for x in LANGS if x.rtl))
    extra = sorted({tuple(x.plurals) for x in LANGS if x.plurals != ONE_OTHER})
    print(f"非 one/other 的复数档位组合：{len(extra)} 组")
    for e in extra:
        who = [x.bcp47 for x in LANGS if tuple(x.plurals) == e]
        print(f"  {'/'.join(e):34s} {', '.join(who)}")
