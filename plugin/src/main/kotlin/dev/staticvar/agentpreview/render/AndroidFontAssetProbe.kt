/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.render

import java.io.File

/** Lightweight diagnostics for font availability in the isolated Robolectric renderer JVM. */
internal object AndroidFontAssetProbe {
    private const val PREFIX = "AgentPreview font probe:"
    private const val SAMPLE_TEXT = "iiii WWWW"
    private const val DEFAULT_CAP = 8

    fun probe(
        activity: Any,
        androidAssetsDir: File,
        cap: Int = DEFAULT_CAP,
    ) {
        val assets = activity.javaClass.getMethod("getAssets").invoke(activity)
        val paths = findFontAssetPaths(androidAssetsDir, cap)
        System.err.println("$PREFIX assetsDir=${androidAssetsDir.absolutePath} fontCount=${paths.size}")
        paths.forEach { path ->
            val file = androidAssetsDir.resolve(path)
            val defaultWidth = measureWidth(null)
            val openResult =
                runCatching {
                    val input = assets.javaClass.getMethod("open", String::class.java).invoke(assets, path) as java.io.InputStream
                    input.use {
                        val bytes = ByteArray(4)
                        val count = it.read(bytes)
                        bytes.take(count.coerceAtLeast(0)).joinToString("") { byte -> "%02X".format(byte) }
                    }
                }
            val assetWidth = runCatching { measureWidth(buildTypefaceFromAssets(assets, path)) }
            val fileWidth = runCatching { measureWidth(buildTypefaceFromFile(file)) }

            System.err.println(
                "$PREFIX path=$path " +
                    "header=${openResult.fold({ it }, { "ERROR:${summary(it)}" })} " +
                    "defaultWidth=$defaultWidth " +
                    "assetWidth=${assetWidth.fold({ it.toString() }, { "ERROR:${summary(it)}" })} " +
                    "fileWidth=${fileWidth.fold({ it.toString() }, { "ERROR:${summary(it)}" })}",
            )
        }
    }

    internal fun findFontAssetPaths(
        androidAssetsDir: File,
        cap: Int = DEFAULT_CAP,
    ): List<String> {
        if (!androidAssetsDir.isDirectory || cap <= 0) return emptyList()
        return androidAssetsDir
            .walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
            .map { androidAssetsDir.toPath().relativize(it.toPath()).joinToString("/") }
            .filter { path -> path.substringBefore('/') == "composeResources" && path.contains("/font/") }
            .sorted()
            .take(cap)
            .toList()
    }

    private fun buildTypefaceFromAssets(
        assets: Any,
        path: String,
    ): Any {
        val builderClass = Class.forName("android.graphics.Typeface\$Builder")
        val builder =
            builderClass
                .getConstructor(
                    Class.forName("android.content.res.AssetManager"),
                    String::class.java,
                ).newInstance(assets, path)
        return builderClass.getMethod("build").invoke(builder)
    }

    private fun buildTypefaceFromFile(file: File): Any {
        val builderClass = Class.forName("android.graphics.Typeface\$Builder")
        val builder = builderClass.getConstructor(File::class.java).newInstance(file)
        return builderClass.getMethod("build").invoke(builder)
    }

    private fun measureWidth(typeface: Any?): Float {
        val paintClass = Class.forName("android.graphics.Paint")
        val paint = paintClass.getConstructor().newInstance()
        paintClass.getMethod("setTextSize", Float::class.javaPrimitiveType).invoke(paint, 32f)
        typeface?.let { paintClass.getMethod("setTypeface", Class.forName("android.graphics.Typeface")).invoke(paint, it) }
        return paintClass.getMethod("measureText", String::class.java).invoke(paint, SAMPLE_TEXT) as Float
    }

    private fun summary(throwable: Throwable): String =
        throwable::class.java.simpleName + (throwable.message?.let { ":${it.replace('\n', ' ').take(120)}" } ?: "")
}
