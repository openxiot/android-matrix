package cc.openxiot.matrix.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把下载好的 APK 交给系统安装器。全是「跳出去、再回来」的胶水，没有状态，所以是
 * 无状态的对象。
 *
 * 两条容易踩的坑都在这层的返回值里：
 * - 授权页跳过去之后，**不能**看 `onActivityResult` 的 resultCode 判断用户有没有授权
 *   —— 那个回调永远是 `RESULT_CANCELED`。唯一可信的办法是回来之后重新问一次
 *   [canInstall]（UI 侧用 ON_RESUME 重新查）。
 * - 受管设备（MDM 设了 `DISALLOW_INSTALL_UNKNOWN_SOURCES`）上，设置页里那个开关可能是
 *   灰的、翻不动，安装入口也可能整个没有。所以每个 startActivity 都兜
 *   `ActivityNotFoundException`，让调用方至少能说一句人话，而不是崩掉。
 */
object ApkInstaller {

    private const val MIME_APK = "application/vnd.android.package-archive"

    /**
     * 本应用是否已被允许安装未知来源的应用。
     *
     * 这是 `REQUEST_INSTALL_PACKAGES` 的**用户授权态**，不是运行时弹窗：系统把它做成
     * 设置页里的一个开关，应用只能把用户送过去自己开。minSdk 29 > 26，这个 API 一直在。
     */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * 跳到本应用的「安装未知应用」授权页。
     *
     * 必须带 `package:`，才会落在**本应用**那一项而不是整个应用列表 —— 后者要用户自己
     * 在几十项里翻出我们。部分 OEM 不认这个带 data 的形式，所以退回不带 data 的通用页，
     * 两条都跳不动才算失败。
     */
    fun openInstallPermissionSettings(context: Context): Boolean {
        val scoped = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, scoped)) return true

        val fallback = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, fallback)
    }

    /**
     * 拉起系统安装器。返回是否真的拉起来了。
     *
     * 授权用 `FLAG_GRANT_READ_URI_PERMISSION` 单次放行，FileProvider 本身是
     * `exported=false` 且只映射 filesDir/updates 一个目录 —— 安装器拿到的是那一个文件的
     * 临时读权限，不是我们私有目录的通行证。
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.isFile) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, MIME_APK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
