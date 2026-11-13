package ai.moa.app.ui

import ai.moa.core.Theme
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin

/** Quiet, entirely local artwork. It never animates or imitates a vehicle control. */
@Composable
fun SceneArt(theme: Theme, subjectId: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sky = when (theme) {
            Theme.SEA -> listOf(Color(0xFFD6EDFF), Color(0xFFF2FAFF), Color(0xFFC4EEFA))
            Theme.FOREST -> listOf(Color(0xFFE1F1DB), Color(0xFFF4F9E9), Color(0xFFD7ECDC))
            Theme.SPACE -> listOf(Color(0xFF303C77), Color(0xFF6473AD), Color(0xFFB0B3DD))
        }
        drawRect(Brush.verticalGradient(sky))
        withTransform({ scale(size.width / 640f, size.height / 420f, Offset.Zero) }) {
            when (theme) {
                Theme.SEA -> seaLandscape()
                Theme.FOREST -> forestLandscape()
                Theme.SPACE -> spaceLandscape()
            }
            when (subjectId) {
                "rain" -> {
                    cloud(314f, 145f, 1.12f)
                    listOf(253f to 216f, 310f to 232f, 365f to 210f).forEach { (x, y) ->
                        val drop = Path().apply {
                            moveTo(x, y - 13f)
                            cubicTo(x - 22f, y + 11f, x - 18f, y + 30f, x, y + 30f)
                            cubicTo(x + 18f, y + 30f, x + 22f, y + 11f, x, y - 13f)
                            close()
                        }
                        drawPath(drop, Color(0xFF75BCEB))
                        drawLine(Color.White.copy(alpha = .65f), Offset(x - 5f, y + 10f), Offset(x - 5f, y + 17f), 3f, StrokeCap.Round)
                    }
                }
                "rainbow" -> {
                    listOf(0xFFF0AAA4, 0xFFF1CAA0, 0xFFF5E7A2, 0xFFB5D5AC, 0xFF91C5DF, 0xFFB3B1DF).forEachIndexed { index, color ->
                        val inset = index * 13f
                        drawArc(Color(color), 180f, 180f, false, Offset(176f + inset, 88f + inset), Size(288f - inset * 2f, 288f - inset * 2f), style = Stroke(14f, cap = StrokeCap.Round))
                    }
                    cloud(190f, 226f, .50f)
                    cloud(446f, 226f, .50f)
                }
                "moon" -> {
                    drawCircle(Color(0xFFFFF3C6), 74f, Offset(322f, 153f))
                    drawCircle(Color(0xFFE7D59D).copy(alpha = .45f), 13f, Offset(293f, 132f))
                    drawCircle(Color(0xFFE7D59D).copy(alpha = .40f), 21f, Offset(345f, 172f))
                    drawCircle(Color(0xFFE7D59D).copy(alpha = .34f), 8f, Offset(316f, 191f))
                    sparkle(208f, 158f, 12f, Color.White)
                    sparkle(428f, 104f, 9f, Color.White)
                }
                "star" -> {
                    drawPath(starPath(320f, 154f, 81f, 38f), Brush.linearGradient(listOf(Color(0xFFFFF6D2), Color(0xFFFFD87F)), Offset(270f, 80f), Offset(365f, 230f)))
                    sparkle(206f, 181f, 13f, Color.White)
                    sparkle(431f, 111f, 10f, Color.White)
                    sparkle(427f, 224f, 7f, Color.White)
                }
                "mountain" -> {
                    mountain(178f, 269f, 178f, 162f, Color(0xFF6AA29D))
                    mountain(297f, 269f, 168f, 125f, Color(0xFF94C7B8))
                    cloud(403f, 111f, .45f)
                }
                "tree" -> {
                    tree(318f, 269f, 1.55f)
                    cloud(429f, 111f, .42f)
                    sparkle(217f, 149f, 7f, Color.White)
                }
                "sea", "river" -> {
                    cloud(315f, 120f, .75f)
                    val blue = if (subjectId == "river") Color(0xFF7FBECC) else Color(0xFF61BBD7)
                    repeat(3) { row ->
                        val wave = Path().apply {
                            moveTo(213f, 210f + row * 25f)
                            cubicTo(249f, 183f + row * 25f, 270f, 236f + row * 25f, 306f, 210f + row * 25f)
                            cubicTo(342f, 183f + row * 25f, 363f, 236f + row * 25f, 399f, 210f + row * 25f)
                            cubicTo(413f, 198f + row * 25f, 423f, 199f + row * 25f, 435f, 207f + row * 25f)
                        }
                        drawPath(wave, blue.copy(alpha = 1f - row * .17f), style = Stroke(9f, cap = StrokeCap.Round))
                    }
                }
                "wind" -> {
                    cloud(279f, 143f, .9f)
                    repeat(3) { index ->
                        val y = 143f + index * 39f
                        val wind = Path().apply {
                            moveTo(344f - index * 8f, y)
                            cubicTo(390f, y + 7f, 415f, y - 32f, 455f, y - 20f)
                            cubicTo(481f, y - 14f, 476f, y + 10f, 459f, y + 8f)
                        }
                        drawPath(wind, Color.White.copy(alpha = .9f), style = Stroke(6f, cap = StrokeCap.Round))
                    }
                }
                else -> cloud(317f, 153f, 1.35f)
            }
        }
    }
}

private fun DrawScope.cloud(x: Float, y: Float, scale: Float) = withTransform({
    translate(x, y)
    scale(scale, scale, Offset.Zero)
}) {
    drawOval(Color(0xFF97B9D8).copy(alpha = .17f), Offset(-96f, 39f), Size(192f, 25f))
    val path = Path().apply {
        moveTo(-77f, 40f)
        cubicTo(-130f, 40f, -127f, -21f, -81f, -28f)
        cubicTo(-73f, -71f, -17f, -87f, 10f, -47f)
        cubicTo(47f, -74f, 80f, -40f, 78f, -14f)
        cubicTo(119f, -16f, 132f, 41f, 81f, 46f)
        cubicTo(30f, 51f, -29f, 48f, -77f, 40f)
        close()
    }
    drawPath(path, Brush.linearGradient(listOf(Color.White, Color(0xFFF4FAFF), Color(0xFFCFDEEE)), Offset(-45f, -60f), Offset(35f, 58f)))
    drawOval(Color.White.copy(alpha = .50f), Offset(-80f, -29f), Size(89f, 45f))
}

private fun DrawScope.seaLandscape() {
    cloud(87f, 98f, .35f)
    cloud(563f, 157f, .40f)
    drawCircle(Color.White.copy(alpha = .47f), 31f, Offset(531f, 68f))
    val distant = Path().apply {
        moveTo(0f, 324f); cubicTo(128f, 269f, 184f, 343f, 294f, 302f)
        cubicTo(419f, 259f, 546f, 309f, 640f, 276f); lineTo(640f, 420f); lineTo(0f, 420f); close()
    }
    drawPath(distant, Color(0xFF9BD1DA).copy(alpha = .42f))
    val sea = Path().apply {
        moveTo(0f, 337f); cubicTo(131f, 305f, 223f, 356f, 347f, 330f)
        cubicTo(475f, 306f, 555f, 348f, 640f, 325f); lineTo(640f, 420f); lineTo(0f, 420f); close()
    }
    drawPath(sea, Brush.verticalGradient(listOf(Color(0xFF87D5E2), Color(0xFF4BAAC9)), startY = 300f, endY = 420f))
    repeat(4) { index ->
        val y = 350f + index * 19f
        val wave = Path().apply {
            moveTo(-10f, y); cubicTo(116f, y - 20f, 200f, y + 20f, 318f, y)
            cubicTo(438f, y - 19f, 530f, y + 22f, 655f, y - 3f)
        }
        drawPath(wave, Color.White.copy(alpha = .20f - index * .025f), style = Stroke(2.5f))
    }
    sailboat(480f, 365f, .78f)
}

private fun DrawScope.sailboat(x: Float, y: Float, scale: Float) = withTransform({ translate(x, y); scale(scale, scale, Offset.Zero) }) {
    drawOval(Color(0xFF276F9A).copy(alpha = .18f), Offset(-54f, 13f), Size(108f, 13f))
    drawLine(Color(0xFF887B67), Offset(0f, -98f), Offset(0f, 4f), 3f, StrokeCap.Round)
    val left = Path().apply { moveTo(-6f, -88f); lineTo(-49f, -7f); lineTo(-6f, -7f); close() }
    val right = Path().apply { moveTo(5f, -79f); lineTo(43f, -8f); lineTo(5f, -8f); close() }
    drawPath(left, Brush.horizontalGradient(listOf(Color(0xFFE1E8E9), Color.White), -50f, 0f))
    drawPath(right, Color(0xFFFFF9E9))
    val hull = Path().apply { moveTo(-56f, 2f); lineTo(53f, 2f); lineTo(33f, 23f); lineTo(-31f, 23f); close() }
    drawPath(hull, Color(0xFFB79469))
    drawLine(Color(0xFFEDD1A9), Offset(-54f, 3f), Offset(50f, 3f), 5f, StrokeCap.Round)
}

private fun DrawScope.forestLandscape() {
    cloud(90f, 89f, .32f)
    cloud(542f, 126f, .36f)
    drawCircle(Color(0xFFFFF9D9), 28f, Offset(520f, 64f))
    val backHill = Path().apply {
        moveTo(0f, 332f); cubicTo(96f, 236f, 210f, 254f, 325f, 315f)
        cubicTo(448f, 235f, 561f, 244f, 640f, 294f); lineTo(640f, 420f); lineTo(0f, 420f); close()
    }
    drawPath(backHill, Color(0xFFB8D7BA))
    val frontHill = Path().apply {
        moveTo(0f, 353f); cubicTo(126f, 290f, 221f, 372f, 342f, 339f)
        cubicTo(471f, 306f, 561f, 322f, 640f, 355f); lineTo(640f, 420f); lineTo(0f, 420f); close()
    }
    drawPath(frontHill, Color(0xFF85BDA4))
    val trail = Path().apply {
        moveTo(338f, 340f); cubicTo(364f, 356f, 270f, 379f, 294f, 420f)
        lineTo(380f, 420f); cubicTo(303f, 380f, 391f, 359f, 348f, 340f); close()
    }
    drawPath(trail, Color(0xFFE6DDAD))
    tree(89f, 362f, .64f)
    tree(138f, 347f, .45f)
    tree(536f, 372f, .77f)
    listOf(46f to 396f, 184f to 381f, 460f to 388f, 598f to 398f).forEach { (x, y) ->
        drawCircle(Color(0xFFFFF1C5), 3f, Offset(x, y))
        drawCircle(Color(0xFFF8F3E9), 3f, Offset(x + 11f, y + 3f))
    }
}

private fun DrawScope.tree(x: Float, y: Float, scale: Float) = withTransform({ translate(x, y); scale(scale, scale, Offset.Zero) }) {
    drawOval(Color(0xFF456F54).copy(alpha = .15f), Offset(-36f, -3f), Size(72f, 13f))
    drawLine(Color(0xFF927758), Offset(0f, -72f), Offset(0f, 0f), 9f, StrokeCap.Round)
    val leaves = Path().apply {
        moveTo(0f, -115f); cubicTo(-37f, -103f, -63f, -64f, -33f, -47f)
        cubicTo(-61f, -20f, -10f, -11f, 0f, -21f)
        cubicTo(34f, -9f, 58f, -29f, 34f, -50f)
        cubicTo(56f, -80f, 25f, -107f, 0f, -115f); close()
    }
    drawPath(leaves, Brush.linearGradient(listOf(Color(0xFF9CCD9C), Color(0xFF4E9779)), Offset(-20f, -110f), Offset(25f, -18f)))
    drawLine(Color(0xFFC0DFB2).copy(alpha = .6f), Offset(-13f, -84f), Offset(-22f, -66f), 4f, StrokeCap.Round)
}

private fun DrawScope.mountain(x: Float, y: Float, width: Float, height: Float, color: Color) {
    val mountain = Path().apply {
        moveTo(x, y); lineTo(x + width * .49f, y - height); lineTo(x + width, y); close()
    }
    drawPath(mountain, color)
    val shade = Path().apply {
        moveTo(x + width * .49f, y - height); lineTo(x + width * .62f, y); lineTo(x + width, y); close()
    }
    drawPath(shade, Color.Black.copy(alpha = .07f))
    val snow = Path().apply {
        moveTo(x + width * .49f, y - height); lineTo(x + width * .66f, y - height * .66f)
        lineTo(x + width * .52f, y - height * .74f); lineTo(x + width * .43f, y - height * .64f)
        lineTo(x + width * .34f, y - height * .70f); close()
    }
    drawPath(snow, Color(0xFFF4FAF6))
}

private fun DrawScope.spaceLandscape() {
    listOf(73f to 69f, 131f to 194f, 228f to 48f, 402f to 57f, 531f to 107f, 575f to 248f, 64f to 289f, 494f to 263f).forEachIndexed { index, (x, y) ->
        sparkle(x, y, if (index % 3 == 0) 6f else 3f, Color(0xFFFFF4D6).copy(alpha = .8f))
    }
    val constellation = listOf(Offset(80f, 168f), Offset(113f, 127f), Offset(159f, 143f), Offset(185f, 97f))
    constellation.zipWithNext().forEach { (a, b) -> drawLine(Color.White.copy(alpha = .17f), a, b, 1.5f) }
    constellation.forEach { drawCircle(Color.White.copy(alpha = .75f), 3f, it) }
    val hills = Path().apply {
        moveTo(0f, 348f); cubicTo(115f, 298f, 238f, 368f, 339f, 332f)
        cubicTo(464f, 284f, 569f, 332f, 640f, 315f); lineTo(640f, 420f); lineTo(0f, 420f); close()
    }
    drawPath(hills, Color(0xFF626FA4).copy(alpha = .52f))
    drawOval(Color(0xFFDDCBE8), Offset(466f, 312f), Size(85f, 63f))
    drawOval(Color(0xFFF8E7ED).copy(alpha = .8f), Offset(443f, 330f), Size(130f, 24f), style = Stroke(7f))
    drawCircle(Color(0xFFC8B3DA).copy(alpha = .5f), 8f, Offset(505f, 331f))
    sparkle(275f, 350f, 9f, Color.White.copy(alpha = .6f))
}

private fun DrawScope.sparkle(x: Float, y: Float, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, y - radius); quadraticBezierTo(x + radius * .2f, y - radius * .2f, x + radius, y)
        quadraticBezierTo(x + radius * .2f, y + radius * .2f, x, y + radius)
        quadraticBezierTo(x - radius * .2f, y + radius * .2f, x - radius, y)
        quadraticBezierTo(x - radius * .2f, y - radius * .2f, x, y - radius); close()
    }
    drawPath(path, color)
}

private fun starPath(x: Float, y: Float, outer: Float, inner: Float): Path = Path().apply {
    repeat(10) { index ->
        val angle = -Math.PI / 2 + index * Math.PI / 5
        val radius = if (index % 2 == 0) outer else inner
        val px = x + cos(angle).toFloat() * radius
        val py = y + sin(angle).toFloat() * radius
        if (index == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}
