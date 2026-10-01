package dev.holo.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons drawn from the same SVG paths as the mockup. Tint them with Icon(tint = ...). */
object HoloIcons {
    val Phone = stroke(
        "M9.5 2.5h5a2.5 2.5 0 0 1 2.5 2.5v14a2.5 2.5 0 0 1-2.5 2.5h-5A2.5 2.5 0 0 1 7 19V5a2.5 2.5 0 0 1 2.5-2.5z",
        "M10.5 18.5h3", width = 1.8f,
    )
    val Globe = stroke(
        "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z", "M3.5 12h17",
        "M12 3.5c2.6 2.4 3.6 5.2 3.6 8.5s-1 6.1-3.6 8.5c-2.6-2.4-3.6-5.2-3.6-8.5s1-6.1 3.6-8.5z", width = 1.8f,
    )
    val Alert = stroke("M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z", "M12 7.5v5.5", "M12 16.3v0.3", width = 1.8f)
    val ArrowOut = stroke("M6.75 17.25 17.25 6.75", "M9 6.75h8.25V15", width = 2.4f)
    val Close = stroke("M7 7l10 10", "M17 7 7 17", width = 2.2f)
    val Search = stroke("M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z", "M15.5 15.5 21 21", width = 1.9f)
    val Mic = stroke("M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21", width = 1.8f)
    val Home = stroke("M4 11.5 12 4.5l8 7V20h-5.2v-5.5H9.2V20H4z")
    val Comms = stroke("M4 5.5h16v10.5H10l-4.5 3.8V16H4z")
    val Media = stroke("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M10 8.5v7l6-3.5z")
    val Tools = stroke("M4 7h9M17 7h3M15 5v4M4 12h3M11 12h9M9 10v4M4 17h11M19 17h1M17 15v4")
    val Apps = stroke("M5 5h5v5H5z", "M14 5h5v5h-5z", "M5 14h5v5H5z", "M14 14h5v5h-5z")
    val Wifi = stroke("M3 9.5a13 13 0 0 1 18 0", "M6 13a8.5 8.5 0 0 1 12 0", "M9 16.5a4 4 0 0 1 6 0", "M12 20h.01", width = 1.8f)
    val Bluetooth = stroke("M7 7.5l10 9-5 4V3.5l5 4-10 9", width = 1.7f)
    val Display = stroke(
        "M12 8a4 4 0 1 0 0 8a4 4 0 0 0 0-8z",
        "M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M18.7 5.3l-1.8 1.8M7.1 16.9l-1.8 1.8",
    )
    val Battery = stroke("M3 8h15v8H3z", "M18 10.5h2.5v3H18z", "M5.5 10.5h5v3h-5z")
    val Volume = stroke("M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9a4 4 0 0 1 0 6", "M18 6.5a7.5 7.5 0 0 1 0 11")
    val Settings = stroke(
        "M12 9a3 3 0 1 0 0 6a3 3 0 0 0 0-6z",
        "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M18.7 5.3l-2.1 2.1M7.4 16.6l-2.1 2.1",
    )
    val Contacts = stroke("M12 4a3.5 3.5 0 1 0 0 7a3.5 3.5 0 0 0 0-7z", "M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5")
    val Mail = stroke("M3.5 6h17v12h-17z", "M3.5 6l8.5 7 8.5-7")
    val Layers = stroke("M12 3.5l8.5 4.5-8.5 4.5L3.5 8z", "M3.5 12l8.5 4.5 8.5-4.5", "M3.5 16l8.5 4.5 8.5-4.5")
    val Check = stroke("M5 12.5l4.5 4.5L19 7.5", width = 2.2f)
    val ClearAll = stroke("M5 7h14", "M5 12h10", "M5 17h6", "M16 15l4 4", "M20 15l-4 4", width = 1.8f)
    val Chip = stroke("M8 8h8v8H8z", "M10 4v4M14 4v4M10 16v4M14 16v4M4 10h4M4 14h4M16 10h4M16 14h4", width = 1.6f)
    val Clock = stroke("M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z", "M12 7.5V12l3 2", width = 1.8f)
    val Drive = stroke(
        "M6 7.5c0-2.3 12-2.3 12 0v9c0 2.3-12 2.3-12 0z", "M6 7.5c0 2.3 12 2.3 12 0", "M6 12c0 2.3 12 2.3 12 0",
        width = 1.6f,
    )
    val Bolt = filled("M13.2 3.5 6.5 13.5h4.8l-1.6 7 6.8-10.2h-5z")
    val Play = filled("M8 5.5v13l10.5-6.5z")
    val Pause = filled("M7 5h3.5v14H7z", "M13.5 5H17v14h-3.5z")
    val Next = filled("M6 5.5v13l9-6.5z", "M16.5 5.5h2v13h-2z")
    val Prev = filled("M18 5.5v13l-9-6.5z", "M7.5 5.5h-2v13h2z")

    // 32-unit glyphs for the environment row
    val Gravity = stroke(
        "M16 4.9a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2z",
        "M16 11v8.5M11 14l5-2 5 2M13 26.5l3-7 3 7M8.5 28.5h15M5.5 16.5l1.5 1.5 1.5-1.5M23.5 16.5l1.5 1.5 1.5-1.5",
        width = 1.4f, viewport = 32f,
    )
    val Sun = stroke(
        "M16 11a5 5 0 1 0 0 10a5 5 0 1 0 0-10z",
        "M16 5v3M16 24v3M5 16h3M24 16h3M8.2 8.2l2.1 2.1M21.7 21.7l2.1 2.1M23.8 8.2l-2.1 2.1M10.3 21.7l-2.1 2.1",
        width = 1.4f, viewport = 32f,
    )
    val Gauge = stroke("M10 20a6 6 0 0 1 12 0", "M16 20l3-4.2", "M16 20h.01", width = 1.5f, viewport = 32f)
    val Hinge = stroke("M6 23l10-10 10 10", "M16 13v13", width = 1.5f, viewport = 32f)
    val Steps = filled(
        "M12.8 10.2a2.2 3.3 0 1 0 0 6.6a2.2 3.3 0 1 0 0-6.6z",
        "M12.8 18.2a1.5 1.4 0 1 0 0 2.8a1.5 1.4 0 1 0 0-2.8z",
        "M19.2 12.7a2.2 3.3 0 1 0 0 6.6a2.2 3.3 0 1 0 0-6.6z",
        "M19.2 20.7a1.5 1.4 0 1 0 0 2.8a1.5 1.4 0 1 0 0-2.8z",
        viewport = 32f,
    )
}

private fun stroke(vararg paths: String, width: Float = 1.6f, viewport: Float = 24f): ImageVector {
    val builder = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = viewport, viewportHeight = viewport,
    )
    paths.forEach { d ->
        builder.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private fun filled(vararg paths: String, viewport: Float = 24f): ImageVector {
    val builder = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = viewport, viewportHeight = viewport,
    )
    paths.forEach { d -> builder.addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White)) }
    return builder.build()
}
