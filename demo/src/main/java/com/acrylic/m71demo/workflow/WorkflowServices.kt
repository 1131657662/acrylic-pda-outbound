package com.acrylic.m71demo.workflow

import javax.inject.Inject

class WorkflowServices @Inject constructor(val capture: CaptureService,
    val confirmation: ConfirmationService, val correction: CorrectionService)
