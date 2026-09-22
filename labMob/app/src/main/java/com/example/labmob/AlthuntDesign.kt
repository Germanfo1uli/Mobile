package com.example.labmob

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val HuntInk = Color(0xFF050507)
internal val HuntRed = Color(0xFFF00018)
internal val HuntPaper = Color(0xFFF4F1E9)
internal val HuntGray = Color(0xFF3A3A40)

internal val SlashShape = GenericShape { size, _ ->
    moveTo(size.width * .08f, 0f)
    lineTo(size.width, size.height * .06f)
    lineTo(size.width * .92f, size.height)
    lineTo(0f, size.height * .86f)
    close()
}

internal val ReverseSlashShape = GenericShape { size, _ ->
    moveTo(0f, size.height * .08f)
    lineTo(size.width * .92f, 0f)
    lineTo(size.width, size.height * .88f)
    lineTo(size.width * .08f, size.height)
    close()
}

internal val DialogueShape = GenericShape { size, _ ->
    moveTo(size.width * .11f, size.height * .03f)
    lineTo(size.width * .97f, 0f)
    lineTo(size.width, size.height * .94f)
    lineTo(size.width * .12f, size.height)
    lineTo(size.width * .12f, size.height * .68f)
    lineTo(0f, size.height * .57f)
    lineTo(size.width * .12f, size.height * .47f)
    close()
}

internal val NoteShape = GenericShape { size, _ ->
    moveTo(size.width * .04f, 0f)
    lineTo(size.width * .96f, size.height * .03f)
    lineTo(size.width, size.height * .92f)
    lineTo(size.width * .9f, size.height)
    lineTo(size.width * .05f, size.height * .97f)
    lineTo(0f, size.height * .08f)
    close()
}

@Composable
internal fun CrimeBackdrop(dim: Float = .38f) {
    Image(
        painter = painterResource(R.drawable.althunt_city_collage_v2),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to HuntInk.copy(alpha = dim * .45f),
                .55f to HuntInk.copy(alpha = dim),
                1f to HuntInk.copy(alpha = .94f),
            ),
        ),
    )
    Canvas(Modifier.fillMaxSize().alpha(.72f)) {
        val slash = Path().apply {
            moveTo(-size.width * .2f, size.height * .19f)
            lineTo(size.width * 1.05f, size.height * .06f)
            lineTo(size.width * 1.05f, size.height * .095f)
            lineTo(-size.width * .2f, size.height * .25f)
            close()
        }
        drawPath(slash, HuntRed)
    }
}

@Composable
internal fun RansomTitle(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 38,
    align: TextAlign = TextAlign.Start,
) {
    Box(modifier) {
        Text(
            text, color = HuntRed, fontSize = size.sp, lineHeight = (size - 3).sp,
            fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, textAlign = align,
            modifier = Modifier.fillMaxWidth().offset(5.dp, 6.dp),
        )
        Text(
            text, color = Color.White, fontSize = size.sp, lineHeight = (size - 3).sp,
            fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SlashMenuItem(
    number: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    red: Boolean,
    testTag: String,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val background = accent ?: if (red) HuntRed else HuntPaper
    val foreground = if (accent != null || red) Color.White else HuntInk
    Box(
        Modifier.fillMaxWidth().height(82.dp).rotate(if (red) -1.2f else .8f)
            .clickable(enabled = enabled, onClick = onClick).testTag(testTag).alpha(if (enabled) 1f else .5f),
    ) {
        Box(
            Modifier.matchParentSize().offset(6.dp, 7.dp).background(Color.White, ReverseSlashShape),
        )
        Row(
            Modifier.fillMaxSize().background(background, SlashShape).padding(start = 16.dp, end = 22.dp, top = 10.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                number, color = when {
                    accent != null -> Color.White
                    red -> HuntInk
                    else -> HuntRed
                }, fontSize = 33.sp,
                fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = foreground, fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, maxLines = 1)
                Text(subtitle, color = foreground.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp, maxLines = 1)
            }
            InkChevron(foreground)
        }
    }
}

@Composable
private fun InkChevron(color: Color) {
    Canvas(Modifier.size(27.dp)) {
        val path = Path().apply {
            moveTo(size.width * .12f, size.height * .15f)
            lineTo(size.width * .75f, size.height * .5f)
            lineTo(size.width * .12f, size.height * .85f)
        }
        drawPath(path, color, style = Stroke(size.width * .16f))
    }
}

@Composable
internal fun CharacterDialogue(
    @DrawableRes character: Int,
    speaker: String,
    text: String,
    modifier: Modifier = Modifier,
    dialogueFraction: Float = .63f,
) {
    Box(modifier.fillMaxWidth().heightIn(min = 260.dp)) {
        Image(
            painter = painterResource(character),
            contentDescription = speaker,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(.60f).height(250.dp),
        )
        Column(
            Modifier.align(Alignment.BottomEnd).fillMaxWidth(dialogueFraction).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                speaker.uppercase(), color = Color.White,
                fontSize = if (speaker.length > 14) 9.sp else 12.sp,
                fontWeight = FontWeight.Black, maxLines = 1,
                modifier = Modifier.rotate(-2f).background(HuntRed, SlashShape).padding(horizontal = 15.dp, vertical = 6.dp),
            )
            Text(
                text, color = HuntInk, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().background(HuntPaper, DialogueShape)
                    .padding(start = 30.dp, end = 16.dp, top = 17.dp, bottom = 18.dp),
            )
        }
    }
}
