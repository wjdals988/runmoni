package com.runmoni.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletMileageMergeTest {
    @Test fun creditSnapshotUpdatesBalanceAfterNewColorsNameAndOwnershipWereSelected() {
        val beforeRead = Profile(name = "이전 이름", mileage = 700,
            avatar = AvatarStyle(gender = AvatarGender.FEMALE, runningKitColors = RunningKitColors(1, 2, 3)),
            ownedItems = AvatarCatalogue.defaultOwned + "top:2")
        val creditSnapshot = beforeRead.copy(mileage = 777)
        // The credit collector is suspended on IO while the user changes the profile.
        val current = beforeRead.copy(name = "현재 이름",
            avatar = beforeRead.avatar.copy(top = 2, runningKitColors = RunningKitColors(4, 2, 5)),
            ownedItems = beforeRead.ownedItems + "wrist:1")

        val merged = current.mergeWalletMileage(beforeRead, creditSnapshot)

        assertEquals(current.copy(mileage = 777), merged)
        assertEquals(current.avatar, merged.avatar)
        assertEquals(current.name, merged.name)
        assertEquals(current.ownedItems, merged.ownedItems)
    }

    @Test fun lateSnapshotDoesNotUndoABalanceAlreadyUpdatedByAnotherCollector() {
        val beforeRead = Profile(name = "러너", mileage = 700)
        val olderSnapshot = beforeRead.copy(mileage = 720)
        val newerSnapshot = beforeRead.copy(mileage = 777)
        val selectedColors = beforeRead.copy(avatar = beforeRead.avatar.copy(runningKitColors = RunningKitColors(3, 4, 5)))
        val withNewCredit = selectedColors.mergeWalletMileage(beforeRead, newerSnapshot)

        assertEquals(withNewCredit, withNewCredit.mergeWalletMileage(beforeRead, olderSnapshot))
        assertEquals(777, withNewCredit.mileage)
        assertEquals(selectedColors.avatar, withNewCredit.avatar)
    }
}
