// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.noglobals.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class NoGlobalsIdeSupportActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        fun requestRescan() {
            NoGlobalsIdeSupportCoordinator.enableIfNeeded(
                project = project,
                userInitiated = false,
            )
        }

        requestRescan()
        NoGlobalsIdeSupportAutoRescan.install(project, ::requestRescan)
    }
}
