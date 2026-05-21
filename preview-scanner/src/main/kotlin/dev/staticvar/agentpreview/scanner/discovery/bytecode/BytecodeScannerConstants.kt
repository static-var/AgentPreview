/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.discovery.bytecode

import org.objectweb.asm.ClassReader

internal const val PREVIEW_DESCRIPTOR = "Landroidx/compose/ui/tooling/preview/Preview;"
internal const val CLASS_READER_FLAGS = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES

internal const val PREVIEW_NAME = "name"
internal const val PREVIEW_GROUP = "group"
internal const val PREVIEW_WIDTH_DP = "widthDp"
internal const val PREVIEW_HEIGHT_DP = "heightDp"
internal const val PREVIEW_SHOW_BACKGROUND = "showBackground"
internal const val PREVIEW_BACKGROUND_COLOR = "backgroundColor"
internal const val PREVIEW_FONT_SCALE = "fontScale"
internal const val PREVIEW_LOCALE = "locale"
internal const val PREVIEW_DEVICE = "device"
internal const val PREVIEW_UI_MODE = "uiMode"
