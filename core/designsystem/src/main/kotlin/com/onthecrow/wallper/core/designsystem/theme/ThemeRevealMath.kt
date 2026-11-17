package com.onthecrow.wallper.core.designsystem.theme

import kotlin.math.hypot
import kotlin.math.max

/** The radius at which a circle about ([x], [y]) covers the whole [width] × [height] box: its farthest corner. */
internal fun farthestCornerDistance(x: Float, y: Float, width: Float, height: Float): Float = hypot(
    max(x, width - x),
    max(y, height - y),
)
