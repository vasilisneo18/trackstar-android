package com.vasilisneo.trackstar.ui.components

// An OutlinedTextField that puts the cursor after the text when tapped, wherever the tap lands —
// for numbers like a weight, where you add to or delete from the end. iOS: EndCursorTextField.

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun EndCursorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    textStyle: TextStyle,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    colors: TextFieldColors,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    // The tap that focuses the field also places the cursor; the first placement after focusing
    // goes to the end instead.
    var justFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = field,
        onValueChange = { new ->
            field = if (justFocused && new.text == field.text) {
                new.copy(selection = TextRange(new.text.length))
            } else new
            justFocused = false
            if (new.text != value) onValueChange(new.text)
        },
        placeholder = placeholder,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        colors = colors,
        modifier = modifier.onFocusChanged { state ->
            if (state.isFocused && !justFocused) {
                justFocused = true
                field = field.copy(selection = TextRange(field.text.length))
            } else if (!state.isFocused) justFocused = false
        },
    )
}
