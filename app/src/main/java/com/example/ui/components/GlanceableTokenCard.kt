package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.QueueToken
import com.example.domain.model.TokenStatus
import com.example.ui.theme.*

@Composable
fun GlanceableTokenCard(
    token: QueueToken,
    peopleAhead: Int,
    nowServingLabel: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("glanceable_token_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.your_token),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = token.label,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("patient_token_number")
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Status chip badge
            StatusBadge(status = token.status)

            Spacer(modifier = Modifier.height(20.dp))

            // Glanceable metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricColumn(
                    title = stringResource(R.string.now_serving),
                    value = nowServingLabel ?: "—",
                    tag = "now_serving_label"
                )
                MetricColumn(
                    title = stringResource(R.string.people_ahead),
                    value = if (token.status == TokenStatus.WAITING) peopleAhead.toString() else "0",
                    tag = "people_ahead_count"
                )
                MetricColumn(
                    title = stringResource(R.string.estimated_wait),
                    value = when {
                        token.status == TokenStatus.CALLED || token.status == TokenStatus.IN_CONSULTATION -> "Now"
                        token.estimatedWaitMinutes != null && token.estimatedWaitMinutes > 0 -> "${token.estimatedWaitMinutes} m"
                        else -> "—"
                    },
                    tag = "estimated_wait_time"
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: TokenStatus) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val (bgColor, textColor, label) = when (status) {
        TokenStatus.PENDING_VERIFICATION -> if (isDark) {
            Triple(Color(0xFF382508), Color(0xFFFFD54F), "Pending Verification")
        } else {
            Triple(AmberWarningContainer, AmberWarning, "Pending Verification")
        }
        TokenStatus.REJECTED -> if (isDark) {
            Triple(Color(0xFF3E1416), Color(0xFFFF8A80), "Prescription Rejected")
        } else {
            Triple(RedErrorContainer, RedError, "Prescription Rejected")
        }
        TokenStatus.WAITING -> if (isDark) {
            Triple(Color(0xFF003830), Color(0xFF80CBC4), "Waiting in Queue")
        } else {
            Triple(Color(0xFFE0F2F1), Teal40, "Waiting in Queue")
        }
        TokenStatus.CALLED -> if (isDark) {
            Triple(Color(0xFF4A2800), Color(0xFFFFE082), "Called — Please Enter")
        } else {
            Triple(Color(0xFFFFE082), Color(0xFFE65100), "Called — Please Enter")
        }
        TokenStatus.IN_CONSULTATION -> if (isDark) {
            Triple(Color(0xFF0F351C), Color(0xFFA5D6A7), "In Consultation")
        } else {
            Triple(MintGreenContainer, MintGreen, "In Consultation")
        }
        TokenStatus.COMPLETED -> if (isDark) {
            Triple(Color(0xFF2E1C4E), Color(0xFFD1C4E9), "Completed")
        } else {
            Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), "Completed")
        }
        TokenStatus.SKIPPED -> if (isDark) {
            Triple(Color(0xFF382508), Color(0xFFFFD54F), "Skipped")
        } else {
            Triple(AmberWarningContainer, AmberWarning, "Skipped")
        }
        TokenStatus.NO_SHOW -> if (isDark) {
            Triple(Color(0xFF3E1416), Color(0xFFFF8A80), "No Show")
        } else {
            Triple(RedErrorContainer, RedError, "No Show")
        }
        TokenStatus.CANCELLED -> if (isDark) {
            Triple(Color(0xFF2A2A2A), Color(0xFFBDBDBD), "Cancelled")
        } else {
            Triple(Color(0xFFEEEEEE), Color(0xFF616161), "Cancelled")
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("status_badge")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
private fun MetricColumn(title: String, value: String, tag: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.testTag(tag)
        )
    }
}
