package com.example.personalledger.domain

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun currentEpochMilliseconds(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
