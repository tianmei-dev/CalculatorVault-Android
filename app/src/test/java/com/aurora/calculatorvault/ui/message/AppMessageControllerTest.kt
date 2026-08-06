package com.aurora.calculatorvault.ui.message

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppMessageControllerTest {
    @Test
    fun `show helpers emit messages in fifo order`() = runTest {
        val controller = AppMessageController()
        val received = mutableListOf<AppMessage>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.messages.take(4).toList(received)
        }

        controller.showSuccess("创建成功")
        controller.showInfo("普通提示")
        controller.showWarning("快捷方式已失效")
        controller.showError("创建失败")
        job.join()

        assertEquals(
            listOf(
                AppMessage("创建成功", AppMessageType.Success),
                AppMessage("普通提示", AppMessageType.Info),
                AppMessage("快捷方式已失效", AppMessageType.Warning),
                AppMessage("创建失败", AppMessageType.Error),
            ),
            received,
        )
    }
}
