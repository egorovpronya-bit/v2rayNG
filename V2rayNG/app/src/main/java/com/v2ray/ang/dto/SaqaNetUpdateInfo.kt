package com.v2ray.ang.dto

import com.google.gson.annotations.SerializedName

data class SaqaNetUpdateInfo(
    @SerializedName("version") val version: String,
    @SerializedName("min_version") val minVersion: String,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("notes") val notes: String = "",
    val hasUpdate: Boolean = false,
    val isForced: Boolean = false
)
