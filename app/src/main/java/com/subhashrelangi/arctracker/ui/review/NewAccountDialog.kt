package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subhashrelangi.arctracker.data.AccountSource
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.engine.account.IdentityConfidence
import com.subhashrelangi.arctracker.engine.account.InstrumentType

/**
 * Modal dialog for quickly registering a new financial account or wallet.
 */
@Composable
fun NewAccountDialog(
    onAccountCreated: (KnownFinancialAccount) -> Unit,
    onDismissRequest: () -> Unit
) {
    var accountName by remember { mutableStateOf("") }
    var accountSuffix by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<InstrumentType>(InstrumentType.BANK_ACCOUNT) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ArcColors.SurfaceElevated,
            border = BorderStroke(1.dp, ArcColors.Border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Account / Wallet",
                        color = ArcColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ArcColors.TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account / Wallet Name input
                Text(
                    text = "Account / Institution Name",
                    color = ArcColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = accountName,
                    onValueChange = {
                        accountName = it
                        errorMessage = null
                    },
                    placeholder = { Text("e.g. Paytm Wallet, Cash, SBI", color = ArcColors.TextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ArcColors.TextPrimary,
                        unfocusedTextColor = ArcColors.TextPrimary,
                        focusedBorderColor = ArcColors.Primary,
                        unfocusedBorderColor = ArcColors.Border,
                        focusedContainerColor = ArcColors.SurfaceHighlight,
                        unfocusedContainerColor = ArcColors.SurfaceHighlight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Type selector
                Text(
                    text = "Instrument Type",
                    color = ArcColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val types = listOf(
                        InstrumentType.BANK_ACCOUNT to "Bank",
                        InstrumentType.CARD to "Card",
                        InstrumentType.WALLET to "Wallet",
                        InstrumentType.CASH to "Cash"
                    )
                    types.forEach { (type, label) ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) ArcColors.PrimaryLight else ArcColors.SurfaceHighlight,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ArcColors.PrimaryBorder else ArcColors.Border
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedType = type }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) ArcColors.Primary else ArcColors.TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                val isSuffixNeeded = selectedType == InstrumentType.BANK_ACCOUNT || selectedType == InstrumentType.CARD

                if (isSuffixNeeded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Suffix / Last 4 Digits",
                        color = ArcColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = accountSuffix,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                accountSuffix = it
                                errorMessage = null
                            }
                        },
                        placeholder = { Text("e.g. 4821", color = ArcColors.TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ArcColors.TextPrimary,
                            unfocusedTextColor = ArcColors.TextPrimary,
                            focusedBorderColor = ArcColors.Primary,
                            unfocusedBorderColor = ArcColors.Border,
                            focusedContainerColor = ArcColors.SurfaceHighlight,
                            unfocusedContainerColor = ArcColors.SurfaceHighlight
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = ArcColors.Danger,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save button
                Button(
                    onClick = {
                        val trimmedName = accountName.trim().ifBlank {
                            if (selectedType == InstrumentType.CASH) "Liquid Cash" else ""
                        }
                        if (trimmedName.isBlank() && selectedType != InstrumentType.CASH) {
                            errorMessage = "Account name is required"
                            return@Button
                        }
                        val cleanSuffix = if (isSuffixNeeded) accountSuffix.trim() else ""
                        if (isSuffixNeeded && cleanSuffix.length < 2) {
                            errorMessage = "Please enter last 4 digits suffix"
                            return@Button
                        }
                        val newAccount = KnownFinancialAccount.create(
                            institutionId = trimmedName.lowercase().replace(" ", "_"),
                            institutionName = trimmedName,
                            accountSuffix = cleanSuffix,
                            instrumentType = selectedType,
                            confidence = IdentityConfidence.HIGH,
                            source = AccountSource.USER_CONFIRMED
                        )
                        onAccountCreated(newAccount)
                        onDismissRequest()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArcColors.Primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "Save & Link Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
