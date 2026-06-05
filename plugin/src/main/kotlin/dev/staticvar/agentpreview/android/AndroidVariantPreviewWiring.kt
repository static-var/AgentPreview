/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.android

import dev.staticvar.agentpreview.AgentPreviewExtension
import dev.staticvar.agentpreview.tasks.CaptureComposePreviewsTask
import dev.staticvar.agentpreview.tasks.ListComposePreviewsTask
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

internal class AndroidVariantPreviewWiring(
    private val project: Project,
    private val extension: AgentPreviewExtension,
    private val listComposePreviews: TaskProvider<ListComposePreviewsTask>,
    private val captureComposePreviews: TaskProvider<CaptureComposePreviewsTask>,
    private val registerRuntimeConfiguration: (String, Configuration) -> Unit,
) {
    fun configure() {
        project.plugins.withId(ANDROID_APPLICATION_PLUGIN_ID) {
            configureAndroidComponents()
        }
        project.plugins.withId(ANDROID_LIBRARY_PLUGIN_ID) {
            configureAndroidComponents()
        }
        project.plugins.withId(ANDROID_KMP_LIBRARY_PLUGIN_ID) {
            configureAndroidComponents()
        }
    }

    private fun configureAndroidComponents() {
        val androidComponents =
            project.extensions.findByName(ANDROID_COMPONENTS_EXTENSION_NAME)
                ?: error("AgentPreview could not find the Android Components extension after an Android plugin was applied.")
        if (
            project.plugins.hasPlugin(ANDROID_KMP_LIBRARY_PLUGIN_ID) &&
            androidComponents.invokeIfPresent("onVariant", Action<Any> { variant -> wireSelectedVariant(variant) })
        ) {
            return
        }
        val selector = androidComponents.invokeRequired("selector").invokeRequired("all")
        androidComponents.invokeRequired(
            "onVariants",
            selector,
            Action<Any> { variant -> wireSelectedVariant(variant) },
        )
    }

    private fun wireSelectedVariant(variant: Any) {
        if (variant.name() != extension.android.variant.get() && !project.plugins.hasPlugin(ANDROID_KMP_LIBRARY_PLUGIN_ID)) return

        val runtimeConfiguration = variant.invokeRequired("getRuntimeConfiguration") as Configuration
        registerRuntimeConfiguration(extension.android.variant.get(), runtimeConfiguration)
        listComposePreviews.configure { task ->
            task.previewRuntimeClasspath.from(runtimeConfiguration)
        }
        captureComposePreviews.configure { task ->
            task.previewRuntimeClasspath.from(runtimeConfiguration)
        }

        val artifacts = variant.invokeRequired("getArtifacts")
        wireScopedClasses(
            artifacts = artifacts,
            scopeName = PROJECT_SCOPE,
            taskProvider = listComposePreviews,
            jarsProperty = ListComposePreviewsTask::androidProjectClassJars,
            dirsProperty = ListComposePreviewsTask::androidProjectClassDirs,
        )
        wireScopedClasses(
            artifacts = artifacts,
            scopeName = PROJECT_SCOPE,
            taskProvider = captureComposePreviews,
            jarsProperty = CaptureComposePreviewsTask::androidProjectClassJars,
            dirsProperty = CaptureComposePreviewsTask::androidProjectClassDirs,
        )
        wireScopedClasses(
            artifacts = artifacts,
            scopeName = ALL_SCOPE,
            taskProvider = listComposePreviews,
            jarsProperty = ListComposePreviewsTask::androidRuntimeClassJars,
            dirsProperty = ListComposePreviewsTask::androidRuntimeClassDirs,
        )
        wireScopedClasses(
            artifacts = artifacts,
            scopeName = ALL_SCOPE,
            taskProvider = captureComposePreviews,
            jarsProperty = CaptureComposePreviewsTask::androidRuntimeClassJars,
            dirsProperty = CaptureComposePreviewsTask::androidRuntimeClassDirs,
        )
        wireMergedAssets(variant.name(), artifacts)
        wireRobolectricResources(variant, artifacts)
    }

    private fun wireMergedAssets(
        variantName: String,
        artifacts: Any,
    ) {
        val assetsArtifact =
            artifacts.javaClass.classLoader.objectInstanceOrNull("com.android.build.api.artifact.SingleArtifact\$ASSETS") ?: return
        val mergedAssets = artifacts.invokeIfPresentReturning("get", assetsArtifact) ?: return
        captureComposePreviews.configure { task ->
            task.androidAssetsDirs.from(mergedAssets)
            if (!project.plugins.hasPlugin(ANDROID_KMP_LIBRARY_PLUGIN_ID)) {
                task.dependsOn(
                    task.fakeRenderer.map { isFake ->
                        if (isFake) emptyList() else listOf("merge${variantName.capitalizedVariantName()}Assets")
                    },
                )
            }
        }
    }

    private fun wireRobolectricResources(
        variant: Any,
        artifacts: Any,
    ) {
        val unitTestArtifacts = variant.invokeIfPresentReturningOrNull("getUnitTest")?.invokeIfPresentReturningOrNull("getArtifacts")
        val resourceArtifacts = unitTestArtifacts ?: artifacts
        val resourceClassLoader = resourceArtifacts.javaClass.classLoader
        val manifestClassLoader = artifacts.javaClass.classLoader
        val resourceApkArtifact =
            resourceClassLoader.objectInstanceOrNull("com.android.build.gradle.internal.scope.InternalArtifactType\$APK_FOR_LOCAL_TEST")
        val mergedManifestArtifact =
            manifestClassLoader.objectInstanceOrNull("com.android.build.api.artifact.SingleArtifact\$MERGED_MANIFEST")
        val resourceApk = resourceApkArtifact?.let { resourceArtifacts.invokeIfPresentReturningOrNull("get", it) }
        val mergedManifest = mergedManifestArtifact?.let { artifacts.invokeIfPresentReturningOrNull("get", it) }
        val namespace = variant.invokeIfPresentReturningOrNull("getNamespace")

        captureComposePreviews.configure { task ->
            @Suppress("UNCHECKED_CAST")
            (resourceApk as? Provider<RegularFile>)?.let { task.androidResourceApk.set(it) }
            @Suppress("UNCHECKED_CAST")
            (mergedManifest as? Provider<RegularFile>)?.let { task.androidMergedManifest.set(it) }
            when (namespace) {
                is String -> {
                    task.androidCustomPackage.set(namespace)
                }

                is Provider<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    task.androidCustomPackage.set(namespace as Provider<String>)
                }
            }
        }
    }

    private fun <T : Task> wireScopedClasses(
        artifacts: Any,
        scopeName: String,
        taskProvider: TaskProvider<T>,
        jarsProperty: (T) -> ListProperty<RegularFile>,
        dirsProperty: (T) -> ListProperty<Directory>,
    ) {
        val classLoader = artifacts.javaClass.classLoader
        val scope = classLoader.enumValue("com.android.build.api.variant.ScopedArtifacts\$Scope", scopeName)
        val classesArtifact = classLoader.objectInstance("com.android.build.api.artifact.ScopedArtifact\$CLASSES")
        artifacts
            .invokeRequired("forScope", scope)
            .invokeRequired("use", taskProvider)
            .invokeRequired("toGet", classesArtifact, jarsProperty, dirsProperty)
    }

    private fun Any.name(): String = invokeRequired("getName") as String

    private fun String.capitalizedVariantName(): String =
        replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }

    private fun Any.invokeRequired(
        methodName: String,
        vararg args: Any,
    ): Any =
        runCatching {
            val method = matchingMethod(methodName, args) ?: error("method not found")
            method.invoke(this, *args) ?: Unit
        }.getOrElse { exception ->
            error(
                "AgentPreview could not invoke Android Components API '$methodName' on ${javaClass.name}: " +
                    exception.message.orEmpty(),
            )
        }

    private fun Any.invokeIfPresent(
        methodName: String,
        vararg args: Any,
    ): Boolean {
        invokeIfPresentReturning(methodName, *args) ?: return false
        return true
    }

    private fun Any.invokeIfPresentReturning(
        methodName: String,
        vararg args: Any,
    ): Any? {
        val method = matchingMethod(methodName, args) ?: return null
        return runCatching {
            method.invoke(this, *args) ?: Unit
        }.getOrElse { exception ->
            error(
                "AgentPreview could not invoke Android Components API '$methodName' on ${javaClass.name}: " +
                    exception.message.orEmpty(),
            )
        }
    }

    private fun Any.invokeIfPresentReturningOrNull(
        methodName: String,
        vararg args: Any,
    ): Any? {
        val method = matchingMethod(methodName, args) ?: return null
        return runCatching { method.invoke(this, *args) }.getOrNull()
    }

    private fun Any.matchingMethod(
        methodName: String,
        args: Array<out Any>,
    ) = javaClass.methods.firstOrNull { method ->
        method.name == methodName &&
            method.parameterCount == args.size &&
            method.parameterTypes.zip(args).all { (parameterType, arg) -> parameterType.isAssignableFrom(arg.javaClass) }
    }

    private fun ClassLoader.enumValue(
        className: String,
        name: String,
    ): Any {
        val enumClass = loadClass(className).asSubclass(Enum::class.java)
        @Suppress("UNCHECKED_CAST")
        return java.lang.Enum.valueOf(enumClass as Class<out Enum<*>>, name)
    }

    private fun ClassLoader.objectInstance(className: String): Any = loadClass(className).getField("INSTANCE").get(null)

    private fun ClassLoader.objectInstanceOrNull(className: String): Any? = runCatching { objectInstance(className) }.getOrNull()

    internal companion object {
        const val ANDROID_APPLICATION_PLUGIN_ID = "com.android.application"
        const val ANDROID_LIBRARY_PLUGIN_ID = "com.android.library"
        private const val ANDROID_KMP_LIBRARY_PLUGIN_ID = "com.android.kotlin.multiplatform.library"
        private const val ANDROID_COMPONENTS_EXTENSION_NAME = "androidComponents"
        private const val PROJECT_SCOPE = "PROJECT"
        private const val ALL_SCOPE = "ALL"

        fun hasStandardAndroidPlugin(project: Project): Boolean =
            project.plugins.hasPlugin(ANDROID_APPLICATION_PLUGIN_ID) ||
                project.plugins.hasPlugin(ANDROID_LIBRARY_PLUGIN_ID)

        fun hasAndroidKmpComponents(project: Project): Boolean = project.plugins.hasPlugin(ANDROID_KMP_LIBRARY_PLUGIN_ID)
    }
}
