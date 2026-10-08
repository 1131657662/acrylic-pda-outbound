package com.acrylic.outbound.handover;

import com.acrylic.outbound.devices.DeviceStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Fault writes require their own explicit gate in addition to ordinary handover command assembly. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"outbound.handover.commands.enabled", "outbound.handover.fault.enabled"}, havingValue = "true")
class HandoverFaultConfiguration {
    @Bean
    HandoverCommand.Operation handoverFaultFence(HandoverStopStore origins, HandoverFaultArchive archives,
            HandoverTemporaryApprovalEvidence validation, HandoverFaultStore fences, HandoverStore cases, DeviceStore devices) {
        return new HandoverFaultOperation(origins, archives, validation, fences, cases, devices);
    }
}
