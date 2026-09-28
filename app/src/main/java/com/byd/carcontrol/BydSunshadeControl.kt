package com.byd.carcontrol

import android.content.Context

/** Uses the same BYDAutoBodyworkDevice API and percentage convention as OEM CarSettings. */
object BydSunshadeControl {
    private const val DEVICE_CLASS = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
    private const val SUNSHADE_AREA = 6
    private const val OPEN_PERCENT = 100

    data class Result(val accepted: Boolean, val percent: Int?, val detail: String)

    fun open(context: Context): Result {
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext) ?: error("BYDAutoBodyworkDevice.getInstance retornou null")

        // OEM CarSettings disables opening when window operation is disallowed or
        // the shade has not completed initialization. Both getters return state 1
        // when true, as used by its SunRoofPresenter.
        val permit = (deviceClass.getMethod("getWindowPermitState").invoke(instance) as Number).toInt()
        val initialized = (deviceClass.getMethod("getWindoblindInitState").invoke(instance) as Number).toInt()
        if (permit != 1 || initialized != 1) {
            return Result(false, readPercent(deviceClass, instance), "A API OEM bloqueia abertura: permissão=$permit, inicialização=$initialized.")
        }

        val method = deviceClass.getMethod("setSunshadeState", Int::class.javaPrimitiveType)
        val result = method.invoke(instance, OPEN_PERCENT)
        val percent = readPercent(deviceClass, instance)
        // This OEM setter is void; distinguish successful invocation from a verified position.
        return Result(true, percent, "setSunshadeState(100) executado; retorno=${result ?: "void"}; posição=${percent ?: "indisponível"}%.")
    }

    private fun readPercent(deviceClass: Class<*>, instance: Any): Int? = runCatching {
        (deviceClass.getMethod("getWindowOpenPercent", Int::class.javaPrimitiveType)
            .invoke(instance, SUNSHADE_AREA) as Number).toInt()
    }.getOrNull()
}
