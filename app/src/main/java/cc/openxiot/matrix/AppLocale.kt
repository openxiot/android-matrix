package cc.openxiot.matrix

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.util.Locale

/**
 * 应用语言：跟随系统，或 [APP_LANGUAGES] 里的任意一种（64 种）。
 *
 * 机制是「给 baseContext 套一层 locale」。⚠️ **每个带界面的 Activity 都要在自己的
 * `attachBaseContext` 里调 [wrap]**，漏了那个页面就永远跟系统语言走。目前三处：
 * [MatrixApp]、[MainActivity]、`ui/scan/ScanQrActivity`。新增 Activity 时照做。
 *
 * 没走 androidx 的 `AppCompatDelegate.setApplicationLocales`：在 minSdk 29 上它要求
 * Activity 继承 `AppCompatActivity`，而后者又要求主题继承 `Theme.AppCompat`，本项目三个
 * 主题的父都是平台主题（`android:Theme.Material.*`）—— 迁移面比 i18n 本身还大。换来的
 * 只有 Android 13+ 系统设置里多一个「应用语言」入口，而那个入口写的是系统
 * `LocaleManager`，会被这里的 baseContext **直接盖掉**：用户会在系统里选中文、进应用
 * 看到英文。不引入反而没有这个矛盾。
 */
object AppLocale {
    /** 「跟随系统」的哨兵值。它不是一个 locale，是「别套 wrapper」的意思。 */
    const val SYSTEM = "system"

    /** 简体中文。留存常量是因为它是 `values-zh` 的 tag，注释里反复要提。 */
    const val ZH = "zh"

    /** 英文。[APP_LANGUAGES] 里也有它 —— 英文的资源目录就是默认的 `values/`。 */
    const val EN = "en"

    /**
     * ⚠️ 独立的 prefs 文件，**不要**并进 TokenManager 的 `openxiot_prefs`：登出走的是
     * `TokenManager.clear()` → `prefs.edit { clear() }`，语言会被顺手清掉。
     */
    private const val PREFS = "app_settings"
    private const val KEY = "app_language"
    private const val TAG = "AppLocale"

    /**
     * 落盘白名单 = 跟随系统 + 语言表的全部 tag。
     *
     * **从 [APP_LANGUAGES] 派生而不是另抄一份**：抄一份的话，将来加语言时漏改这里，
     * 表现是「选择了该语言、退出重进又变回跟随系统」—— 每次都被 `read()` 当成非法值
     * 收敛掉，且没有任何报错。这种静默失效只有真机上手动试才看得见。
     */
    internal val VALID: Set<String> = setOf(SYSTEM) + APP_LANGUAGES.map { it.tag }

    /** 给 Compose 读的镜像；真源是 prefs（见 [read]）。 */
    var current by mutableStateOf(SYSTEM)
        private set

    /** 在 `Application.onCreate` 里调，必须早于任何 UI。 */
    fun init(context: Context) {
        current = read(context)
    }

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM)
            ?.takeIf { it in VALID }
            ?: SYSTEM

    /** 切换语言。调用方紧接着要 `recreate()`，否则界面不会变。 */
    fun set(context: Context, tag: String) {
        if (tag !in VALID) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, tag) }
        current = tag
    }

    /**
     * 给 baseContext 套上用户选的语言。
     *
     * ⚠️ 这里**只能读裸 prefs**。它会在 `Application.attachBaseContext` 里被调用，那时
     * `onCreate` 还没跑、[MatrixApp.instance] 还是 lateinit 空值、tokenManager 压根没建
     * —— 碰它们就是进程创建阶段崩溃，连崩溃上报都还没起来。同理也不能读 [current]。
     *
     * 用 `createConfigurationContext` 而不是 `applyOverrideConfiguration`：后者会改变
     * `ContextThemeWrapper.getResourcesInternal()` 的资源构造路径，和主题互相干扰；
     * 前者返回一个全新的 ContextImpl，语义干净。
     */
    fun wrap(base: Context): Context {
        val tag = read(base)
        val config = base.resources.configuration
        val locale = resolveAppLocale(tag) ?: return base
        return try {
            val wrapped = Configuration(config).apply {
                // 24+ 框架优先看 locales，已废弃的 locale 字段不再作数
                setLocales(LocaleList(locale))
                // 阿拉伯语系统下切到英文时，别把 RTL 留着
                setLayoutDirection(locale)
            }
            base.createConfigurationContext(wrapped)
        } catch (e: Exception) {
            // attachBaseContext 里抛异常 = 启动即崩。退回系统语言，只留一条日志。
            // 日志用英文：全项目 Log.* 都是英文，且它不是用户文案（NoHardcodedChineseTest 会管）
            Log.w(TAG, "failed to apply locale, falling back to system", e)
            base
        }
    }
}

/**
 * 决定要不要给 baseContext 套语言：返回 `null` 表示原样用系统 config。
 *
 * **每种语言都有自己的资源目录了，所以这里可以退化成「要么不套、要么照 tag 套」。**
 *
 * 上一版有一段 `zh → SIMPLIFIED_CHINESE` 的特判，因为当时只有一份 `values-zh`，
 * 而 aapt2 会把它隐式标成 `Hans`，于是 `zh-Hant` 系的系统（zh-TW / zh-HK / zh-MO）
 * 匹配不上、径直掉到默认英文 —— 特判是为了「是中文就显示中文」。现在
 * `values-zh-rTW` / `values-zh-rHK` 是真的繁体译文，**再保留那个特判反而有害**：
 * 它会让繁体用户永远选不中自己那份译文。让框架照系统 locale 自己解析才是对的。
 *
 * 那条「繁体不掉英文」的保证**没有消失，只是从 Kotlin 挪到了资源层** ——
 * 由 `values-zh-rTW` / -rHK 两个目录的存在与内容，加上 `StringsParityTest` 的
 * 繁体守卫来兑现。见该测试。
 *
 * 抽成一个不碰 `android.*`、只吃 `java.util.Locale` 的顶层纯函数，是为了能在纯 JVM
 * 单测里钉住它（见 `AppLocaleTest`）—— 这段逻辑错了的表现是「某些语言整个应用语言不对」，
 * 只有恰好用那种语言的人才会发现。
 */
internal fun resolveAppLocale(tag: String): Locale? {
    if (tag !in AppLocale.VALID || tag == AppLocale.SYSTEM) return null
    // 语言表的 tag 都是良构的，但万一有人往表里写错一个，forLanguageTag 会**静默**
    // 返回 Locale.ROOT（语言为空），表现是整个应用变回英文且不报错。
    // 这里挡一下，让它退化成「跟随系统」，至少和用户选的语言同一个语系。
    return Locale.forLanguageTag(tag).takeIf { it.language.isNotEmpty() }
}
