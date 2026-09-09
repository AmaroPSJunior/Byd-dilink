package com.byd.carcontrol.discovery

enum class DiscoveryStatus {
    DISCOVERED,
    TESTED,
    VALIDATED,
    FAILED,
    DENIED,
    NOT_AVAILABLE,
    UNSUPPORTED,
    UNKNOWN
}

enum class ApiSafety {
    READ_ONLY,
    CONTROL,
    UNKNOWN
}
