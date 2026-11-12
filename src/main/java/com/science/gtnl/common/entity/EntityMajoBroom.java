package com.science.gtnl.common.entity;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidBlock;

import com.science.gtnl.common.item.items.ItemMajoBroom;
import com.science.gtnl.config.ItemConfig;
import com.science.gtnl.loader.EnchantmentLoader;
import com.science.gtnl.loader.ItemLoader;

public class EntityMajoBroom extends Entity {

    public static final double MODEL_LIFT = 0.25D;
    public static final double HORIZONTAL_RESPONSE = 0.42D;
    public static final double FORWARD_ACCELERATION = 0.035D;
    public static final double COAST_DECELERATION = 0.02D;
    public static final double BRAKE_ACCELERATION = 0.075D;
    public static final double MAX_REVERSE_SPEED = 0.12D;
    public static final double UNMOUNTED_GRAVITY = 0.006D;
    public static final double UNMOUNTED_MAX_FALL_SPEED = 0.08D;
    public static final double UNMOUNTED_VERTICAL_DRAG = 0.92D;
    public static final double VERTICAL_DRAG = 0.9D;
    public static final double HOVER_DRAG = 0.65D;
    public static final double HOVER_BOB_ACCELERATION = 0.0015D;
    public static final int HOVER_BOB_DELAY_TICKS = 30;
    public static final int HOVER_BOB_PERIOD_TICKS = 72;
    public static final double GRAVITY = 0.03D;
    public static final double ASCEND_THRUST = 0.09D;
    public static final double DESCEND_THRUST = 0.03D;
    public static final float MAX_TURN_DEGREES_PER_TICK = 6.0F;
    public static final float TURN_ACCELERATION = 1.35F;
    public static final float TURN_DRAG = 0.78F;
    public static final float MAX_FORWARD_VISUAL_PITCH = 4.0F;
    public static final float VERTICAL_INPUT_VISUAL_PITCH = 14.0F;
    public static final float VERTICAL_STEP_VISUAL_PITCH = 80.0F;
    public static final float VERTICAL_SPEED_VISUAL_PITCH = 80.0F;
    public static final float MAX_VISUAL_PITCH = 20.0F;
    public static final float VISUAL_PITCH_RESPONSE = 0.2F;
    private static final float VISUAL_YAW_RESPONSE = 0.58F;
    private static final double CLIENT_POSITION_BLEND = 0.75D;
    private static final double CLIENT_MAX_CATCHUP_MULTIPLIER = 1.5D;
    private static final float CLIENT_ROTATION_BLEND = 0.65F;
    private static final double CLIENT_SNAP_DISTANCE_SQUARED = 256.0D;
    private static final int BROOM_STACK_WATCHER = 20;
    private static final int GROUND_SKILLS_WATCHER = 21;
    private static final String FLIGHT_USAGE_TAG = "GTNLMajoBroomFlightTicks";
    public ItemStack broomStack;
    private int flightTicks;
    private byte keyVerticalInput;
    public int hoverIdleTicks;
    public byte verticalInput;
    public double forwardSpeed;
    public float turnVelocity;
    public float visualPitch;
    public float prevVisualPitch;
    private float visualYaw;
    private float prevVisualYaw;
    private boolean clientVisualInitialized;
    private boolean clientHasTarget;
    private double clientTargetX;
    private double clientTargetY;
    private double clientTargetZ;
    private float clientTargetYaw;
    private float clientTargetPitch;
    private boolean clientWasRidden;

    public EntityMajoBroom(World world) {
        super(world);
        setSize(0.7F, 0.4F);
        yOffset = 0.0F;
        preventEntitySpawning = true;
    }

    public double getMaxHorizontalSpeed() {
        return ItemConfig.broom.maxSpeed / 20.0D * getSpeedMultiplier();
    }

    private double getSpeedMultiplier() {
        Enchantment enchantment = dataWatcher.getWatchableObjectByte(GROUND_SKILLS_WATCHER) != 0
            ? EnchantmentLoader.landSkills
            : EnchantmentLoader.airSkills;
        return 1.0D + 0.2D * EnchantmentLoader.getLevel(enchantment, getBroomStack());
    }

    public int getMaxFlightTicks() {
        return EnchantmentLoader.getFlightDurationTicks(getBroomStack());
    }

    protected boolean hasUnlimitedFlight() {
        return false;
    }

    public ItemStack getDefaultBroomStack() {
        return new ItemStack(ItemLoader.majoBroom);
    }

    public void setBroomStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemMajoBroom)) {
            stack = getDefaultBroomStack();
        }
        broomStack = stack.copy();
        broomStack.stackSize = 1;
        dataWatcher.updateObject(BROOM_STACK_WATCHER, broomStack);
        NBTTagCompound data = broomStack.getTagCompound();
        flightTicks = data == null ? 0 : Math.max(0, Math.min(getMaxFlightTicks(), data.getInteger(FLIGHT_USAGE_TAG)));
    }

    public ItemStack getBroomStack() {
        ItemStack stack = worldObj.isRemote ? dataWatcher.getWatchableObjectItemStack(BROOM_STACK_WATCHER) : broomStack;
        return stack == null ? getDefaultBroomStack() : stack;
    }

    public void setVerticalInput(byte input) {
        keyVerticalInput = (byte) Math.max(-1, Math.min(1, input));
    }

    public boolean isSupportedByGround() {
        return !worldObj.func_147461_a(
            boundingBox.copy()
                .offset(0.0D, -0.01D, 0.0D))
            .isEmpty();
    }

    @Override
    public void entityInit() {
        dataWatcher.addObjectByDataType(BROOM_STACK_WATCHER, 5);
        dataWatcher.addObject(GROUND_SKILLS_WATCHER, (byte) 0);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            updateClient();
            return;
        }

        simulateMotion(riddenByEntity);
        if (riddenByEntity != null) updateRiderPosition();
        if (onGround) flightTicks = 0;
        pushNearbyEntities();
    }

    public void updateClient() {
        boolean clientRidden = riddenByEntity instanceof EntityPlayer;
        if (!clientRidden) verticalInput = 0;
        boolean ridingChanged = clientRidden != clientWasRidden;
        clientWasRidden = clientRidden;
        if (!clientVisualInitialized || ridingChanged) {
            visualYaw = rotationYaw;
            prevVisualYaw = rotationYaw;
            clientVisualInitialized = true;
        } else {
            prevVisualYaw = visualYaw;
        }
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        double oldX = posX;
        double oldY = posY;
        double oldZ = posZ;
        if (clientHasTarget) {
            double dx = clientTargetX - posX;
            double dy = clientTargetY - posY;
            double dz = clientTargetZ - posZ;
            if (dx * dx + dy * dy + dz * dz > CLIENT_SNAP_DISTANCE_SQUARED) {
                snapClientTransform(clientTargetX, clientTargetY, clientTargetZ, clientTargetYaw, clientTargetPitch);
            } else {
                double stepX = dx * CLIENT_POSITION_BLEND;
                double stepY = dy * CLIENT_POSITION_BLEND;
                double stepZ = dz * CLIENT_POSITION_BLEND;
                double stepSquared = stepX * stepX + stepY * stepY + stepZ * stepZ;
                double maxStep = getMaxHorizontalSpeed() * CLIENT_MAX_CATCHUP_MULTIPLIER;
                if (stepSquared > maxStep * maxStep) {
                    double scale = maxStep / Math.sqrt(stepSquared);
                    stepX *= scale;
                    stepY *= scale;
                    stepZ *= scale;
                }
                setPosition(posX + stepX, posY + stepY, posZ + stepZ);
                setRotation(
                    rotationYaw
                        + MathHelper.wrapAngleTo180_float(clientTargetYaw - rotationYaw) * CLIENT_ROTATION_BLEND,
                    rotationPitch + (clientTargetPitch - rotationPitch) * CLIENT_ROTATION_BLEND);
            }
        }
        if (riddenByEntity != null) updateRiderPosition();
        if (clientVisualInitialized) {
            float yawDelta = MathHelper.wrapAngleTo180_float(rotationYaw - visualYaw);
            visualYaw += yawDelta * VISUAL_YAW_RESPONSE;
            if (Math.abs(yawDelta) < 0.01F) visualYaw = rotationYaw;
        }
        motionX = motionY = motionZ = 0.0D;
        updateVisualPitch(posX - oldX, posY - oldY, posZ - oldZ);
    }

    @Override
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int steps) {
        double dx = x - posX;
        double dy = y - posY;
        double dz = z - posZ;
        if (dx * dx + dy * dy + dz * dz > CLIENT_SNAP_DISTANCE_SQUARED) {
            snapClientTransform(x, y, z, yaw, pitch);
            return;
        }
        clientTargetX = x;
        clientTargetY = y;
        clientTargetZ = z;
        clientTargetYaw = yaw;
        clientTargetPitch = pitch;
        clientHasTarget = true;
    }

    private void snapClientTransform(double x, double y, double z, float yaw, float pitch) {
        setPosition(x, y, z);
        setRotation(yaw, pitch);
        lastTickPosX = x;
        lastTickPosY = y;
        lastTickPosZ = z;
        prevRotationYaw = yaw;
        prevRotationPitch = pitch;
        visualYaw = yaw;
        prevVisualYaw = yaw;
        clientVisualInitialized = true;
        clientHasTarget = false;
    }

    public void simulateMotion(Entity rider) {
        if (!(rider instanceof EntityPlayer)) {
            simulateUnmountedMotion();
            return;
        }
        EntityPlayer player = (EntityPlayer) rider;
        float forward = MathHelper.clamp_float(player.moveForward, -1.0F, 1.0F);
        float strafe = MathHelper.clamp_float(player.moveStrafing, -1.0F, 1.0F);
        verticalInput = keyVerticalInput != 0 ? keyVerticalInput : getViewVerticalInput(player, forward);
        boolean supported = isSupportedByGround();
        dataWatcher.updateObject(GROUND_SKILLS_WATCHER, (byte) (supported ? 1 : 0));
        boolean hasSeabreeze = EnchantmentLoader.getLevel(EnchantmentLoader.seabreeze, getBroomStack()) > 0;
        if (hasSeabreeze && !Double.isNaN(getLiquidSurfaceY(0.0D, 0.0D))) flightTicks = 0;
        boolean canFly = hasUnlimitedFlight() || flightTicks < getMaxFlightTicks();
        if (!canFly) {
            motionY = motionY * VERTICAL_DRAG - GRAVITY;
        } else if (verticalInput == 0) {
            motionY *= HOVER_DRAG;
            if (Math.abs(motionY) < 0.0001D) motionY = 0.0D;
        } else {
            motionY = motionY * VERTICAL_DRAG - GRAVITY;
            if (verticalInput > 0) motionY += ASCEND_THRUST;
            else if (verticalInput < 0) motionY -= DESCEND_THRUST;
        }
        player.fallDistance = 0;
        fallDistance = 0;
        prevRotationYaw = rotationYaw;
        updateSteering(strafe);
        rotationPitch = prevRotationPitch = 0.0F;

        updateHorizontalMotion(forward);
        limitHorizontalSpeed();
        double liquidSurface = hasSeabreeze ? getLiquidSurfaceY(motionX, motionZ) : Double.NaN;
        boolean seaBreezing = !Double.isNaN(liquidSurface);
        if (seaBreezing) {
            flightTicks = 0;
            if (motionY <= 0.0D) motionY = Math.max(motionY * 0.3D, liquidSurface - boundingBox.minY);
        }
        updateHoverBobbing(rider, supported || seaBreezing);

        if (supported && motionY <= 0.0D) motionY = Math.min(motionY, -0.001D);
        moveEntity(motionX, motionY, motionZ);
        if (onGround) {
            motionY = 0;
        } else if (!seaBreezing && !hasUnlimitedFlight() && flightTicks < getMaxFlightTicks()) {
            flightTicks++;
        }
    }

    private double getLiquidSurfaceY(double offsetX, double offsetZ) {
        double minSurface = boundingBox.minY + Math.min(0.0D, motionY) - 0.25D;
        double maxSurface = boundingBox.minY + 0.15D;
        int minX = MathHelper.floor_double(boundingBox.minX + offsetX);
        int maxX = MathHelper.floor_double(boundingBox.maxX + offsetX - 0.0001D);
        int minZ = MathHelper.floor_double(boundingBox.minZ + offsetZ);
        int maxZ = MathHelper.floor_double(boundingBox.maxZ + offsetZ - 0.0001D);
        int minY = Math.max(0, MathHelper.floor_double(minSurface) - 1);
        int maxY = Math.min(worldObj.getHeight() - 1, MathHelper.floor_double(maxSurface));
        double surface = Double.NaN;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!worldObj.blockExists(x, y, z)) continue;
                    Block block = worldObj.getBlock(x, y, z);
                    Material material = block.getMaterial();
                    if (material != Material.water && material != Material.lava) continue;
                    if (worldObj.getBlock(x, y + 1, z)
                        .getMaterial() == material) continue;
                    double filled;
                    if (block instanceof IFluidBlock fluid) {
                        filled = fluid.getFilledPercentage(worldObj, x, y, z);
                    } else if (block instanceof BlockLiquid) {
                        filled = 1.0D - BlockLiquid.getLiquidHeightPercent(worldObj.getBlockMetadata(x, y, z));
                    } else {
                        continue;
                    }
                    if (filled <= 0.0D) continue;
                    double candidate = y + Math.min(1.0D, filled);
                    if (candidate >= minSurface && candidate <= maxSurface
                        && (Double.isNaN(surface) || candidate > surface)) surface = candidate;
                }
            }
        }
        return surface;
    }

    public void simulateUnmountedMotion() {
        keyVerticalInput = 0;
        verticalInput = 0;
        turnVelocity = 0.0F;
        forwardSpeed = 0.0D;
        motionX *= 0.8D;
        motionZ *= 0.8D;
        motionY = Math.max(motionY * UNMOUNTED_VERTICAL_DRAG - UNMOUNTED_GRAVITY, -UNMOUNTED_MAX_FALL_SPEED);
        moveEntity(motionX, motionY, motionZ);
        if (onGround) motionY = 0.0D;
        dataWatcher.updateObject(GROUND_SKILLS_WATCHER, (byte) (onGround ? 1 : 0));
    }

    public byte getViewVerticalInput(EntityPlayer player, float forward) {
        if (!ItemConfig.broom.enableViewControl || forward <= 0.01F) return 0;
        if (player.rotationPitch <= -30.0F) return 1;
        if (player.rotationPitch >= 30.0F) return -1;
        return 0;
    }

    public void updateHorizontalMotion(float forward) {
        forward = MathHelper.clamp_float(forward, -1.0F, 1.0F);
        double reverseSpeed = MAX_REVERSE_SPEED * getSpeedMultiplier();
        forwardSpeed = Math.max(-reverseSpeed, Math.min(getMaxHorizontalSpeed(), forwardSpeed));
        if (forward > 0.01F) {
            forwardSpeed = Math.min(getMaxHorizontalSpeed(), forwardSpeed + FORWARD_ACCELERATION * forward);
        } else if (forward < -0.01F) {
            if (forwardSpeed > 0.0D) {
                forwardSpeed = Math.max(0.0D, forwardSpeed - BRAKE_ACCELERATION);
            } else {
                forwardSpeed = Math.max(-reverseSpeed, forwardSpeed - FORWARD_ACCELERATION * 0.5D);
            }
        } else {
            if (forwardSpeed > 0.0D) forwardSpeed = Math.max(0.0D, forwardSpeed - COAST_DECELERATION);
            else if (forwardSpeed < 0.0D) forwardSpeed = Math.min(0.0D, forwardSpeed + BRAKE_ACCELERATION * 0.5D);
        }
        float yaw = rotationYaw * (float) Math.PI / 180.0F;
        float sin = MathHelper.sin(yaw);
        float cos = MathHelper.cos(yaw);
        double targetX = -sin * forwardSpeed;
        double targetZ = cos * forwardSpeed;
        motionX += (targetX - motionX) * HORIZONTAL_RESPONSE;
        motionZ += (targetZ - motionZ) * HORIZONTAL_RESPONSE;
        if (forwardSpeed == 0.0D) {
            if (Math.abs(motionX) < 0.001D) motionX = 0.0D;
            if (Math.abs(motionZ) < 0.001D) motionZ = 0.0D;
        }
    }

    public void updateHoverBobbing(Entity rider, boolean supported) {
        if (!(rider instanceof EntityPlayer player) || supported
            || verticalInput != 0
            || Math.abs(player.moveForward) > 0.01F
            || Math.abs(player.moveStrafing) > 0.01F
            || motionX != 0.0D
            || motionZ != 0.0D
            || turnVelocity != 0.0F
            || Math.abs(motionY) >= (hoverIdleTicks >= HOVER_BOB_DELAY_TICKS ? 0.02D : 0.0001D)) {
            hoverIdleTicks = 0;
            return;
        }
        hoverIdleTicks++;
        if (hoverIdleTicks >= HOVER_BOB_DELAY_TICKS) {
            if (hoverIdleTicks >= HOVER_BOB_DELAY_TICKS + HOVER_BOB_PERIOD_TICKS)
                hoverIdleTicks = HOVER_BOB_DELAY_TICKS;
            double phase = (hoverIdleTicks - HOVER_BOB_DELAY_TICKS) * 2.0D * Math.PI / HOVER_BOB_PERIOD_TICKS;
            motionY += HOVER_BOB_ACCELERATION * Math.sin(phase);
        }
    }

    public void updateVisualPitch(double horizontalX, double verticalStep, double horizontalZ) {
        prevVisualPitch = visualPitch;
        float yaw = rotationYaw * (float) Math.PI / 180.0F;
        double forwardStep = -horizontalX * MathHelper.sin(yaw) + horizontalZ * MathHelper.cos(yaw);
        float forwardPitch = MathHelper.clamp_float((float) (forwardStep / getMaxHorizontalSpeed()), -1.0F, 1.0F)
            * MAX_FORWARD_VISUAL_PITCH;
        float targetPitch = MathHelper.clamp_float(
            verticalInput * VERTICAL_INPUT_VISUAL_PITCH + (float) verticalStep * VERTICAL_STEP_VISUAL_PITCH
                + (float) motionY * VERTICAL_SPEED_VISUAL_PITCH
                - forwardPitch,
            -MAX_VISUAL_PITCH,
            MAX_VISUAL_PITCH);
        visualPitch += (targetPitch - visualPitch) * VISUAL_PITCH_RESPONSE;
    }

    public float getVisualPitch(float partialTicks) {
        return prevVisualPitch + (visualPitch - prevVisualPitch) * partialTicks;
    }

    public float getVisualYaw(float partialTicks) {
        if (!clientVisualInitialized) return rotationYaw;
        float delta = MathHelper.wrapAngleTo180_float(visualYaw - prevVisualYaw);
        return prevVisualYaw + delta * partialTicks;
    }

    public void limitHorizontalSpeed() {
        double speedSquared = motionX * motionX + motionZ * motionZ;
        double maxSpeed = getMaxHorizontalSpeed();
        double limitSquared = maxSpeed * maxSpeed;
        if (speedSquared > limitSquared) {
            double scale = maxSpeed / Math.sqrt(speedSquared);
            motionX *= scale;
            motionZ *= scale;
        }
    }

    public void updateSteering(float strafe) {
        turnVelocity = MathHelper.clamp_float(
            turnVelocity * TURN_DRAG - strafe * TURN_ACCELERATION,
            -MAX_TURN_DEGREES_PER_TICK,
            MAX_TURN_DEGREES_PER_TICK);
        if (Math.abs(turnVelocity) < 0.01F) turnVelocity = 0.0F;
        rotationYaw += turnVelocity;
    }

    public void pushNearbyEntities() {
        List<Entity> nearby = worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(0.2D, 0.0D, 0.2D));
        for (Entity entity : nearby) {
            if (entity != riddenByEntity && entity.canBePushed()) entity.applyEntityCollision(this);
        }
    }

    @Override
    public double getMountedYOffset() {
        return 0.15D + MODEL_LIFT;
    }

    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (isDead) return false;
        if (player.isSneaking()) {
            if (!worldObj.isRemote && riddenByEntity == null) recover(player);
            return true;
        }
        if (riddenByEntity == null || riddenByEntity == player) {
            if (!worldObj.isRemote) player.mountEntity(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isEntityInvulnerable() {
        return true;
    }

    public void recover(EntityPlayer player) {
        if (worldObj.isRemote || isDead || riddenByEntity != null || player.isDead || player.worldObj != worldObj)
            return;
        ItemStack result = getBroomStack().copy();
        if (!result.hasTagCompound()) result.setTagCompound(new NBTTagCompound());
        if (flightTicks > 0) result.getTagCompound()
            .setInteger(FLIGHT_USAGE_TAG, flightTicks);
        else result.getTagCompound()
            .removeTag(FLIGHT_USAGE_TAG);
        setDead();
        if (!player.inventory.addItemStackToInventory(result)) entityDropItem(result, 0.0F);
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead && !(riddenByEntity instanceof EntityPlayer);
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public void addVelocity(double x, double y, double z) {
        // The broom is deliberately unaffected by entity collisions and explosions.
    }

    @Override
    public boolean canTriggerWalking() {
        return false;
    }

    @Override
    public void fall(float distance) {}

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        ItemStack stack = tag.hasKey("BroomStack", 10)
            ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("BroomStack"))
            : null;
        setBroomStack(stack == null ? getDefaultBroomStack() : stack);
        if (tag.hasKey("FlightTicks", 3))
            flightTicks = Math.max(0, Math.min(getMaxFlightTicks(), tag.getInteger("FlightTicks")));
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        tag.setInteger("FlightTicks", flightTicks);
        tag.setTag("BroomStack", getBroomStack().writeToNBT(new NBTTagCompound()));
    }
}
