/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package androidx.compose.ui.tooling.preview

@Repeatable
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class Preview(
    val name: String = "",
    val group: String = "",
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
    val fontScale: Float = 1.0f,
    val locale: String = "",
    val device: String = "",
    val uiMode: Int = 0,
)
