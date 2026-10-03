package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _flagFilled: ImageVector? = null
val FlagFilledIcon: ImageVector
    get() {
        if (_flagFilled != null) return _flagFilled!!
        _flagFilled = ImageVector.Builder(
            name = "FlagFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.4f, 6f)
                lineTo(14f, 4f)
                horizontalLineTo(5f)
                verticalLineToRelative(17f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(5.6f)
                lineToRelative(0.4f, 2f)
                horizontalLineToRelative(7f)
                verticalLineTo(6f)
                close()
            }
        }.build()
        return _flagFilled!!
    }

private var _flagOutlined: ImageVector? = null
val FlagOutlinedIcon: ImageVector
    get() {
        if (_flagOutlined != null) return _flagOutlined!!
        _flagOutlined = ImageVector.Builder(
            name = "FlagOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.36f, 6f)
                lineToRelative(0.4f, 2f)
                horizontalLineTo(18f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(-3.36f)
                lineToRelative(-0.4f, -2f)
                horizontalLineTo(7f)
                verticalLineTo(6f)
                horizontalLineToRelative(5.36f)
                moveTo(14f, 4f)
                horizontalLineTo(5f)
                verticalLineToRelative(17f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(5.6f)
                lineToRelative(0.4f, 2f)
                horizontalLineToRelative(7f)
                verticalLineTo(6f)
                horizontalLineToRelative(-5.6f)
                lineTo(14f, 4f)
                close()
            }
        }.build()
        return _flagOutlined!!
    }
