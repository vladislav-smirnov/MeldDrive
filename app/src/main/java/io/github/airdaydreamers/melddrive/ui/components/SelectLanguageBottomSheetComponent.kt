package io.github.airdaydreamers.melddrive.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.airdaydreamers.melddrive.R
import io.github.airdaydreamers.melddrive.data.model.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectLanguageBottomSheetComponent(
    currentLanguageCode: String,
    onDismissRequest: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    var isExpanded by remember { mutableStateOf(false) }

    val preferredLanguages = remember { getPreferredLanguages() }
    val systemHasIndonesian = remember(preferredLanguages) {
        "id" in preferredLanguages || "in" in preferredLanguages
    }

    val displayedLanguages = remember(isExpanded, currentLanguageCode, preferredLanguages, systemHasIndonesian) {
        getDisplayedLanguages(isExpanded, currentLanguageCode, preferredLanguages, systemHasIndonesian)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.testTag("select_language_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.language_select_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                items(displayedLanguages) { lang ->
                    val isSelected = lang.code == currentLanguageCode ||
                        (lang.code == "id" && currentLanguageCode == "in")

                    LanguageRowItem(
                        lang = lang,
                        isSelected = isSelected,
                        onLanguageSelected = onLanguageSelected,
                    )
                }

                if (!isExpanded && displayedLanguages.size < AppLanguage.supportedLanguages.size) {
                    item {
                        ShowAllButton(onClick = { isExpanded = true })
                    }
                }
            }
        }
    }
}

private fun getPreferredLanguages(): Set<String> {
    val set = mutableSetOf<String>()
    val systemLocales = android.os.LocaleList.getAdjustedDefault()
    for (i in 0 until systemLocales.size()) {
        systemLocales.get(i)?.language?.let { set.add(it) }
    }
    return set
}

private fun getDisplayedLanguages(
    isExpanded: Boolean,
    currentLanguageCode: String,
    preferredLanguages: Set<String>,
    systemHasIndonesian: Boolean,
): List<AppLanguage> {
    val supported = AppLanguage.supportedLanguages
    if (isExpanded) return supported
    return supported.filter { lang ->
        lang.code == "en" ||
            lang.code == currentLanguageCode ||
            (currentLanguageCode == "in" && lang.code == "id") ||
            lang.code in preferredLanguages ||
            (lang.code == "id" && systemHasIndonesian)
    }
}

@Composable
private fun LanguageRowItem(lang: AppLanguage, isSelected: Boolean, onLanguageSelected: (String) -> Unit) {
    NavigationDrawerItem(
        label = {
            Text(
                text = lang.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        selected = isSelected,
        onClick = { onLanguageSelected(lang.code) },
        badge = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.testTag("selected_indicator_${lang.code}"),
                )
            }
        },
        modifier = Modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .testTag("language_row_${lang.code}"),
    )
}

@Composable
private fun ShowAllButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("show_all_languages_button"),
        ) {
            Text(text = stringResource(R.string.language_show_all))
        }
    }
}
