package com.subhashrelangi.arctracker.service

import com.subhashrelangi.arctracker.engine.category.*

/**
 * Backward-compatibility shims mapping legacy service package references
 * to the segregated [com.subhashrelangi.arctracker.engine.category] domain module.
 */
typealias CategoryInferenceEngine = com.subhashrelangi.arctracker.engine.category.CategoryInferenceEngine
typealias CategorySource = com.subhashrelangi.arctracker.engine.category.CategorySource
typealias CategoryInferenceStatus = com.subhashrelangi.arctracker.engine.category.CategoryInferenceStatus
typealias CategoryInferenceConfidence = com.subhashrelangi.arctracker.engine.category.CategoryInferenceConfidence
typealias EvidenceType = com.subhashrelangi.arctracker.engine.category.EvidenceType
typealias CategoryInferenceEvidence = com.subhashrelangi.arctracker.engine.category.CategoryInferenceEvidence
typealias CategoryInferenceInput = com.subhashrelangi.arctracker.engine.category.CategoryInferenceInput
typealias CategoryInferenceResult = com.subhashrelangi.arctracker.engine.category.CategoryInferenceResult
typealias CategoryMatchingType = com.subhashrelangi.arctracker.engine.category.CategoryMatchingType
typealias RulePriority = com.subhashrelangi.arctracker.engine.category.RulePriority
typealias CategoryInferenceRule = com.subhashrelangi.arctracker.engine.category.CategoryInferenceRule
typealias CategoryManager = com.subhashrelangi.arctracker.engine.category.CategoryManager
typealias CategoryRuleManager = com.subhashrelangi.arctracker.engine.category.CategoryRuleManager
typealias BuiltInInferenceRules = com.subhashrelangi.arctracker.engine.category.BuiltInInferenceRules
typealias MerchantAliasManager = com.subhashrelangi.arctracker.engine.category.MerchantAliasManager
typealias MerchantNormalizer = com.subhashrelangi.arctracker.engine.category.MerchantNormalizer
typealias PreviewChangeStatus = com.subhashrelangi.arctracker.engine.category.PreviewChangeStatus
typealias HistoricalReassignmentPreviewItem = com.subhashrelangi.arctracker.engine.category.HistoricalReassignmentPreviewItem
typealias HistoricalRulePreviewResult = com.subhashrelangi.arctracker.engine.category.HistoricalRulePreviewResult
typealias BulkReassignmentResult = com.subhashrelangi.arctracker.engine.category.BulkReassignmentResult
typealias HistoricalRuleApplier = com.subhashrelangi.arctracker.engine.category.HistoricalRuleApplier
