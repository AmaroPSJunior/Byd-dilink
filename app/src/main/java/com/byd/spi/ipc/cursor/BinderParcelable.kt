package com.byd.spi.ipc.cursor

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

/** Mirrors the provider's nested BinderCursor.BinderParcelable binary name. */
object BinderCursor {
    class BinderParcelable private constructor(private val binder: IBinder?) : Parcelable {
        constructor(source: Parcel) : this(source.readStrongBinder())

        fun getBinder(): IBinder? = binder

        override fun describeContents(): Int = 0

        override fun writeToParcel(destination: Parcel, flags: Int) {
            destination.writeStrongBinder(binder)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<BinderParcelable> = object : Parcelable.Creator<BinderParcelable> {
                override fun createFromParcel(source: Parcel): BinderParcelable = BinderParcelable(source)
                override fun newArray(size: Int): Array<BinderParcelable?> = arrayOfNulls(size)
            }
        }
    }
}
