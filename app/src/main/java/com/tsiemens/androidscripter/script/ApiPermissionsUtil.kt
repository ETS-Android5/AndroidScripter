package com.tsiemens.androidscripter.script

import androidx.fragment.app.FragmentActivity
import com.tsiemens.androidscripter.service.AccessibilitySettingDialogFragment
import com.tsiemens.androidscripter.service.ScriptAccessService
import com.tsiemens.androidscripter.service.isMyServiceRunning
import com.tsiemens.androidscripter.tryGuaranteeUsageStatsAccess

/**
 * @return: true if permissions were already granted.
 *          false if the user is going to need to take some action which is not guaranteed
 *              complete on return
 */
fun tryGetApiPermissions(ctx: FragmentActivity): Boolean {
    var alreadyGranted = tryGuaranteeUsageStatsAccess(ctx)

    if (!isMyServiceRunning(ctx, ScriptAccessService::class.java)) {
        AccessibilitySettingDialogFragment().show(
            ctx.supportFragmentManager, "")
        alreadyGranted = false
    }
    return alreadyGranted
}