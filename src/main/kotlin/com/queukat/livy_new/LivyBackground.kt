package com.queukat.livy_new

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

object LivyBackground {

    fun <T> run(
        project: Project?,
        title: String,
        cancellable: Boolean = true,
        action: (ProgressIndicator) -> T,
        onSuccessUi: (T) -> Unit = {},
        onErrorUi: (Throwable) -> Unit = {},
        onFinishedUi: () -> Unit = {}
    ) {
        val resultRef = AtomicReference<Any?>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) {

            override fun run(indicator: ProgressIndicator) {
                resultRef.set(action(indicator))
            }

            override fun onSuccess() {
                @Suppress("UNCHECKED_CAST")
                onSuccessUi(resultRef.get() as T)
            }

            override fun onThrowable(error: Throwable) {
                onErrorUi(error)
            }

            override fun onFinished() {
                onFinishedUi()
            }
        })
    }
}
