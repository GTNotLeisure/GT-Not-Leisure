package com.science.gtnl.common.render.beamformer;

import java.util.Map;

import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.client.model.loading.ModelDeserializer.Position;
import com.gtnewhorizon.gtnhlib.client.model.loading.ResourceLoc.ModelLoc;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.eventbus.Phase;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.render.model.JsonBlockModel;
import com.science.gtnl.common.render.model.JsonBlockModel.Geometry;
import com.science.gtnl.common.render.model.JsonItemModel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT, phase = Phase.PRE)
public class BeamFormerModel {

    public static final BeamFormerModel INSTANCE = new BeamFormerModel();
    private static final ModelLoc MODEL = ModelLoc.fromStr(ScienceNotLeisure.RESOURCE_ROOT_ID + ":block/beam_former");
    private JsonBlockModel pending;
    private volatile Geometry[] orientations;
    private volatile Map<Position, Geometry> itemModels;

    public Geometry get(ForgeDirection front, ForgeDirection up) {
        Geometry[] models = orientations;
        if (models == null || front == ForgeDirection.UNKNOWN || up == ForgeDirection.UNKNOWN) return null;
        return models[front.ordinal() * 6 + up.ordinal()];
    }

    public Geometry getItem(Position position) {
        Map<Position, Geometry> models = itemModels;
        return models == null ? null : models.get(position);
    }

    @SubscribeEvent
    public static void beforeStitch(TextureStitchEvent.Pre event) {
        INSTANCE.handleBeforeStitch(event);
    }

    public void handleBeforeStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) return;
        orientations = null;
        itemModels = null;
        pending = null;
        try {
            pending = new JsonBlockModel(JsonItemModel.load(MODEL));
        } catch (RuntimeException exception) {
            ScienceNotLeisure.LOG.error("Cannot load Beam Former model", exception);
        }
    }

    @SubscribeEvent
    public static void afterStitch(TextureStitchEvent.Post event) {
        INSTANCE.handleAfterStitch(event);
    }

    public void handleAfterStitch(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() != 0 || pending == null) return;
        try {
            Geometry[] models = new Geometry[36];
            for (ForgeDirection front : ForgeDirection.VALID_DIRECTIONS) {
                for (ForgeDirection up : ForgeDirection.VALID_DIRECTIONS) {
                    if (front != up && front != up.getOpposite()) {
                        models[front.ordinal() * 6 + up.ordinal()] = pending.bake(front, up);
                    }
                }
            }
            Map<Position, Geometry> items = JsonItemModel
                .bakeItems(pending, models[ForgeDirection.NORTH.ordinal() * 6 + ForgeDirection.UP.ordinal()]);
            orientations = models;
            itemModels = items;
        } catch (RuntimeException exception) {
            ScienceNotLeisure.LOG.error("Cannot bake Beam Former model", exception);
        } finally {
            pending = null;
        }
    }
}
