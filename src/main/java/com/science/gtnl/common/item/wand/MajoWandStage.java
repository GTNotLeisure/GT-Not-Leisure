package com.science.gtnl.common.item.wand;

public enum MajoWandStage {

    DEPARTURE("departure", 75, 1.0F, 0xA48ABE),
    RESONANCE("resonance", 150, 0.95F, 0xB5C5E5),
    STARLIGHT("starlight", 300, 0.9F, 0xE8DDAB),
    VOID("void", 600, 0.85F, 0x78619E),
    ASHEN_LEGACY("ashen_legacy", 1000, 0.8F, 0xC1BDC9),
    JOURNEYS_END("journeys_end", 2000, 0.7F, 0xE5B3EA);

    private static final MajoWandStage[] STAGES = values();

    public final String key;
    public final int capacity;
    public final float costMultiplier;
    public final int color;

    MajoWandStage(String key, int capacity, float costMultiplier, int color) {
        this.key = key;
        this.capacity = capacity;
        this.costMultiplier = costMultiplier;
        this.color = color;
    }

    public String getResearch() {
        return "gtnl.majo_wand." + key;
    }

    public static MajoWandStage fromMetadata(int metadata) {
        return metadata >= 0 && metadata < STAGES.length ? STAGES[metadata] : DEPARTURE;
    }
}
