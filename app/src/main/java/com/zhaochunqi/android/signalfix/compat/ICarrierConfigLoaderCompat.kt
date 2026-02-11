package com.zhaochunqi.android.signalfix.compat

import android.os.IBinder
import android.os.Parcel
import android.os.PersistableBundle

object ICarrierConfigLoaderCompat {
    private const val DESCRIPTOR = "com.android.internal.telephony.ICarrierConfigLoader"
    // Method order:
    // 1. getConfigForSubId
    // 2. getConfigForSubIdWithFeature
    // 3. overrideConfig
    private const val TRANSACTION_overrideConfig = IBinder.FIRST_CALL_TRANSACTION + 2

    fun overrideConfig(binder: IBinder, subId: Int, overrides: PersistableBundle?) {
        val _data = Parcel.obtain()
        val _reply = Parcel.obtain()
        try {
            _data.writeInterfaceToken(DESCRIPTOR)
            _data.writeInt(subId)
            if (overrides != null) {
                _data.writeInt(1)
                overrides.writeToParcel(_data, 0)
            } else {
                _data.writeInt(0)
            }
            // boolean persistent = true (always true for our use case to match manual override behavior)
            _data.writeBoolean(true)
            
            val status = binder.transact(TRANSACTION_overrideConfig, _data, _reply, 0)
            _reply.readException()
        } finally {
            _reply.recycle()
            _data.recycle()
        }
    }
}
