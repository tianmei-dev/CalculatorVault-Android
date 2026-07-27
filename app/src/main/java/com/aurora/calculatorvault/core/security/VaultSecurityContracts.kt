package com.aurora.calculatorvault.core.security

/**
 * 后续由 Activity/Window 层实现，私密页面可通过它统一启停 FLAG_SECURE。
 */
interface SecureScreenController {
    fun setSecureScreenEnabled(enabled: Boolean)
}

/**
 * 后续由应用生命周期层调用，集中触发后台锁定；不在 Composable 中持久化密码。
 */
interface AppLockController {
    fun onAppEnteredBackground()
}

/**
 * 后续密码模块仅接收临时字符数据并返回哈希结果，禁止日志记录或明文持久化。
 */
interface PasswordHasher {
    suspend fun hash(password: CharArray): ByteArray
}
