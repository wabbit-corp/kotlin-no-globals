// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.noglobals.idea

import one.wabbit.ijplugin.common.ConfiguredRefreshIdeSupportAction

class RefreshNoGlobalsIdeSupportAction : ConfiguredRefreshIdeSupportAction(
    "Refresh No-Globals IDE Support",
    "Re-scan Kotlin compiler arguments and enable no-globals IDE support for this project session",
    NoGlobalsIdeSupportCoordinator,
) {
}
