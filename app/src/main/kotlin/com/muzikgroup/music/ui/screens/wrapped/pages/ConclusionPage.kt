package com.muzikgroup.music.ui.screens.wrapped.pages

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muzikgroup.music.R
import com.muzikgroup.music.ui.screens.wrapped.LocalWrappedManager
import com.muzikgroup.music.ui.screens.wrapped.WrappedConstants
import com.muzikgroup.music.ui.screens.wrapped.components.AnimatedBackground
import com.muzikgroup.music.ui.screens.wrapped.components.ShapeType
import com.muzikgroup.music.utils.ComposeToImage
import kotlinx.coroutines.launch

@Composable
fun ConclusionPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val manager = LocalWrappedManager.current
    val state by manager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isGeneratingShare by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(elementCount = 30, shapeTypes = listOf(ShapeType.Circle, ShapeType.Line))
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_static_foreground),
                contentDescription = stringResource(R.string.wrapped_logo_content_description),
                modifier = Modifier.size(96.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.wrapped_thank_you),
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(48.dp))
            OutlinedButton(
                onClick = {
                    if (isGeneratingShare) return@OutlinedButton
                    isGeneratingShare = true
                    scope.launch {
                        try {
                            val topSong = state.topSongs.firstOrNull()
                            val topArtist = state.topArtists.firstOrNull()
                            val image = ComposeToImage.createWrappedImage(
                                context = context,
                                year = WrappedConstants.YEAR,
                                totalMinutes = state.totalMinutes,
                                uniqueSongCount = state.uniqueSongCount,
                                uniqueArtistCount = state.uniqueArtistCount,
                                topSongTitle = topSong?.title,
                                topSongArtist = topSong?.artistName
                                    ?: topSong?.artists?.joinToString(", ") { it.name },
                                topSongThumbnailUrl = topSong?.thumbnailUrl,
                                topArtistName = topArtist?.artist?.name,
                                topArtistThumbnailUrl = topArtist?.artist?.thumbnailUrl,
                                topFiveSongs = state.topSongs.take(5).map { song ->
                                    song.title to (song.artistName
                                        ?: song.artists.firstOrNull()?.name.orEmpty())
                                },
                            )
                            val uri = ComposeToImage.saveBitmapAsFile(
                                context,
                                image,
                                "wrapped_${System.currentTimeMillis()}",
                            )
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    context.getString(R.string.wrapped_share_title),
                                )
                            )
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.failed_to_create_image, e.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isGeneratingShare = false
                        }
                    }
                },
                enabled = !isGeneratingShare,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                if (isGeneratingShare) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = stringResource(R.string.wrapped_share),
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClose,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = stringResource(R.string.wrapped_close),
                    style = TextStyle(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
