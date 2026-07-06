package com.vasilisneo.trackstar.ui.components

// Shared building blocks for the auth flow (Login, and later Register/ForgotPassword),
// replicating Trackstar/UI/View/Registration/RegistrationHelpers.swift on iOS exactly —
// same heights, corner radii, paddings, opacities, and font sizes. Not a generic design
// system component library; these are auth-screen-specific on purpose, same as iOS.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground

/** Flat #0D0D17 fill + a blurred accent-color glow — matches authBackground(offsetY:) on iOS. */
@Composable
fun AuthBackground(
    modifier: Modifier = Modifier,
    glowOffsetX: Dp = 60.dp,
    glowOffsetY: Dp = (-160).dp,
) {
    Box(modifier = modifier.fillMaxSize().background(TrackstarBackground)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = glowOffsetX, y = glowOffsetY)
                .size(380.dp)
                // Unbounded: without this, blur() clips to its own rectangular bounds,
                // turning the soft circular glow into a hard-edged square patch.
                .blur(radius = 80.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(TrackstarAccent.copy(alpha = 0.13f), CircleShape)
        )
    }
}

/** Matches registrationWordmark on iOS: fixed, non-interactive "Trackstar" wordmark
 *  pinned near the bottom of the screen, behind the scrollable content. */
@Composable
fun AuthWordmark(modifier: Modifier = Modifier) {
    Text(
        text = "Trackstar",
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF17171F),
        modifier = modifier.fillMaxWidth().padding(bottom = 20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/** Matches glassCircle() on iOS: 44dp translucent circle, used for the nav bar back button. */
@Composable
fun GlassCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.ChevronLeft,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

private val FieldShape = RoundedCornerShape(20.dp)
private val FieldBackground = Color.White.copy(alpha = 0.08f)
private val PlaceholderColor = Color.White.copy(alpha = 0.55f)

/** Matches authTextField(...) on iOS: 52dp tall, 8%-white fill, 20dp corners, 16dp horizontal padding. */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(FieldBackground, FieldShape),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = PlaceholderColor, fontSize = 16.sp)
                    innerTextField()
                }
            }
        )
    }
}

/** Matches secureAuthField(...) on iOS: same 52dp/20dp box, leading-only padding, trailing eye toggle in a 40dp box. */
@Composable
fun AuthSecureField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(FieldBackground, FieldShape)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) Text(placeholder, color = PlaceholderColor, fontSize = 16.sp)
                            innerTextField()
                        }
                    }
                )
            }
            IconButton(
                onClick = onToggleShowPassword,
                modifier = Modifier.width(40.dp).fillMaxHeight()
            ) {
                Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** Matches capsuleButton(...) / LoginView's primary button: 56dp tall pill, solid white, black 17sp semibold label. */
@Composable
fun AuthCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val isEnabled = enabled && !isLoading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (isEnabled) 1f else 0.35f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(text, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Matches orDivider() on iOS: two 1dp 10%-white hairlines around a 13sp 30%-white "or". */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = Color.White.copy(alpha = 0.1f))
        Text("or", fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = Color.White.copy(alpha = 0.1f))
    }
}

/** Matches LoginView's Google button: 56dp pill, 8%-white fill, bold blue "G" + semibold white label. */
@Composable
fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isEnabled = enabled && !isLoading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isLoading) {
                Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    Text("G", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4085F5))
                }
                Box(modifier = Modifier.width(12.dp))
            }
            Text(
                text = if (isLoading) "Signing in…" else "Continue with Google",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
