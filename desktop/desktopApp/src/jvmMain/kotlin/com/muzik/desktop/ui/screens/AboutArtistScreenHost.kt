package com.muzik.desktop.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.muzik.desktop.state.app.AppState
import com.muzik.desktop.state.content.ArtistState
import com.muzik.desktop.utils.saveArtistPhoto
import com.muzik.shared.model.ArtistItem
import com.muzik.shared.settings.Settings
import com.muzik.shared.ui.navigation.LocalNavigator
import com.muzik.shared.ui.screens.AboutArtistContent
import com.muzik.shared.ui.screens.ArtistPhotoActions
import com.muzik.shared.viewmodels.AboutArtistViewModel
import com.muzik.shared.viewmodels.artistCatalogTitles
import kotlinx.coroutines.launch

@Composable
fun AboutArtistScreenHost(
    artistId: String,
    artistName: String,
    initialInfo: ArtistItem? = null,
) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val viewModel = remember(artistId, artistName) { AboutArtistViewModel(artistId, artistName, scope) }
    val uiState by viewModel.uiState.collectAsState()
    val catalogTitles = remember(ArtistState.artistData) { artistCatalogTitles(ArtistState.artistData) }
    LaunchedEffect(viewModel, catalogTitles, ArtistState.isArtistLoading, Settings.shared.lastResolvedContentLanguage) {
        if (!ArtistState.isArtistLoading) viewModel.load(catalogTitles)
    }
    val photoActions =
        remember(artistName) {
            ArtistPhotoActions(
                share = { AppState.shareLink(it, artistName) },
                save = { saveArtistPhoto(it, artistName) },
                showMessage = { message -> AppState.scope.launch { AppState.snackbarHostState.showSnackbar(message) } },
            )
        }
    AboutArtistContent(
        artistName = artistName,
        info = ArtistState.artistData["header"]?.firstOrNull() as? ArtistItem ?: initialInfo,
        uiState = uiState,
        photoActions = photoActions,
        onBackClick = { navigator.goBack() },
    )
}
