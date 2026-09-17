package com.acrylic.outbound.inventory.parser;

public record ParsedBale(String model, String name, String barcode, String deliveryCode,
                         String rawWeight, String weightGrams) { }
