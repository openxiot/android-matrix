package cc.openxiot.wematrix.data.api

import com.google.gson.annotations.SerializedName

/**
 * 官网的版本清单，对应 webapp-matrix-site 仓库的 `static/data/apps/android.json`
 * （由 android-matrix 打 tag 发版时自动改写，见 .github/workflows/build-release.yml）。
 *
 * 字段全可空 + 给默认值：这个文件是**手改过的**（流水线只覆盖其中几个字段），
 * 少一个字段或写错类型都不该让解析整个炸掉。校验与「够不够发更新」的判断留给
 * [cc.openxiot.wematrix.data.repository.UpdateRepository]。
 */
data class UpdateManifest(
    @SerializedName("version") val version: String? = null,
    @SerializedName("releasedAt") val releasedAt: String? = null,
    // 仅供显示，形如 "54.4 MB"。**不要**拿它当字节数去算存储空间，那是另一个字段的事
    @SerializedName("size") val size: String? = null,
    @SerializedName("url") val url: String? = null,
    // 安装包整包的 SHA-256（小写十六进制）。发版流水线写入，但早于本次改造的清单里没有
    // 这个字段，所以可空 —— 缺失时跳过这一道，不匹配时硬失败。
    @SerializedName("sha256") val sha256: String? = null,
    @SerializedName("notes") val notes: UpdateNotes? = null
)

data class UpdateNotes(
    @SerializedName("zh") val zh: List<String>? = null,
    @SerializedName("en") val en: List<String>? = null
)
