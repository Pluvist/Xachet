@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/** Multi-term NFKC keyword matcher shared by academic query pages and unit tests. */
object AcademicKeywordSearch {
    private val whitespace = Regex("\\s+")
    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    internal fun terms(query: String): List<String> =
        normalize(query).trim().split(whitespace).filter { it.isNotEmpty() }

    /** Build once per response, rather than normalizing every record on every keystroke. */
    internal fun searchableText(record: AcademicRecord): String = normalize(buildString {
        append(record.title)
        record.fields.forEach {
            append('\n')
            append(it.second)
        }
    })

    internal fun matchesPrepared(text: String, terms: List<String>): Boolean =
        terms.all { text.contains(it) }

    fun matches(record: AcademicRecord, query: String): Boolean {
        val terms = terms(query)
        return terms.isEmpty() || matchesPrepared(searchableText(record), terms)
    }
}

/**
 * Material 3 Expressive search bar inside a Top App Bar paired with an expandable
 * [ExpandedFullScreenContainedSearchBar] for filtering or searching academic records & PDFs.
 */
@Composable
internal fun ExpressiveAcademicSearchAppBar(
    textFieldState: TextFieldState,
    placeholder: String,
    onBack: () -> Unit,
    onSearchSubmit: (String) -> Unit = {},
    searchBarState: SearchBarState = rememberContainedSearchBarState(),
    scrollBehavior: SearchBarScrollBehavior? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    expandedContent: @Composable ColumnScope.(collapse: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val collapse: () -> Unit = {
        scope.launch { searchBarState.animateToCollapsed() }
    }
    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { keyword ->
                onSearchSubmit(keyword.trim())
                collapse()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = collapse) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "收起搜索",
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            textFieldState.clearText()
                            onSearchSubmit("")
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "清空",
                        )
                    }
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        AppBarWithSearch(
            state = searchBarState,
            inputField = inputField,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            },
            actions = actions,
            scrollBehavior = scrollBehavior,
        )
        ExpandedFullScreenContainedSearchBar(
            state = searchBarState,
            inputField = inputField,
        ) {
            expandedContent(collapse)
        }
    }
}
