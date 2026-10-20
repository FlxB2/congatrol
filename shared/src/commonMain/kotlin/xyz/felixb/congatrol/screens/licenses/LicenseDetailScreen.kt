package xyz.felixb.congatrol.screens.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.license_author
import congatrol.shared.generated.resources.license_version
import congatrol.shared.generated.resources.license_view_online
import congatrol.shared.generated.resources.license_website
import congatrol.shared.generated.resources.licenses_empty
import congatrol.shared.generated.resources.licenses_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import xyz.felixb.congatrol.components.CongatrolCard
import xyz.felixb.congatrol.data.OpenSourceLibrary
import xyz.felixb.congatrol.data.OpenSourceLicense
import xyz.felixb.congatrol.theme.CongatrolBackground
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.PreviewCongatrol

/** One library: what it is, who wrote it, and the full text of the licence(s) it ships under. */
@Composable
fun LicenseDetailScreen(
    libraryId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val viewModel = koinViewModel<LicenseDetailViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(libraryId) { viewModel.load(libraryId) }

    LicenseDetailScreenContent(
        state = state,
        onBack = onBack,
        onOpenUrl = { url ->
            // No browser to hand the link to (or a URL the POM got wrong) is not worth a dialog on
            // a page whose content is already on screen.
            try {
                uriHandler.openUri(url)
            } catch (e: IllegalArgumentException) {
                // Nothing to do.
            }
        },
        modifier = modifier,
        bottomInset = bottomInset,
    )
}

@Composable
fun LicenseDetailScreenContent(
    state: LicenseDetailUiState,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = (state as? LicenseDetailUiState.Content)?.library?.name ?: stringResource(Res.string.licenses_title),
            onBack = onBack,
        )
        when (state) {
            LicenseDetailUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            LicenseDetailUiState.NotFound ->
                Text(
                    text = stringResource(Res.string.licenses_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            is LicenseDetailUiState.Content -> LibraryDetail(state.library, onOpenUrl, bottomInset)
        }
    }
}

@Composable
private fun LibraryDetail(
    library: OpenSourceLibrary,
    onOpenUrl: (String) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(0.dp))
        val version = library.version
        val author = library.author
        val website = library.website
        if (version != null || author != null || website != null) {
            CongatrolCard(modifier = Modifier.fillMaxWidth()) {
                if (version != null) FactRow(label = stringResource(Res.string.license_version), value = version)
                if (author != null) {
                    if (version != null) RowDivider()
                    FactRow(label = stringResource(Res.string.license_author), value = author)
                }
                if (website != null) {
                    if (version != null || author != null) RowDivider()
                    LinkRow(title = stringResource(Res.string.license_website), subtitle = website, onClick = { onOpenUrl(website) })
                }
            }
        }
        library.licenses.forEach { license ->
            Text(
                text = license.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp),
            )
            LicenseBody(license = license, onOpenUrl = onOpenUrl)
        }
        Spacer(Modifier.height(bottomInset))
    }
}

/** The licence's full text, or — where the report knows it only by name — a link to read it. */
@Composable
private fun LicenseBody(
    license: OpenSourceLicense,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = license.text
    val url = license.url
    if (text == null && url == null) return
    CongatrolCard(modifier = modifier.fillMaxWidth()) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("license_text").fillMaxWidth().padding(16.dp),
            )
        } else if (url != null) {
            LinkRow(title = stringResource(Res.string.license_view_online), subtitle = url, onClick = { onOpenUrl(url) })
        }
    }
}

/** A read-only label and its value. */
@Composable
private fun FactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = modifier.padding(horizontal = 16.dp))
}

@PreviewCongatrol
@Preview
@Composable
internal fun LicenseDetailScreenContentPreview() {
    CongatrolTheme {
        CongatrolBackground {
            LicenseDetailScreenContent(
                state = LicenseDetailUiState.Content(previewLibraries.last()),
                onBack = {},
                onOpenUrl = {},
            )
        }
    }
}
