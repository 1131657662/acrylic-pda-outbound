package com.acrylic.outbound.correction;

import java.util.*;

/** Validates the immutable posting graph; result quantities never stand in for postings. */
final class CorrectionContribution {
    record Entry(UUID id, UUID bale, UUID line, String kind, long quantity, long weight,
        UUID reverses, UUID reversesInventory, UUID restores, boolean original) {
        UUID reference() { return restores != null ? restores : reversesInventory != null ? reversesInventory : reverses; }
    }

    static List<Entry> active(List<Entry> entries, UUID bale, long[] result) {
        var byId = index(entries, bale);
        var consumed = new HashSet<UUID>();
        for (var entry : entries) validate(entry, byId, consumed);
        var active = entries.stream().filter(entry -> contributes(entry, byId) && !consumed.contains(entry.id())).toList();
        long quantity = 0, weight = 0, postedQuantity = 0, postedWeight = 0;
        for (var entry : active) {
            quantity = Math.addExact(quantity, entry.quantity()); weight = Math.addExact(weight, entry.weight());
        }
        for (var entry : entries) {
            postedQuantity = Math.addExact(postedQuantity, entry.quantity()); postedWeight = Math.addExact(postedWeight, entry.weight());
        }
        if (quantity != postedQuantity || weight != postedWeight) throw CorrectionCommand.ledgerConflict();
        if (entries.isEmpty()) return active;
        if (quantity != Math.negateExact(result[0]) || weight != Math.negateExact(result[1]))
            throw CorrectionCommand.ledgerConflict();
        return active;
    }

    static Entry representative(List<Entry> entries, Entry source) {
        var children = new HashMap<UUID, Entry>();
        for (var entry : entries) if (entry.reference() != null) {
            if (children.put(entry.reference(), entry) != null) throw CorrectionCommand.ledgerConflict();
        }
        var visited = new HashSet<UUID>();
        while (children.containsKey(source.id())) {
            if (!visited.add(source.id())) throw CorrectionCommand.ledgerConflict();
            source = children.get(source.id());
        }
        return source;
    }

    private static Map<UUID, Entry> index(List<Entry> entries, UUID bale) {
        var result = new HashMap<UUID, Entry>();
        for (var entry : entries) {
            if (!entry.bale().equals(bale) || result.put(entry.id(), entry) != null) throw CorrectionCommand.ledgerConflict();
        }
        return result;
    }

    private static void validate(Entry entry, Map<UUID, Entry> byId, Set<UUID> consumed) {
        int refs = (entry.reverses() == null ? 0 : 1) + (entry.reversesInventory() == null ? 0 : 1)
            + (entry.restores() == null ? 0 : 1);
        if (refs > 1) throw CorrectionCommand.ledgerConflict();
        if (entry.reference() == null) { validateRoot(entry); return; }
        var source = byId.get(entry.reference());
        if (entry.original() || source == null || !consumed.add(source.id()) || !source.bale().equals(entry.bale())
            || entry.quantity() != Math.negateExact(source.quantity()) || entry.weight() != Math.negateExact(source.weight()))
            throw CorrectionCommand.ledgerConflict();
        root(entry, byId);
        if (entry.restores() != null) validateRestore(entry, source, byId);
        else if (!"REVERSAL".equals(entry.kind()) || !contributes(source, byId)
            || source.original() != (entry.reversesInventory() != null)) throw CorrectionCommand.ledgerConflict();
    }

    private static void validateRestore(Entry entry, Entry source, Map<UUID, Entry> byId) {
        if (!"WITHDRAWAL_RESTORE".equals(entry.kind()) || source.original()) throw CorrectionCommand.ledgerConflict();
        if (contributes(source, byId)) {
            var origin = root(source, byId);
            if (origin.original() || !Objects.equals(entry.line(), origin.line())) throw CorrectionCommand.ledgerConflict();
        } else if (!"REVERSAL".equals(source.kind()) || !Objects.equals(entry.line(), source.line()))
            throw CorrectionCommand.ledgerConflict();
    }

    private static void validateRoot(Entry entry) {
        boolean debit = entry.original() || "MANUAL_DEDUCT".equals(entry.kind());
        if (debit && entry.quantity() == -1 && entry.weight() < 0) return;
        if (!debit && "WEIGHT_DIFF".equals(entry.kind()) && entry.quantity() == 0 && entry.weight() != 0) return;
        throw CorrectionCommand.ledgerConflict();
    }

    private static boolean contributes(Entry entry, Map<UUID, Entry> byId) {
        boolean active = true;
        var visited = new HashSet<UUID>();
        while (entry.reference() != null) {
            if (!visited.add(entry.id())) throw CorrectionCommand.ledgerConflict();
            active = !active; entry = byId.get(entry.reference());
            if (entry == null) throw CorrectionCommand.ledgerConflict();
        }
        return active;
    }

    private static Entry root(Entry entry, Map<UUID, Entry> byId) {
        var visited = new HashSet<UUID>();
        while (entry.reference() != null) {
            if (!visited.add(entry.id())) throw CorrectionCommand.ledgerConflict();
            entry = byId.get(entry.reference());
            if (entry == null) throw CorrectionCommand.ledgerConflict();
        }
        return entry;
    }
}
