package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.UpdateManager

@Composable
fun UpdateDialog(
    config: UpdateManager.UpdateConfig,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    // Parse colors safely with proper fallbacks
    val titleColor = safeParseColor(config.titleColor, Color(0x00, 0xCF, 0xCF))
    val messageColor = safeParseColor(config.textColor, Color.White)
    val backgroundColor = safeParseColor(config.backgroundColor, Color(0x1E, 0x1E, 0x1E))
    val buttonColor = safeParseColor(config.buttonColor, Color(0x7B, 0x1F, 0xA2))

    // If force is true, disable back button press within the container context
    if (config.force) {
        BackHandler(enabled = true) {
            // Do nothing, back press is disabled
        }
    }

    Dialog(
        onDismissRequest = {
            if (!config.force) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !config.force,
            dismissOnClickOutside = !config.force,
            usePlatformDefaultWidth = true
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Update Icon
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Update Icon",
                    tint = titleColor,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(bottom = 16.dp)
                )

                // Title in specified color
                Text(
                    text = config.title,
                    color = titleColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Version display
                Text(
                    text = "V ${config.version}",
                    color = titleColor.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Message in specified color
                Text(
                    text = config.message,
                    color = messageColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 28.dp)
                )

                // Action buttons: user can only click "تحديث | Update" to proceed
                Button(
                    onClick = onUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = config.buttonText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun safeParseColor(colorStr: String, defaultColor: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorStr))
    } catch (e: Exception) {
        defaultColor
    }
}
