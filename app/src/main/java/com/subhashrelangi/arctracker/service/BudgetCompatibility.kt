package com.subhashrelangi.arctracker.service

/**
 * Backward-compatibility shims mapping legacy service package references
 * to the segregated [com.subhashrelangi.arctracker.engine.budget] domain module.
 */
typealias BudgetManager = com.subhashrelangi.arctracker.engine.budget.BudgetManager
typealias BudgetAlertNotifier = com.subhashrelangi.arctracker.engine.budget.BudgetAlertNotifier
typealias AndroidBudgetAlertNotifier = com.subhashrelangi.arctracker.engine.budget.AndroidBudgetAlertNotifier
