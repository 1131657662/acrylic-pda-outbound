package com.acrylic.outbound.reports;

public record ReportLine(String dispatchId, String captureId, long orderScanSeq,
        String originDeviceId, long deviceCaptureSeq, String operatorName, String inputSource,
        String barcode, long dispatchCycle, String productCode, String weightGrams,
        String rawWeight, String receivedAt, String savedAt, String timeQuality, String remark) { }
