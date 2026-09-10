package com.example.arctracker.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "expenses")
public class Expense {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public double amount;
    public String merchant;
    public long dateMillis;
    public String type; // "Debit" or "Credit"
    
    // New fields for Phase 3 (Catch-All and Deduplication)
    public String notificationKey; // Android sbn.key to prevent duplicates
    public boolean isPending; // True if it needs user approval
    public String rawText; // The raw notification text
    public String tag; // e.g. Food, Travel, etc.
    public String note; // User added note
    
    // Source of the transaction
    public String source; // "NOTIFICATION", "SMS_HISTORY", "MANUAL"

    public Expense(double amount, String merchant, long dateMillis, String type, String notificationKey, boolean isPending, String rawText, String tag, String note, String source) {
        this.amount = amount;
        this.merchant = merchant;
        this.dateMillis = dateMillis;
        this.type = type;
        this.notificationKey = notificationKey;
        this.isPending = isPending;
        this.rawText = rawText;
        this.tag = tag;
        this.note = note;
        this.source = source;
    }
}
