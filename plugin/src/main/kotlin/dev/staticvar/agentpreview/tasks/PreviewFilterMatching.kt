/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.tasks

import dev.staticvar.agentpreview.model.PreviewDescriptor

private val PREVIEW_PARAMETER_ID_SUFFIX_REGEX = Regex(":previewParam-\\d+$")
private val PREVIEW_PARAMETER_SHORTHAND_ID_REGEX = Regex("^previewParam-\\d+$")

internal fun PreviewDescriptor.matchesBeforePreviewParameterExpansion(filters: Set<String>): Boolean =
    filters.any { filter ->
        when {
            filter.isPreviewParameterShorthand() -> {
                previewParameter != null
            }

            filter.hasPreviewParameterSuffix() -> {
                previewParameter != null && (id == filter.withoutPreviewParameterSuffix() || id == filter)
            }

            else -> {
                id.matchesPreviewFilter(filter) ||
                    name.matchesPreviewFilter(filter) ||
                    fullyQualifiedFunctionName.matchesPreviewFilter(filter)
            }
        }
    }

internal fun PreviewDescriptor.matchesAfterPreviewParameterExpansion(filters: Set<String>): Boolean =
    filters.any { filter ->
        when {
            filter.isPreviewParameterShorthand() -> {
                id.endsWith(":$filter")
            }

            filter.hasPreviewParameterSuffix() -> {
                id == filter
            }

            else -> {
                id.matchesPreviewFilter(filter) ||
                    parentPreviewId().matchesPreviewFilter(filter) ||
                    name.matchesPreviewFilter(filter) ||
                    fullyQualifiedFunctionName.matchesPreviewFilter(filter)
            }
        }
    }

internal fun String.hasPreviewParameterSuffix(): Boolean = PREVIEW_PARAMETER_ID_SUFFIX_REGEX.containsMatchIn(this)

internal fun String.isPreviewParameterShorthand(): Boolean = PREVIEW_PARAMETER_SHORTHAND_ID_REGEX.matches(this)

internal fun String.withoutPreviewParameterSuffix(): String = replace(PREVIEW_PARAMETER_ID_SUFFIX_REGEX, "")

internal fun Set<String>.previewParameterFilterIndexes(): Set<Int> =
    mapNotNull { filter ->
        when {
            filter.isPreviewParameterShorthand() -> filter.substringAfter("previewParam-").toIntOrNull()
            filter.hasPreviewParameterSuffix() -> filter.substringAfterLast(":previewParam-").toIntOrNull()
            else -> null
        }
    }.toSet()

internal fun String?.matchesPreviewFilter(filter: String): Boolean = this == filter || this?.contains(filter) == true

private fun PreviewDescriptor.parentPreviewId(): String =
    if (previewParameter?.index == null) {
        id
    } else {
        id.replace(PREVIEW_PARAMETER_ID_SUFFIX_REGEX, "")
    }
