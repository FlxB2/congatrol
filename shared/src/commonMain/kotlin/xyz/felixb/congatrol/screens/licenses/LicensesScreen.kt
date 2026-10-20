package xyz.felixb.congatrol.screens.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import congatrol.shared.generated.resources.Res
import congatrol.shared.generated.resources.ic_back
import congatrol.shared.generated.resources.ic_chevron
import congatrol.shared.generated.resources.licenses_back
import congatrol.shared.generated.resources.licenses_empty
import congatrol.shared.generated.resources.licenses_intro
import congatrol.shared.generated.resources.licenses_title
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import xyz.felixb.congatrol.data.OpenSourceLibrary
import xyz.felixb.congatrol.data.OpenSourceLicense
import xyz.felixb.congatrol.theme.CongatrolBackground
import xyz.felixb.congatrol.theme.CongatrolTheme
import xyz.felixb.congatrol.theme.PreviewCongatrol

/** Every open source component this build ships; tapping one opens its licence. */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    onLibraryClick: (libraryId: String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val viewModel = koinViewModel<LicensesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LicensesScreenContent(state = state, onBack = onBack, onLibraryClick = onLibraryClick, modifier = modifier, bottomInset = bottomInset)
}

@Composable
fun LicensesScreenContent(
    state: LicensesUiState,
    onBack: () -> Unit,
    onLibraryClick: (libraryId: String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(Res.string.licenses_title), onBack = onBack)
        when (state) {
            LicensesUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is LicensesUiState.Content -> LibraryList(state.libraries, onLibraryClick, bottomInset)
        }
    }
}

/** A back arrow and what the screen is, for the screens that are gone to and come back from. */
@Composable
internal fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
            Icon(
                painterResource(Res.drawable.ic_back),
                contentDescription = stringResource(Res.string.licenses_back),
                modifier = Modifier.size(22.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The libraries as one long pane. A card is a plain column, which would compose all ~150 rows up
 * front, so the lazy list draws the pane itself: every row carries the fill and only the first
 * and last round their outer corners.
 */
@Composable
private fun LibraryList(
    libraries: ImmutableList<OpenSourceLibrary>,
    onLibraryClick: (libraryId: String) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("licenses_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + 16.dp),
    ) {
        item {
            Text(
                text = stringResource(if (libraries.isEmpty()) Res.string.licenses_empty else Res.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
            )
        }
        itemsIndexed(libraries, key = { _, library -> library.id }) { index, library ->
            Column(
                modifier =
                    Modifier
                        .clip(paneSliceShape(index = index, count = libraries.size))
                        .background(colors.surfaceContainerLow),
            ) {
                if (index > 0) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                LinkRow(
                    title = library.name,
                    subtitle = library.summary(),
                    onClick = { onLibraryClick(library.id) },
                    // One tag for every row: which library sits where changes with every dependency bump.
                    modifier = Modifier.testTag("license_row"),
                )
            }
        }
    }
}

/** A row that leads somewhere: what, a line about it, and a chevron. */
@Composable
internal fun LinkRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            painterResource(Res.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp).size(16.dp),
        )
    }
}

/** The pane's corner shape for row [index] of [count]: rounded only where the pane itself ends. */
@Composable
private fun paneSliceShape(
    index: Int,
    count: Int,
): CornerBasedShape {
    val square = CornerSize(0.dp)
    val pane = MaterialTheme.shapes.medium
    return pane.copy(
        topStart = if (index == 0) pane.topStart else square,
        topEnd = if (index == 0) pane.topEnd else square,
        bottomStart = if (index == count - 1) pane.bottomStart else square,
        bottomEnd = if (index == count - 1) pane.bottomEnd else square,
    )
}

/** The row's second line, e.g. `3.6.0 · Apache License 2.0`; the version is absent for fonts and icons. */
private fun OpenSourceLibrary.summary(): String? =
    (listOfNotNull(version) + licenses.map { it.name })
        .joinToString(" · ")
        .ifEmpty { null }

internal val previewLibraries: ImmutableList<OpenSourceLibrary> =
    persistentListOf(
        OpenSourceLibrary(
            id = "io.ktor:ktor-client-core",
            name = "ktor-client-core",
            version = "3.6.0",
            author = "JetBrains",
            website = "https://github.com/ktorio/ktor",
            licenses = persistentListOf(OpenSourceLicense("Apache License 2.0", "https://spdx.org/licenses/Apache-2.0.html", null)),
        ),
        OpenSourceLibrary(
            id = "com.googlefonts:manrope",
            name = "Manrope",
            version = null,
            author = "The Manrope Project Authors",
            website = "https://github.com/googlefonts/manrope",
            licenses = persistentListOf(OpenSourceLicense("SIL Open Font License 1.1", null, null)),
        ),
        OpenSourceLibrary(
            id = "com.phosphoricons:phosphor",
            name = "Phosphor Icons",
            version = null,
            author = "Phosphor Icons",
            website = "https://phosphoricons.com",
            licenses =
                persistentListOf(
                    OpenSourceLicense(
                        name = "MIT License",
                        url = "https://spdx.org/licenses/MIT.html",
                        text =
                            "MIT License\n\nCopyright (c) 2023 Phosphor Icons\n\n" +
                                "Permission is hereby granted, free of charge, to any person obtaining a copy of this " +
                                "software and associated documentation files (the \"Software\"), to deal in the Software " +
                                "without restriction.",
                    ),
                ),
        ),
    )

@PreviewCongatrol
@Preview
@Composable
internal fun LicensesScreenContentPreview() {
    CongatrolTheme {
        CongatrolBackground {
            LicensesScreenContent(state = LicensesUiState.Content(previewLibraries), onBack = {}, onLibraryClick = {})
        }
    }
}
