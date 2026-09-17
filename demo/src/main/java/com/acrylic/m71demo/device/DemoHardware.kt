package com.acrylic.m71demo.device

import javax.inject.Inject

class DemoHardware @Inject constructor(val sound: SoundFeedback, val telemetry: Telemetry, val fault: FaultGate)
