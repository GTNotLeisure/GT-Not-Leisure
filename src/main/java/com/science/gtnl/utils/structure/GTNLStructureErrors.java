package com.science.gtnl.utils.structure;

import gregtech.api.enums.GTValues;
import gregtech.api.structure.error.ErrorType;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.structure.error.TranslatableText;

public class GTNLStructureErrors {

    public static StructureError parallelControllerHatchCount(ErrorType type, int current, int target) {
        return StructureErrors.hatchCount(
            type,
            TranslatableText.lang("gtnl.gui.structure_error.parallel_controller_hatch"),
            current,
            target);
    }

    public static StructureError energyHatchTierTooHigh(int current, int maximum) {
        return hatchTierTooHigh("gtnl.gui.structure_error.energy_hatch_tier_too_high", current, maximum);
    }

    public static StructureError inputHatchTierTooHigh(int current, int maximum) {
        return hatchTierTooHigh("gtnl.gui.structure_error.input_hatch_tier_too_high", current, maximum);
    }

    public static StructureError dynamoHatchTierTooHigh(int current, int maximum) {
        return hatchTierTooHigh("gtnl.gui.structure_error.dynamo_hatch_tier_too_high", current, maximum);
    }

    private static StructureError hatchTierTooHigh(String langKey, int current, int maximum) {
        return StructureErrors.of(langKey, voltageTier(current), voltageTier(maximum));
    }

    private static TranslatableText voltageTier(int tier) {
        return TranslatableText
            .literal(tier >= 0 && tier < GTValues.VN.length ? GTValues.VN[tier] : String.valueOf(tier));
    }

    public static StructureError dynamoAmperageTooHigh(long current, long maximum) {
        return StructureErrors.of(
            "gtnl.gui.structure_error.dynamo_amperage_too_high",
            TranslatableText.literal(String.valueOf(current)),
            TranslatableText.literal(String.valueOf(maximum)));
    }

    public static StructureError controllerItemRequired(TranslatableText requiredItem) {
        return StructureErrors.of("gtnl.gui.structure_error.controller_item_required", requiredItem);
    }

    public static StructureError missingTurbineRotors(int current, int required) {
        return StructureErrors.of(
            "gtnl.gui.structure_error.missing_turbine_rotors",
            TranslatableText.literal(current),
            TranslatableText.literal(required));
    }

    public static StructureError invalidEnergyHatchConfiguration() {
        return StructureErrors.of("gtnl.gui.structure_error.invalid_energy_hatch_configuration");
    }

    public static StructureError laserEnergyTunnelDisabled() {
        return StructureErrors.of("gtnl.gui.structure_error.laser_energy_tunnel_disabled");
    }

    public static StructureError energyInputAmperageTooHigh() {
        return StructureErrors.of("gtnl.gui.structure_error.energy_input_amperage_too_high");
    }

    public static StructureError missingDistillationLayerOutputHatch() {
        return StructureErrors.of("gtnl.gui.structure_error.missing_distillation_layer_output_hatch");
    }

    public static StructureError unknownLegacyCheckFailure() {
        return StructureErrors.of("gtnl.gui.structure_error.legacy_check_failed");
    }
}
