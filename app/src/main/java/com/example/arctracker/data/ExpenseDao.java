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

    @Query("SELECT * FROM expenses WHERE amount = :amount AND type = :type AND dateMillis >= :startTime AND dateMillis <= :endTime")
    List<Expense> findPotentialDuplicates(double amount, String type, long startTime, long endTime);

    @Insert
    void insertExpense(Expense expense);

    @Update
    void updateExpense(Expense expense);

    @Query("DELETE FROM expenses")
    void deleteAllExpenses();
    @androidx.room.Delete
    void deleteExpense(Expense expense);
}
