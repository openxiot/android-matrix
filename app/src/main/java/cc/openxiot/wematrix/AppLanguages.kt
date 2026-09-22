package cc.openxiot.wematrix

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
    AppLanguage("en", "English", "English", false),
// i18n-ignore: 语言自称是数据不是界面文案 —— 任何语言的界面下都显示原文，不抽资源
    AppLanguage("zh", "中文(简体)", "Chinese (Simplified)", false),
// i18n-ignore: 语言自称是数据不是界面文案 —— 任何语言的界面下都显示原文，不抽资源
    AppLanguage("zh-TW", "中文(繁體)", "Chinese (Traditional)", false),
// i18n-ignore: 语言自称是数据不是界面文案 —— 任何语言的界面下都显示原文，不抽资源
    AppLanguage("zh-HK", "中文(香港)", "Chinese (Hong Kong)", false),
// i18n-ignore: 语言自称是数据不是界面文案 —— 任何语言的界面下都显示原文，不抽资源
    AppLanguage("ja", "日本語", "Japanese", false),
    AppLanguage("ko", "한국어", "Korean", false),
    AppLanguage("vi", "Tiếng Việt", "Vietnamese", false),
    AppLanguage("th", "ไทย", "Thai", false),
    AppLanguage("id", "Bahasa Indonesia", "Indonesian", false),
    AppLanguage("ms", "Bahasa Melayu", "Malay", false),
    AppLanguage("km", "ភាសាខ្មែរ", "Khmer", false),
    AppLanguage("de", "Deutsch", "German", false),
    AppLanguage("fr", "Français", "French", false),
    AppLanguage("fr-BE", "Français (Belgique)", "French (Belgium)", false),
    AppLanguage("fr-CA", "Français (Canada)", "French (Canada)", false),
    AppLanguage("it", "Italiano", "Italian", false),
    AppLanguage("es", "Español", "Spanish", false),
    AppLanguage("pt", "Português", "Portuguese", false),
    AppLanguage("pt-BR", "Português (Brasil)", "Portuguese (Brazil)", false),
    AppLanguage("nl", "Nederlands", "Dutch", false),
    AppLanguage("nl-BE", "Nederlands (België)", "Dutch (Belgium)", false),
    AppLanguage("ca", "Català", "Catalan", false),
    AppLanguage("gl", "Galego", "Galician", false),
    AppLanguage("da", "Dansk", "Danish", false),
    AppLanguage("sv", "Svenska", "Swedish", false),
    AppLanguage("nb", "Norsk (Bokmål)", "Norwegian Bokmål", false),
    AppLanguage("is", "Íslenska", "Icelandic", false),
    AppLanguage("fi", "Suomi", "Finnish", false),
    AppLanguage("et", "Eesti", "Estonian", false),
    AppLanguage("lv", "Latviešu", "Latvian", false),
    AppLanguage("lt", "Lietuvių", "Lithuanian", false),
    AppLanguage("ga", "Gaeilge", "Irish", false),
    AppLanguage("el", "Ελληνικά", "Greek", false),
    AppLanguage("hu", "Magyar", "Hungarian", false),
    AppLanguage("ro", "Română", "Romanian", false),
    AppLanguage("cs", "Čeština", "Czech", false),
    AppLanguage("sk", "Slovenčina", "Slovak", false),
    AppLanguage("sl", "Slovenščina", "Slovenian", false),
    AppLanguage("pl", "Polski", "Polish", false),
    AppLanguage("hr", "Hrvatski", "Croatian", false),
    AppLanguage("sr", "Српски", "Serbian", false),
    AppLanguage("bg", "Български", "Bulgarian", false),
    AppLanguage("mk", "Македонски", "Macedonian", false),
    AppLanguage("ru", "Русский", "Russian", false),
    AppLanguage("uk", "Українська", "Ukrainian", false),
    AppLanguage("be", "Беларуская", "Belarusian", false),
    AppLanguage("tr", "Türkçe", "Turkish", false),
    AppLanguage("az", "Azərbaycan dili", "Azerbaijani", false),
    AppLanguage("kk", "Қазақ тілі", "Kazakh", false),
    AppLanguage("mn", "Монгол", "Mongolian", false),
    AppLanguage("hy", "Հայերեն", "Armenian", false),
    AppLanguage("ka", "ქართული", "Georgian", false),
    AppLanguage("ar", "العربية", "Arabic", true),
    AppLanguage("he", "עברית", "Hebrew", true),
    AppLanguage("fa", "فارسی", "Persian", true),
    AppLanguage("ur", "اردو", "Urdu", true),
    AppLanguage("ckb", "کوردی (Sorani)", "Kurdish (Sorani)", true),
    AppLanguage("kmr", "Kurdî (Kurmancî)", "Kurdish (Kurmanji)", false),
    AppLanguage("hi", "हिन्दी", "Hindi", false),
    AppLanguage("bn", "বাংলা", "Bengali", false),
    AppLanguage("ne", "नेपाली", "Nepali", false),
    AppLanguage("ta", "தமிழ்", "Tamil", false),
    AppLanguage("kn", "ಕನ್ನಡ", "Kannada", false),
    AppLanguage("ml", "മലയാളം", "Malayalam", false),
)

private val BY_TAG: Map<String, AppLanguage> = APP_LANGUAGES.associateBy { it.tag }

/** 按 tag 查语言；未收录（含 [AppLocale.SYSTEM]）返回 null，由调用方决定兜底文案。 */
fun appLanguage(tag: String): AppLanguage? = BY_TAG[tag]
