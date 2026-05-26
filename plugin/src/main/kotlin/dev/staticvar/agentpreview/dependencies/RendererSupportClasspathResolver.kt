package dev.staticvar.agentpreview.dependencies

internal data class ResolvedArtifactCoordinate(
    val group: String,
    val module: String,
    val version: String,
    val filePath: String,
) {
    val moduleCoordinate: ResolvedModuleCoordinate = ResolvedModuleCoordinate(group, module, version)
    val notation: String = "$group:$module:$version"
}

internal data class RendererSupportResolution(
    val composeVersion: String,
    val consumerArtifactFiles: List<String>,
    val pluginArtifactCoordinates: List<String>,
    val warnings: List<String>,
)

/**
 * Prefers renderer-support artifacts already present on the selected variant and resolves only missing
 * tooling/test support in plugin-owned detached configurations.
 */
internal class RendererSupportClasspathResolver(
    private val composeVersionDetector: ComposeVersionDetector = ComposeVersionDetector(),
) {
    fun resolve(
        selectedVariant: String,
        inspectedConfigurations: List<String>,
        runtimeArtifacts: List<ResolvedArtifactCoordinate>,
    ): RendererSupportResolution {
        val detection = composeVersionDetector.detect(runtimeArtifacts.map { it.moduleCoordinate })
        val composeVersion = detection.version ?: RendererDependencyPolicy.FALLBACK_COMPOSE_VERSION
        val warnings =
            if (detection.version == null) {
                listOf(
                    "AgentPreview: could not infer a Compose UI version for selected variant '$selectedVariant'. " +
                        "Inspected configurations: ${inspectedConfigurations.joinToString(", ")}. " +
                        "Falling back to renderer-only tooling coordinates at $composeVersion.",
                )
            } else {
                emptyList()
            }

        val consumerArtifacts =
            runtimeArtifacts.filter {
                RendererDependencyPolicy.isRendererSupportModule(it.group, it.module)
            }
        val consumerModuleKeys = consumerArtifacts.map { "${it.group}:${it.module}" }.toSet()

        val pluginCoordinates =
            RendererDependencyPolicy
                .fallbackRendererCoordinates(composeVersion)
                .filterNot { coordinate -> RendererDependencyPolicy.moduleKey(coordinate) in consumerModuleKeys }

        return RendererSupportResolution(
            composeVersion = composeVersion,
            consumerArtifactFiles = consumerArtifacts.map { it.filePath },
            pluginArtifactCoordinates = pluginCoordinates,
            warnings = warnings,
        )
    }
}
