package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.subhashrelangi.arctracker.service.IdentityConfidence
import com.subhashrelangi.arctracker.service.InstrumentType

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
            color = Color(0xFF131620),
            border = BorderStroke(1.dp, Color(0xFF22283A)),
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
                        color = Color.White,
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
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account / Wallet Name input
                Text(
                    text = "Account / Institution Name",
                    color = Color(0xFF94A3B8),
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
                    placeholder = { Text("e.g. Paytm Wallet, Cash, SBI", color = Color(0xFF64748B), fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF242A3A),
                        focusedContainerColor = Color(0xFF161A26),
                        unfocusedContainerColor = Color(0xFF161A26)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Last 4 digits / identifier
                Text(
                    text = "Suffix / Identifier (Optional)",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = accountSuffix,
                    onValueChange = {
                        accountSuffix = it
                        errorMessage = null
                    },
                    placeholder = { Text("e.g. 4821 or CASH", color = Color(0xFF64748B), fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF242A3A),
                        focusedContainerColor = Color(0xFF161A26),
                        unfocusedContainerColor = Color(0xFF161A26)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Type selector
                Text(
                    text = "Instrument Type",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(
                        InstrumentType.BANK_ACCOUNT to "Bank",
                        InstrumentType.CARD to "Card",
                        InstrumentType.UNKNOWN to "Wallet"
                    )
                    types.forEach { (type, label) ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF1E293B) else Color(0xFF161A26),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF3B82F6) else Color(0xFF242A3A)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedType = type }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
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
                        val trimmedName = accountName.trim()
                        if (trimmedName.isBlank()) {
                            errorMessage = "Account name is required"
                            return@Button
                        }
                        val cleanSuffix = accountSuffix.trim().ifBlank {
                            if (selectedType == InstrumentType.UNKNOWN) "WLT" else "0000"
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
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
