package com.aurora.calculatorvault.feature.hiddenapp.domain

import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class LaunchHiddenAppUseCase(
    private val runtime: HiddenAppRuntime,
    private val repository: HiddenAppRepositoryContract,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(packageName: String): AppLaunchResult {
        val result = runtime.launch(packageName)
        if (result == AppLaunchResult.Success) {
            // startActivity 已被系统接受后再记账；即使页面随后台锁定销毁，也完成这次本地更新。
            runCatching {
                withContext(NonCancellable) {
                    repository.markAppOpened(packageName, currentTimeMillis())
                }
            }
        }
        return result
    }
}
