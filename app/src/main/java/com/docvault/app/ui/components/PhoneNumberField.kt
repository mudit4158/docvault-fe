package com.docvault.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.docvault.app.ui.theme.docVaultTextFieldColors

/**
 * One field: country dial code as a tappable prefix, number after it.
 *
 * The country sits INSIDE the text field rather than beside it as a second
 * box. A separate box has to be height-matched to the field by hand — which
 * never quite lines up, because an outlined field reserves vertical space for
 * its floating label — and it eats width that the number needs. As a prefix it
 * is aligned by construction and takes only the space the dial code needs,
 * which also leaves the trailing slot free for an action like "pick a contact".
 */
@Composable
fun PhoneNumberField(
    country: Country,
    nationalNumber: String,
    onCountryChange: (Country) -> Unit,
    onNationalNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Phone number",
    enabled: Boolean = true,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    trailingIcon: (@Composable () -> Unit)? = null,
    testTagPrefix: String = "phone",
) {
    var pickerOpen by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = nationalNumber,
        // Digits only: a pasted "+91 98765-43210" is cleaned rather than
        // rejected, and the dial code is already selected in the prefix.
        onValueChange = { onNationalNumberChange(PhoneNumber.digitsOnly(it)) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        colors = docVaultTextFieldColors(),
        leadingIcon = {
            DialCodePrefix(
                country = country,
                enabled = enabled,
                onClick = { pickerOpen = true },
                modifier = Modifier.testTag("${testTagPrefix}_country"),
            )
        },
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = imeAction,
        ),
        modifier = modifier.testTag("${testTagPrefix}_number"),
    )

    if (pickerOpen) {
        CountryPickerDialog(
            selected = country,
            onDismiss = { pickerOpen = false },
            onSelect = {
                onCountryChange(it)
                pickerOpen = false
            },
        )
    }
}

/** Flag, dial code and a caret — tappable, sized to its content. */
@Composable
private fun DialCodePrefix(
    country: Country,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = country.shortLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = "Choose country",
            modifier = Modifier.size(20.dp),
        )
        // Hairline separating the prefix from the number the user types.
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun CountryPickerDialog(
    selected: Country,
    onDismiss: () -> Unit,
    onSelect: (Country) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val matches = remember(query) {
        if (query.isBlank()) {
            Countries.ALL
        } else {
            val q = query.trim().lowercase()
            Countries.ALL.filter {
                it.name.lowercase().contains(q) ||
                    it.dialCode.contains(q) ||
                    it.isoCode.lowercase() == q
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select country") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    placeholder = { Text("India, +91, IN") },
                    singleLine = true,
                    colors = docVaultTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("country_search"),
                )
                Spacer(Modifier.padding(4.dp))

                if (matches.isEmpty()) {
                    Text(
                        "No matching country.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(matches, key = { it.isoCode }) { c ->
                            CountryRow(
                                country = c,
                                isSelected = c.isoCode == selected.isoCode,
                                onClick = { onSelect(c) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CountryRow(country: Country, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag("country_${country.isoCode}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${country.flag}  ${country.name}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = country.dialCode,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
