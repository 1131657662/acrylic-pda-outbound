package com.acrylic.outbound.recovery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test-side recorder for the real-HTTP runs. Nothing in the application reads or writes it. */
final class RecoveryRootObservation {
    /** One traversal of the probe filter: which endpoint, which dispatch, and what the root looked like. */
    record Pass(String endpoint, String dispatcherType, boolean rootMarker, int liveRoots, int openSubOperations) { }

    /** One framework session save: was the saving thread inside a live root at that moment. */
    record Save(String outcome, int liveRoots) { }

    /** One servlet multipart parse: was the request inside a live root, and how many staged files. */
    record Parse(String bound, int stagedFiles) { }

    private final List<Pass> passes = new CopyOnWriteArrayList<>();
    private final List<Save> saves = new CopyOnWriteArrayList<>();
    private final List<Parse> parses = new CopyOnWriteArrayList<>();
    private final List<String> asyncBorrows = new CopyOnWriteArrayList<>();
    private final List<Integer> concurrentRoots = new CopyOnWriteArrayList<>();
    private final List<Integer> liveRootsDuringRequests = new CopyOnWriteArrayList<>();
    private final List<String> endpoints = new CopyOnWriteArrayList<>();

    void pass(Pass pass) { passes.add(pass); }
    void save(Save save) { saves.add(save); }
    void parse(Parse parse) { parses.add(parse); }
    void asyncBorrow(String code) { asyncBorrows.add(code); }
    void concurrent(int liveRoots) { concurrentRoots.add(liveRoots); }
    void duringRequest(String endpoint, int liveRoots) {
        endpoints.add(endpoint);
        liveRootsDuringRequests.add(liveRoots);
    }

    List<Pass> passes() { return List.copyOf(passes); }
    List<Save> saves() { return List.copyOf(saves); }
    List<Parse> parses() { return List.copyOf(parses); }
    List<String> asyncBorrows() { return List.copyOf(asyncBorrows); }
    List<Integer> concurrentRoots() { return List.copyOf(concurrentRoots); }
    List<Integer> liveRootsDuringRequests() { return List.copyOf(liveRootsDuringRequests); }
    List<String> endpoints() { return List.copyOf(endpoints); }
}
