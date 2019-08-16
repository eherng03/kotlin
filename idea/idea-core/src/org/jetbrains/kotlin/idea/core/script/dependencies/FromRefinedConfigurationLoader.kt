/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.ScriptClassRootsManager
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManagerImpl
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesManager
import org.jetbrains.kotlin.idea.core.script.debug
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.LegacyResolverWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.script.experimental.dependencies.AsyncDependenciesResolver

// TODO: rename and provide alias for compatibility - this is not only about dependencies anymore
class FromRefinedConfigurationLoader internal constructor(
    private val project: Project,
    private val manager: ScriptConfigurationManagerImpl,
    private val rootsManager: ScriptClassRootsManager
) : ScriptDependenciesLoader {
    private val backgroundLoader = BackgroundLoader()

    override fun isApplicable(file: KtFile, scriptDefinition: ScriptDefinition) = true

    private fun isAsyncDependencyResolver(scriptDef: ScriptDefinition): Boolean =
        scriptDef.asLegacyOrNull<KotlinScriptDefinition>()?.dependencyResolver?.let {
            it is AsyncDependenciesResolver || it is LegacyResolverWrapper
        } ?: false

    override fun loadDependencies(
        file: KtFile,
        scriptDefinition: ScriptDefinition
    ) {
        if (isAsyncDependencyResolver(scriptDefinition)) {
            backgroundLoader.scheduleAsync(file)
        } else {
            runDependenciesUpdate(file, scriptDefinition)
        }
    }

    // internal for tests
    internal fun runDependenciesUpdate(file: KtFile, scriptDefinition: ScriptDefinition? = file.findScriptDefinition()) {
        if (scriptDefinition == null) return

        debug(file) { "start dependencies loading" }

        val result = refineScriptCompilationConfiguration(KtFileScriptSource(file), scriptDefinition, file.project)

        debug(file) { "finish dependencies loading" }

        manager.saveConfiguration(
            file.originalFile.virtualFile,
            result
        )
    }

    private inner class BackgroundLoader {
        private var task: Task? = null

        @Synchronized
        fun scheduleAsync(file: KtFile) {
            if (task == null) {
                startBatch(file)
            } else {
                task!!.addTask(file)
            }
        }

        @Synchronized
        private fun startBatch(file: KtFile) {
            rootsManager.startTransaction()
            task = Task()
            task!!.addTask(file)
            task!!.start()
        }

        @Synchronized
        private fun endBatch() {
            task = null
            rootsManager.commit()
        }

        private inner class Task {
            private val sequenceOfFiles: ConcurrentLinkedQueue<KtFile> = ConcurrentLinkedQueue()
            private var forceStop: Boolean = false
            private var startedSilently: Boolean = false

            fun start() {
                if (KotlinScriptingSettings.getInstance(project).isAutoReloadEnabled) {
                    startWithProgress()
                } else {
                    startSilently()
                }
            }

            private fun restartWithProgress() {
                forceStop = true
                startWithProgress()
                forceStop = false
            }

            private fun startSilently() {
                startedSilently = true
                BackgroundTaskUtil.executeOnPooledThread(project, Runnable {
                    loadDependencies(EmptyProgressIndicator())
                })
            }

            private fun startWithProgress() {
                startedSilently = false
                object : com.intellij.openapi.progress.Task.Backgroundable(project, "Kotlin: Loading script dependencies...", true) {
                    override fun run(indicator: ProgressIndicator) {
                        loadDependencies(indicator)
                    }
                }.queue()
            }

            fun addTask(file: KtFile) {
                if (file in sequenceOfFiles) return

                debug(file) { "added to update queue" }

                sequenceOfFiles.add(file)

                // If the queue is longer than 3, show progress and cancel button
                if (sequenceOfFiles.size > 3 && startedSilently) {
                    restartWithProgress()
                }
            }

            private fun loadDependencies(indicator: ProgressIndicator?) {
                while (true) {
                    if (forceStop) return
                    if (indicator?.isCanceled == true || sequenceOfFiles.isEmpty()) {
                        endBatch()
                        return
                    }

                    runDependenciesUpdate(sequenceOfFiles.poll())
                }
            }
        }
    }
}

