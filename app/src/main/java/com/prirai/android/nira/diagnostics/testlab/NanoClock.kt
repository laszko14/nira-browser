package com.prirai.android.nira.diagnostics.testlab

/** Primitive monotonic clock on the frame hot path. */
fun interface NanoClock { fun nowNanos(): Long }
