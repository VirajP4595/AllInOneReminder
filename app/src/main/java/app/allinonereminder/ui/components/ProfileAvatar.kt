package app.allinonereminder.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** A person's photo in a circle, or their initial on their colour when there is no photo (#80, #81). */
@Composable
fun ProfileAvatar(
    name: String,
    colorArgb: Int,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val bitmap = remember(photoPath) {
        photoPath?.let { path ->
            runCatching {
                val file = File(path)
                if (!file.exists()) return@runCatching null
                // Small decode: avatars never need the full photo.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    val color = Color(colorArgb)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                color = readableTextColor(color),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp
            )
        }
    }
}
