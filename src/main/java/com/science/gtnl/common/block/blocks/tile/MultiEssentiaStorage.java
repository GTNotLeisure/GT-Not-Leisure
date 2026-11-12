package com.science.gtnl.common.block.blocks.tile;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

public class MultiEssentiaStorage {

    private final AspectList storedAspects;
    private final int capacity;
    private Aspect[] cachedSortedAspects;
    private boolean sortedCacheDirty = true;

    private int totalAmount;

    public MultiEssentiaStorage(AspectList storedAspects, int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Essentia capacity must not be negative");
        this.storedAspects = Objects.requireNonNull(storedAspects, "storedAspects");
        this.capacity = capacity;
        reload();
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public int getStoredTypeCount() {
        return storedAspects.size();
    }

    public boolean isEmpty() {
        return getTotalAmount() <= 0;
    }

    public boolean isFull() {
        return getTotalAmount() >= capacity;
    }

    public int getAmount(Aspect aspect) {
        return aspect == null ? 0 : storedAspects.getAmount(aspect);
    }

    public AspectList copyAspects() {
        AspectList copy = new AspectList();
        for (Aspect storedAspect : getStoredAspectsSorted()) {
            copy.add(storedAspect, storedAspects.getAmount(storedAspect));
        }
        return copy;
    }

    public void setAspects(AspectList aspects) {
        if (aspects == storedAspects) aspects = copyAspects();
        storedAspects.aspects.clear();
        int remaining = capacity;
        if (aspects != null) {
            for (Aspect storedAspect : getSortedAspects(aspects)) {
                int accepted = Math.min(aspects.getAmount(storedAspect), remaining);
                if (accepted <= 0) continue;

                storedAspects.add(storedAspect, accepted);
                remaining -= accepted;
                if (remaining == 0) break;
            }
        }
        totalAmount = capacity - remaining;
        sortedCacheDirty = true;
    }

    public boolean doesContainerAccept(Aspect aspect) {
        return aspect != null && getTotalAmount() < capacity;
    }

    public int addToContainer(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return amount;

        int accepted = Math.min(amount, capacity - totalAmount);
        if (accepted <= 0) return amount;

        storedAspects.add(aspect, accepted);
        totalAmount += accepted;
        sortedCacheDirty = true;
        return amount - accepted;
    }

    public boolean takeFromContainer(Aspect aspect, int amount) {
        if (!doesContainerContainAmount(aspect, amount)) return false;

        storedAspects.remove(aspect, amount);
        totalAmount -= amount;
        sortedCacheDirty = true;
        return true;
    }

    public boolean takeFromContainer(AspectList requestedAspects) {
        if (!doesContainerContain(requestedAspects)) return false;

        for (Aspect requestedAspect : getSortedAspects(requestedAspects)) {
            int amount = requestedAspects.getAmount(requestedAspect);
            storedAspects.remove(requestedAspect, amount);
            totalAmount -= amount;
        }
        sortedCacheDirty = true;
        return true;
    }

    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        return aspect != null && amount >= 0 && storedAspects.getAmount(aspect) >= amount;
    }

    public boolean doesContainerContain(AspectList requestedAspects) {
        if (requestedAspects == null) return false;

        for (Aspect requestedAspect : getSortedAspects(requestedAspects)) {
            if (!doesContainerContainAmount(requestedAspect, requestedAspects.getAmount(requestedAspect))) {
                return false;
            }
        }
        return true;
    }

    public int clearAll() {
        int clearedAmount = totalAmount;
        if (clearedAmount <= 0) return 0;

        storedAspects.aspects.clear();
        totalAmount = 0;
        sortedCacheDirty = true;
        return clearedAmount;
    }

    public void reload() {
        sortedCacheDirty = true;
        removeInvalidAspects();
        trimToCapacity();
        recomputeTotalAmount();
    }

    private void recomputeTotalAmount() {
        totalAmount = 0;
        for (Aspect storedAspect : storedAspects.aspects.keySet()) {
            totalAmount += storedAspects.getAmount(storedAspect);
        }
    }

    public void markDirty() {
        sortedCacheDirty = true;
    }

    public Aspect[] getStoredAspectsSorted() {
        if (sortedCacheDirty) {
            cachedSortedAspects = getSortedAspects(storedAspects);
            sortedCacheDirty = false;
        }
        return cachedSortedAspects;
    }

    private void removeInvalidAspects() {
        storedAspects.aspects.entrySet()
            .removeIf(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0);
    }

    private void trimToCapacity() {
        int remaining = capacity;
        AspectList trimmed = new AspectList();

        for (Aspect storedAspect : getStoredAspectsSorted()) {
            int accepted = Math.min(storedAspects.getAmount(storedAspect), remaining);

            if (accepted > 0) {
                trimmed.add(storedAspect, accepted);
                remaining -= accepted;
            }

            if (remaining == 0) break;
        }

        storedAspects.aspects.clear();
        for (Aspect storedAspect : getSortedAspects(trimmed)) {
            storedAspects.add(storedAspect, trimmed.getAmount(storedAspect));
        }
        sortedCacheDirty = true;
    }

    public static Aspect[] getSortedAspects(AspectList aspectList) {
        return aspectList.aspects.entrySet()
            .stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparing(Aspect::getTag))
            .toArray(Aspect[]::new);
    }
}
