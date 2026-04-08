// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.noglobals.idea

import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener

internal object NoGlobalsIdeSupportAutoRescan {
    fun install(project: Project, requestRescan: () -> Unit) {
        val projectConnection = project.messageBus.connect(project)
        val applicationConnection = ApplicationManager.getApplication().messageBus.connect(project)
        install(
            project = project,
            requestRescan = requestRescan,
            subscribeImportFinished = { callback ->
                projectConnection.subscribe(
                    ProjectDataImportListener.TOPIC,
                    object : ProjectDataImportListener {
                        override fun onImportFinished(projectPath: String?) {
                            callback()
                        }
                    },
                )
            },
            subscribeRootsChanged = { callback ->
                projectConnection.subscribe(
                    ModuleRootListener.TOPIC,
                    object : ModuleRootListener {
                        override fun rootsChanged(event: ModuleRootEvent) {
                            callback()
                        }
                    },
                )
            },
            subscribeProjectTrusted = { callback ->
                applicationConnection.subscribe(
                    TrustedProjectsListener.TOPIC,
                    object : TrustedProjectsListener {
                        override fun onProjectTrusted(project: Project) {
                            callback(project)
                        }
                    },
                )
            },
        )
    }

    internal fun install(
        project: Project,
        requestRescan: () -> Unit,
        subscribeImportFinished: ((() -> Unit)) -> Unit,
        subscribeRootsChanged: ((() -> Unit)) -> Unit,
        subscribeProjectTrusted: (((Project) -> Unit)) -> Unit,
    ) {
        subscribeImportFinished(requestRescan)
        subscribeRootsChanged(requestRescan)
        subscribeProjectTrusted { trustedProject ->
            if (trustedProject === project) {
                requestRescan()
            }
        }
    }
}
