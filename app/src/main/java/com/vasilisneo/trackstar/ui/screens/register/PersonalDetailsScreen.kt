package com.vasilisneo.trackstar.ui.screens.register

// Replica of PersonalDetailsView (Trackstar/UI/View/Registration/PersonalDetailsView.swift) on
// iOS — step 2/5 of the registration flow. Visuals (fields, paddings, radii, gender selector)
// match iOS exactly; the date-of-birth and country pickers use Android-native widgets
// (Material3 DatePickerDialog, a searchable ModalBottomSheet) instead of iOS's inline wheel
// picker and pushed list — per the project's "match iOS visuals, use native interaction
// mechanics" principle, not a visual regression.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.AuthCapsuleButton
import com.vasilisneo.trackstar.ui.components.AuthErrorText
import com.vasilisneo.trackstar.ui.components.AuthFieldButton
import com.vasilisneo.trackstar.ui.components.AuthFieldLabel
import com.vasilisneo.trackstar.ui.components.AuthScreenScaffold
import com.vasilisneo.trackstar.ui.components.AuthSelectorButton
import com.vasilisneo.trackstar.ui.components.AuthTextField
import com.vasilisneo.trackstar.ui.theme.TrackstarSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDetailsScreen(
    viewModel: RegisterViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }

    val dobFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }

    AuthScreenScaffold(
        title = "About You",
        subtitle = "Tell us a bit about yourself",
        showBackButton = true,
        onBackClick = onBackClick,
        navBarTrailing = {
            Text(
                text = "2 / 5",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Name
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthTextField(
                    value = viewModel.firstName,
                    onValueChange = viewModel::onFirstNameChange,
                    placeholder = "First name",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f)
                )
                AuthTextField(
                    value = viewModel.lastName,
                    onValueChange = viewModel::onLastNameChange,
                    placeholder = "Last name",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f)
                )
            }

            // Date of birth
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AuthFieldLabel("DATE OF BIRTH")
                AuthFieldButton(
                    text = viewModel.dateOfBirth.format(dobFormatter),
                    onClick = { showDatePicker = true },
                    icon = Icons.Filled.DateRange,
                )
            }

            // Gender
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthFieldLabel("GENDER")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthSelectorButton(
                        text = "Female",
                        selected = viewModel.gender == UserGender.FEMALE,
                        onClick = { viewModel.onGenderChange(UserGender.FEMALE) },
                        modifier = Modifier.weight(1f)
                    )
                    AuthSelectorButton(
                        text = "Male",
                        selected = viewModel.gender == UserGender.MALE,
                        onClick = { viewModel.onGenderChange(UserGender.MALE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Country
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AuthFieldLabel("COUNTRY")
                AuthFieldButton(
                    text = viewModel.country.ifEmpty { "Select your country" },
                    onClick = { showCountryPicker = true },
                    icon = Icons.Filled.Public,
                    isPlaceholder = viewModel.country.isEmpty(),
                )
            }
        }

        viewModel.errorMessage?.let { error -> AuthErrorText(error) }

        AuthCapsuleButton(
            text = "Continue",
            onClick = onContinue,
            enabled = viewModel.isPersonalDetailsValid,
            modifier = Modifier.padding(top = 20.dp)
        )
    }

    if (showDatePicker) {
        val today = LocalDate.now()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.dateOfBirth.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = (today.year - 100)..(today.year - 13),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDateOfBirthChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCountryPicker) {
        CountryPickerSheet(
            selectedCountry = viewModel.country,
            onSelect = { viewModel.onCountryChange(it) },
            onDismiss = { showCountryPicker = false }
        )
    }
}

private data class CountryOption(val name: String, val flag: String)

private fun regionFlagEmoji(isoCode: String): String =
    isoCode.uppercase().map { 0x1F1E6 - 'A'.code + it.code }
        .joinToString("") { String(Character.toChars(it)) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerSheet(
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
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color(0xFF2E80FF)
                            )
                        }
                    }
                }
            }
        }
    }
}
