package cc.openxiot.wematrix

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
 * 应用语言：跟随系统 / 中文 / 英文。
 *
 * 机制是「给 baseContext 套一层 locale」。⚠️ **每个带界面的 Activity 都要在自己的
 * `attachBaseContext` 里调 [wrap]**，漏了那个页面就永远跟系统语言走。目前三处：
 * [WeMatrixApp]、[MainActivity]、`ui/scan/ScanQrActivity`。新增 Activity 时照做。
 *
 * 没走 androidx 的 `AppCompatDelegate.setApplicationLocales`：在 minSdk 29 上它要求
 * Activity 继承 `AppCompatActivity`，而后者又要求主题继承 `Theme.AppCompat`，本项目三个
 * 主题的父都是平台主题（`android:Theme.Material.*`）—— 迁移面比 i18n 本身还大。换来的
 * 只有 Android 13+ 系统设置里多一个「应用语言」入口，而那个入口写的是系统
 * `LocaleManager`，会被这里的 baseContext **直接盖掉**：用户会在系统里选中文、进应用
 * 看到英文。不引入反而没有这个矛盾。
 */
object AppLocale {
    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"

    /**
     * ⚠️ 独立的 prefs 文件，**不要**并进 TokenManager 的 `openxiot_prefs`：登出走的是
     * `TokenManager.clear()` → `prefs.edit { clear() }`，语言会被顺手清掉。
     */
    private const val PREFS = "app_settings"
    private const val KEY = "app_language"
    private const val TAG = "AppLocale"

    private val VALID = setOf(SYSTEM, ZH, EN)

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
     * `onCreate` 还没跑、[WeMatrixApp.instance] 还是 lateinit 空值、tokenManager 压根没建
     * —— 碰它们就是进程创建阶段崩溃，连崩溃上报都还没起来。同理也不能读 [current]。
     *
     * 用 `createConfigurationContext` 而不是 `applyOverrideConfiguration`：后者会改变
     * `ContextThemeWrapper.getResourcesInternal()` 的资源构造路径，和主题互相干扰；
     * 前者返回一个全新的 ContextImpl，语义干净。
     */
    fun wrap(base: Context): Context {
        val tag = read(base)
        val config = base.resources.configuration
        val locale = resolveAppLocale(tag, config.locales.toList()) ?: return base
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

    private fun LocaleList.toList(): List<Locale> = (0 until size()).map { this[it] }
}

/**
 * 决定要不要给 baseContext 套语言：返回 `null` 表示原样用系统 config。
 *
 * **为什么「跟随系统」也要显式判断，而不是直接原样返回交给框架**：`values-zh` 会被 aapt2
 * 隐式标上 `Hans`（简体）脚本，于是它匹配不了 `zh-Hant` 系的系统 —— zh-TW / zh-HK / zh-MO
 * 会径直掉到默认的**英文**去（`aapt2 dump badging` 里 `application-label-zh-TW: WeMatrix`
 * 就是铁证）。而需求是「是中文就是中文」，繁体中文也是中文。所以凡 `zh` 系一律显式套简体。
 *
 * 代价是繁体系统用户看到简体（已确认按需求口径有意为之）。不按方言拆 `values-zh-rTW`
 * 的另一面：那要再复制一整套中文文案，且和「一次做完」的规模不成比例。
 *
 * 抽成一个不碰 `android.*`、只吃 `java.util.Locale` 的顶层纯函数，是为了能在纯 JVM 单测里
 * 钉住它（见 `AppLocaleTest`）—— 这段逻辑错了的表现是「某些语言的系统上整个应用语言不对」，
 * 只有恰好用那种系统的人才会发现。
 */
internal fun resolveAppLocale(tag: String, systemLocales: List<Locale>): Locale? = when (tag) {
    AppLocale.ZH -> Locale.SIMPLIFIED_CHINESE
    AppLocale.EN -> Locale.ENGLISH
    // 只有「跟随系统」才看系统语言；显式选了语言就不该被系统影响
    else -> systemLocales.firstOrNull()
        ?.takeIf { it.language == "zh" }
        ?.let { Locale.SIMPLIFIED_CHINESE }
}
