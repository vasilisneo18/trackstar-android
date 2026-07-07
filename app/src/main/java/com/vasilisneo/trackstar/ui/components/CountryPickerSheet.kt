package com.vasilisneo.trackstar.ui.components

// Shared searchable country picker (iOS CountryPickerSheet), used by the Personal Details
// registration step and the Personal Info profile screen.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.theme.TrackstarSurface
import java.util.Locale

private data class CountryOption(val name: String, val flag: String)

private fun regionFlagEmoji(isoCode: String): String =
    isoCode.uppercase().map { 0x1F1E6 - 'A'.code + it.code }
        .joinToString("") { String(Character.toChars(it)) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selectedCountry: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val allCountries = remember {
        Locale.getISOCountries()
            .mapNotNull { code ->
                val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
                if (name.isBlank() || name == code) null else CountryOption(name, regionFlagEmoji(code))
            }
            .distinctBy { it.name }
            .sortedBy { it.name }
    }
    var searchText by remember { mutableStateOf("") }
    val filtered = remember(searchText) {
        if (searchText.isBlank()) allCountries
        else allCountries.filter { it.name.contains(searchText, ignoreCase = true) }
    }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TrackstarSurface,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "Country",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search countries", color = Color.White.copy(alpha = 0.4f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered) { country ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(country.name); onDismiss() }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(country.flag, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(country.name, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
                        if (country.name == selectedCountry) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2E80FF))
                        }
                    }
                }
            }
        }
    }
}
