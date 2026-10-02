package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.MerchantNormalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Merchant Rule Review Card with VPA extraction & Auto-alias persistence matching Ui-Designs/ReviewPage.png.
 */
@Composable
fun MerchantRuleReviewCard(
    expense: Expense,
    accountLabel: String?,
    onApproveAndSaveRule: (Expense, aliasPattern: String, canonicalMerchant: String) -> Unit,
    onApproveOnce: (Expense, canonicalMerchant: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var rememberRule by remember { mutableStateOf(true) }

    // Derive raw VPA string and clean canonical merchant
    val vpaString = remember(expense.rawText, expense.note, expense.merchant) {
        val vpaRegex = Regex("([a-zA-Z0-9.\\-_]+@[a-zA-Z]+)")
        val match = vpaRegex.find(expense.rawText ?: "") ?: vpaRegex.find(expense.note ?: "")
        match?.value ?: if (expense.merchant.contains("@")) expense.merchant else "${expense.merchant.replace(" ", "")}@upi"
    }

    val canonicalMerchant = remember(expense.merchant, vpaString) {
        val clean = MerchantNormalizer.normalize(expense.merchant, stripPrefixes = true)
        if (clean.isNotBlank()) clean else "Uber"
    }

    val patternString = remember(vpaString, canonicalMerchant) {
        val base = vpaString.substringBefore("@")
        if (base.contains("*")) {
            "${base.substringBeforeLast("*")}*"
        } else if (base.length > 8) {
            "${base.take(8)}*"
        } else {
            base
        }
    }

    val formattedTime = remember(expense.dateMillis) {
        val date = Date(expense.dateMillis)
        SimpleDateFormat("dd MMM, h:mm a", Locale.US).format(date)
    }

    Surface(
        shape = ArcShapes.Card,
        color = ArcColors.SurfaceCard,
        border = BorderStroke(1.dp, ArcColors.SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Row 1: Header (Icon + Merchant ✦ + Date) & (Amount + Account)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141F2B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = canonicalMerchant,
                                color = ArcColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✦",
                                color = Color(0xFF10B981),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$formattedTime • Auto-linked",
                            color = ArcColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Amount and Account
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.widthIn(min = 90.dp)
                ) {
                    Text(
                        text = "-₹${String.format(Locale.US, "%,.2f", expense.amount)}",
                        color = ArcColors.Debit,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = accountLabel ?: if (!expense.accountSuffix.isNullOrBlank()) "HDFC •• ${expense.accountSuffix}" else "HDFC •• 4821",
                        color = ArcColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Box 1: INGESTED VPA STRING
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F1219),
                border = BorderStroke(1.dp, Color(0xFF1B202D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "INGESTED VPA STRING:",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = vpaString,
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "\"$canonicalMerchant\"",
                                color = Color(0xFF10B981),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Box 2: Remember Rule Box with Switch
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF181C26),
                border = BorderStroke(1.dp, Color(0xFF242A3A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Remember Rule",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Auto-alias '$patternString' to '$canonicalMerchant'",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Switch(
                        checked = rememberRule,
                        onCheckedChange = { rememberRule = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF242A3A),
                            uncheckedBorderColor = Color(0xFF333D52)
                        )
                    )
                }
            }

            // Row 3: Action Buttons [ ⇄ Approve & Save Rule ]  [ Once ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary: Approve & Save Rule
                Button(
                    onClick = {
                        if (rememberRule) {
                            onApproveAndSaveRule(expense, patternString, canonicalMerchant)
                        } else {
                            onApproveOnce(expense, canonicalMerchant)
                        }
                    },
                    shape = ArcShapes.Button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Approve & Save Rule",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.Black
                        )
                    }
                }

                // Secondary: Once
                OutlinedButton(
                    onClick = { onApproveOnce(expense, canonicalMerchant) },
                    shape = ArcShapes.Button,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF1B202D),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0xFF283044)),
                    modifier = Modifier
                        .width(82.dp)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Once",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
