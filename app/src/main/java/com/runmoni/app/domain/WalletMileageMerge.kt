package com.runmoni.app.domain

/** An IO wallet read owns the balance field, not a newer name, outfit or inventory.
 * A balance changed since the read began takes precedence over this older snapshot.
 */
fun Profile.mergeWalletMileage(before: Profile, snapshot: Profile): Profile =
    if (mileage == before.mileage) copy(mileage = snapshot.mileage) else this
