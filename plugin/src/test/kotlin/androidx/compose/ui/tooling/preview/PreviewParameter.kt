/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package androidx.compose.ui.tooling.preview

import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PreviewParameter(
    val value: KClass<out PreviewParameterProvider<*>>,
    val limit: Int = Int.MAX_VALUE,
)

interface PreviewParameterProvider<T> {
    val values: Sequence<T>
}
