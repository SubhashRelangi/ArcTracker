package com.example.arctracker.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import kotlinx.coroutines.flow.Flow;
import java.util.List;

@Dao
public interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC")
    Flow<List<Expense>> getAllExpenses();

    @Query("SELECT * FROM expenses WHERE notificationKey = :key ORDER BY dateMillis DESC LIMIT 1")
    Expense getExpenseByKey(String key);

    @Query("SELECT * FROM expenses WHERE dateMillis >= :timeWindow AND isPending = 1 ORDER BY dateMillis DESC LIMIT 1")
    Expense getRecentPendingExpense(long timeWindow);

    @Insert
    void insertExpense(Expense expense);

    @Update
    void updateExpense(Expense expense);

    @androidx.room.Delete
    void deleteExpense(Expense expense);
}
