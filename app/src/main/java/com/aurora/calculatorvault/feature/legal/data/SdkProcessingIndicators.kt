package com.aurora.calculatorvault.feature.legal.data

/** Process-local setup flags, which say nothing about what fields a supplier received. */
internal data class SdkProcessingIndicators(
    val umengInitialized: Boolean,
    val buglyInitialized: Boolean,
    val wechatRegistered: Boolean,
)
