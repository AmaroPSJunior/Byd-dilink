package com.byd.carcontrol

import android.content.Context
import android.net.Uri
import android.os.IBinder
import android.os.Parcel
import com.byd.spi.ipc.cursor.BinderCursor.BinderParcelable

/** Read-only client for the AIDL getter exposed by BYD AppServer. */
object BydDrivingStateReader {
    private const val SERVICE_CLASS = "com.byd.car.driving.IDrivingStateService"
    private const val DESCRIPTOR = SERVICE_CLASS
    private const val GET_DRIVING_STATE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION
    private val providerUri = Uri.parse(
        "content://com.byd.car.server.provider.CarServiceProvider/sync_binder"
    )

    /**
     * Reads the raw state integer through AppServer's exported SPI provider.
     * The enum mapping is deliberately not guessed; callers must display it as raw.
     */
    fun readRawState(context: Context): Int {
        val provider = context.packageManager.resolveContentProvider(
            providerUri.authority!!,
            0
        ) ?: error("Provider BYD CarServiceProvider não encontrado")
        check(provider.packageName == "com.byd.appserver" && provider.exported) {
            "Provider BYD encontrado, mas não está exportado como esperado"
        }
        check(provider.readPermission.isNullOrBlank()) {
            "Provider BYD exige permissão de leitura: ${provider.readPermission}"
        }

        val cursor = context.contentResolver.query(
            providerUri,
            null,
            null,
            arrayOf(SERVICE_CLASS),
            null
        ) ?: error("Provider BYD não retornou cursor")

        val binder = cursor.use {
            val extras = it.extras
            extras.classLoader = BinderParcelable::class.java.classLoader
            @Suppress("DEPRECATION")
            val wrapped = extras.getParcelable("binder") as? BinderParcelable
            wrapped?.getBinder()
        } ?: error("Provider respondeu sem o Binder de $SERVICE_CLASS")

        check(binder.isBinderAlive) { "Binder de estado de condução não está ativo" }
        check(binder.interfaceDescriptor == DESCRIPTOR) {
            "Descriptor Binder inesperado: ${binder.interfaceDescriptor}"
        }

        val request = Parcel.obtain()
        val response = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            check(binder.transact(GET_DRIVING_STATE_TRANSACTION, request, response, 0)) {
                "O Binder não aceitou a chamada de leitura getDrivingState()"
            }
            response.readException()
            return response.readInt()
        } finally {
            response.recycle()
            request.recycle()
        }
    }
}
