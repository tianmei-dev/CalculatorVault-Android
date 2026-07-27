package com.aurora.calculatorvault.core.common

import com.aurora.calculatorvault.BuildConfig

/**
 * Phase 1 临时开发能力集中入口。删除此对象及 CalculatorScreen 对应按钮即可完整移除。
 */
object DeveloperOptions {
    val ENABLE_VAULT_DEBUG_ENTRY: Boolean = BuildConfig.DEBUG
}
