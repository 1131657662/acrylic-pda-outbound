package com.acrylic.outbound.recovery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test-side recorder for the S3 real-HTTP run. Nothing in the application reads or writes it. */
final class RecoveryRootObservation {
    /** One traversal of the probe filter: which endpoint, which dispatch, and what the root looked like. */
    record Pass(String endpoint, String dispatcherType, boolean rootMarker, int liveRoots, int openSubOperations) { }

    /** One framework session save: was the saving thread inside a live root at that moment. */
    record Save(String outcome, int liveRoots) { }

    private final List<Pass> passes = new CopyOnWriteArrayList<>();
    private final List<Save> saves = new CopyOnWriteArrayList<>();
    private final List<String> asyncBorrows = new CopyOnWriteArrayList<>();
    private final List<Integer> liveRootsDuringRequests = new CopyOnWriteArrayList<>();
    private final List<String> endpoints = new CopyOnWriteArrayList<>();

    void pass(Pass pass) { passes.add(pass); }
    void save(Save save) { saves.add(save); }
    void asyncBorrow(String code) { asyncBorrows.add(code); }
    void duringRequest(String endpoint, int liveRoots) {
        endpoints.add(endpoint);
        liveRootsDuringRequests.add(liveRoots);
    }

    List<Pass> passes() { return List.copyOf(passes); }
    List<Save> saves() { return List.copyOf(saves); }
    List<String> asyncBorrows() { return List.copyOf(asyncBorrows); }
    List<Integer> liveRootsDuringRequests() { return List.copyOf(liveRootsDuringRequests); }
    List<String> endpoints() { return List.copyOf(endpoints); }
}
