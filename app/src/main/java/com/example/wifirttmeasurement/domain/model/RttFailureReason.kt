package com.example.wifirttmeasurement.domain.model

enum class RttFailureReason {
    None,
    PermissionDenied,
    RttUnsupported,
    ResponderUnavailable,
    Timeout,
    InvalidSession,
    ApiFailure,
    Unknown,
}
