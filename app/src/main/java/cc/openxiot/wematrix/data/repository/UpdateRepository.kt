package cc.openxiot.wematrix.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import cc.openxiot.wematrix.BuildConfig
import cc.openxiot.wematrix.data.api.UpdateApi
import cc.openxiot.wematrix.util.Constants
import cc.openxiot.wematrix.util.VersionCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** 官网清单里与本机相关的那部分：已校验、已挑好语言。 */
data class UpdateInfo(
    val version: String,
    val releasedAt: String?,
    /** 仅供显示，形如 `54.4 MB`。别拿它当字节数算存储空间。 */
    val size: String?,
    val url: String,
    /** 清单里写的整包 SHA-256（小写）；老清单没有这个字段时为 null。 */
    val sha256: String?,
    /** 更新说明，已按语言挑过（zh 优先，缺失回退 en）。可能是空的。 */
    val notes: List<String>
)

/** 检查的结果。 */
sealed interface CheckOutcome {
    /** 本机已是最新（或官网的还没本机新）。 */
    data object UpToDate : CheckOutcome
    data class Available(val info: UpdateInfo) : CheckOutcome
}

/**
 * 版本更新的取数与落盘。
 *
 * 下载下来的 APK 放私有目录，用**文件系统当持久状态**：下载完点安装、从安装器返回、
 * 甚至强杀重进，都靠「磁盘上有没有这一版且校验过的文件」判断，不依赖任何内存状态。
 */
class UpdateRepository(private val context: Context) {

    // filesDir 而不是 cacheDir：cacheDir 在系统空间紧张时会被随时清掉，54MB 下完还没
    // 点安装就没了。filesDir 默认会进自动备份，所以那两个 backup 规则文件里把本目录
    // 排除了（54MB 撞 25MB 配额会让整个备份静默失败，连用户设置一起丢）。
    private val updatesDir: File
        get() = File(context.filesDir, DIR_UPDATES).apply { mkdirs() }

    /** 拉清单并与本机比对。 */
    suspend fun check(): Result<CheckOutcome> = runCatching {
        val manifest = UpdateApi.fetchManifest(Constants.UPDATE_MANIFEST_URL)

        val remote = manifest.version?.trim().orEmpty()
        val diff = VersionCompare.compare(remote, BuildConfig.VERSION_NAME)
            // 「不知道」必须报出来。顺着当成「不更新」会静默吃掉一次发版，而界面上
            // 只显示「已是最新版本」，从症状根本看不出是清单写歪了。
            ?: throw Exception("无法识别版本信息（官网写的是「${remote.ifEmpty { "空" }}」）")
        if (diff <= 0) {
            // 已经是最新：私有目录里任何安装包都没用了（正在跑的这一版装不了，
            // 更旧的更装不了），全部清掉
            cleanup(keep = null)
            return@runCatching CheckOutcome.UpToDate
        }

        val url = manifest.url?.takeIf { it.isNotBlank() }
            ?: throw Exception("版本信息里没有下载地址")

        // 清单必须指向**带版本号**的那个文件。指向 wematrix-latest.apk 会让「清单说是哪一版」
        // 和「实际下到哪一版」脱钩：等 latest 再往前走一步，用户就会在被告知是 1.0.6 的情况下
        // 装上 1.0.7。这里只拦这一个已知别名，不做「URL 里必须含版本号」那种子串猜测
        // —— 那会误伤将来换成 CDN 路径或带内容哈希的文件名。
        val fileName = url.substringBefore('?').substringAfterLast('/')
        if (fileName.equals(LATEST_ALIAS, ignoreCase = true)) {
            throw Exception("下载地址指向 latest 别名，不是 $remote 对应的安装包")
        }

        val info = UpdateInfo(
            version = remote,
            releasedAt = manifest.releasedAt,
            size = manifest.size,
            url = url,
            sha256 = manifest.sha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            // 只用中文：App 没有 i18n 层，其余文案全是硬编码中文。回退到 en 只是因为
            // 「有英文没中文」比「什么都看不到」强。
            notes = (manifest.notes?.zh ?: manifest.notes?.en).orEmpty()
        )

        // 只留这一版。放在这里而不是只在下载之后清，是因为「装完新版、旧包还躺着」这条
        // 路没有别的地方会经过：安装成功时应用被系统杀掉，下载流程根本没机会再跑一遍。
        // 每次查清单顺手扫一次，代价是一个目录列举。
        cleanup(keep = apkFileFor(remote))
        CheckOutcome.Available(info)
    }

    /**
     * 清理私有目录里「不比本机新」的安装包。进程启动时跑一次（AppUpdate.sweepOnStart）。
     *
     * 还是为上面那条路兜底：装完新版后应用被杀重启，那份 54MB 的包还躺在 filesDir 里，
     * 而用户可能再也不会打开「关于」页。这里按**文件名里的版本号 vs 当前运行版本**判断
     * ——等于本机的是刚装上的那一份，小于本机的是更早的遗留，两者都装不了，删；只有比
     * 本机新的才是「下好了等着装」，留下（删了等于逼用户重下 54MB）。
     */
    suspend fun sweepStale(): Unit = withContext(Dispatchers.IO) {
        updatesDir.listFiles()?.forEach { file ->
            // 半截文件永远没用：写下它的那次下载已经结束了，不会再有人来续写
            if (file.name.endsWith(PART_SUFFIX)) {
                file.delete()
                return@forEach
            }
            if (!file.name.endsWith(".apk")) return@forEach
            val version = file.name.removePrefix("wematrix-").removeSuffix(".apk")
            val diff = VersionCompare.compare(version, BuildConfig.VERSION_NAME)
            // 名字解析不出来（`wematrix-latest.apk` 这种别名，或谁手工塞进来的东西）也删：
            // 判断不了新旧，而一个来路不明的 APK 留在私有目录里没有任何好处。
            if (diff == null || diff <= 0) file.delete()
        }
    }

    /** 磁盘上这一版已下好且校验通过的安装包；没有则 null。 */
    suspend fun downloadedApk(info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        val file = apkFileFor(info.version)
        // 复用已有文件时不重算整包哈希，理由见 [verify] 的 checkHash
        if (file.isFile && verify(file, info, checkHash = false) == null) file else null
    }

    /**
     * 下载 [info] 并校验，返回可以直接交给安装器的文件。
     *
     * 落盘是「先写 .part 再原子改名」：断网或被杀留下的半截文件永远不会被当成完整包，
     * 因为改名只在整包写完**且**校验通过之后才发生。
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float?) -> Unit): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val target = apkFileFor(info.version)
            // 已经下好就用现成的：「下载完点安装、从安装器返回」这条路不该重下 54MB
            if (target.isFile && verify(target, info, checkHash = false) == null) {
                return@withContext target
            }

            val part = File(updatesDir, "${target.name}$PART_SUFFIX")
            part.delete()

            try {
                UpdateApi.download(info.url, part, onProgress)
                // 这里 checkHash 用默认的 true：刚下完的那一份还从没验过哈希
                verify(part, info)?.let { throw Exception(it) }
                if (!part.renameTo(target)) throw Exception("安装包保存失败")
            } catch (t: Throwable) {
                part.delete()
                throw t
            }

            cleanup(keep = target)
            target
        }
    }

    /**
     * 校验一个候选安装包：通过返回 null，不通过返回中文原因。
     *
     * 这是**设备端**的闸门，前两道不依赖清单里有没有哈希：
     * 1. 包内 versionCode 必须高于本机。低了安装器必然拒（INSTALL_FAILED_VERSION_DOWNGRADE），
     *    先拦下能省用户一趟白等；顺带挡住「清单与 Blob 不同步」—— 手改清单很容易把 url
     *    指到一个已经被覆盖掉的文件上。
     * 2. 签名证书必须与本机**完全相同**。这一道比清单里的 sha256 更强：哈希和 url 同在一个
     *    信任域里（同一个站、同一次手改），能改 url 的人也能改哈希；签名私钥不在那个域里，
     *    而且签名覆盖整个文件，本身就验了完整性。
     *
     * [checkHash] 只控制第三道（清单里的 sha256）。**复用磁盘上已有的包时传 false**：那个
     * 文件在改名落盘之前验过整包哈希，而改名是下载流程的最后一步 —— 能出现在那个文件名下，
     * 就一定是验过的。每次进「关于」页重算 54MB 是纯浪费（用户会看着按钮卡在那里），而真正
     * 拦得住伪造的是上面那道签名闸门，它照常执行。
     */
    private fun verify(file: File, info: UpdateInfo, checkHash: Boolean = true): String? {
        val archive = archiveInfo(file) ?: return "安装包无法解析（下载不完整或不是 APK）"

        if (archive.longVersionCode <= BuildConfig.VERSION_CODE.toLong()) {
            return "官网上的包（versionCode ${archive.longVersionCode}）不比本机新"
        }

        val signers = archive.signingInfo?.apkContentsSigners
        if (signers.isNullOrEmpty()) return "安装包没有签名信息"
        // 要求**恰好一个**签名且与本机一致。本项目的发布包就是单签名；多签名一律拒绝
        // —— 宽松的「任意一个签名匹配」会给「我们的证书 + 攻击者的证书」开口子。
        if (signers.size != 1) return "安装包的签名不符合预期"
        val own = ownCertDigest() ?: return "读不到本机的签名证书"
        if (!signers[0].toByteArray().contentEquals(own)) {
            return "安装包签名与本机不一致，装了也覆盖不了"
        }

        // 清单里的 sha256 是**双向可选**的：字段缺失就跳过（早于本次改造的清单里没有它，
        // 不能因此把所有用户卡死在「无法更新」），写了就必须对上。checkHash=false 时
        // 连 expected 都不必取，直接过。
        if (!checkHash) return null
        val expected = info.sha256 ?: return null
        if (!sha256(file).equals(expected, ignoreCase = true)) {
            return "安装包校验值不匹配，可能下载损坏"
        }
        return null
    }

    private fun archiveInfo(file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                )
            )
        } else {
            // minSdk 是 29，走不到 API 28 以下那条更老的 GET_SIGNATURES 分支
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        }

    /** 本机自己的签名证书。查一次就够，缓存起来 —— 每次校验都要用。 */
    private var ownCertCache: ByteArray? = null

    private fun ownCertDigest(): ByteArray? = ownCertCache ?: run {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        }
        info?.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            ?.also { ownCertCache = it }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 文件名带版本号，与 Blob 上的命名对齐，在设备上对照起来方便。 */
    private fun apkFileFor(version: String): File = File(updatesDir, "wematrix-$version.apk")

    /**
     * 只留 [keep] 一份（[keep] 为 null 表示一份都不留）。半截文件与旧版本一律删掉 ——
     * 一份 54MB，留在私有目录里既没用又占地方，旧版本还多一个被误装的机会。
     *
     * 按**名字**比对而不是按路径：`kept` 那份不必存在（清单说有新版但还没下过，是常态）。
     * 自己取 Dispatchers.IO —— 调用方有时在 Default 上（[check]），有时已经在 IO 上。
     */
    private suspend fun cleanup(keep: File?): Unit = withContext(Dispatchers.IO) {
        updatesDir.listFiles()?.forEach { file ->
            if (keep != null && file.name == keep.name) return@forEach
            if (file.name.endsWith(PART_SUFFIX) || file.name.endsWith(".apk")) file.delete()
        }
    }

    private companion object {
        const val DIR_UPDATES = "updates"
        const val LATEST_ALIAS = "wematrix-latest.apk"
        const val PART_SUFFIX = ".part"
    }
}
