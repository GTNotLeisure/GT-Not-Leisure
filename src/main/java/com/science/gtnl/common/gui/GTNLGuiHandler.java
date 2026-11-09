package com.science.gtnl.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.block.blocks.tile.TileEntityDirePatternEncoder;
import com.science.gtnl.common.block.blocks.tile.TileEntityMEChisel;
import com.science.gtnl.common.block.blocks.tile.TileEntitySuperDualInterface;
import com.science.gtnl.common.block.blocks.tile.TileEntitySuperInterface;
import com.science.gtnl.common.celestweave.CelestweaveBackpack;
import com.science.gtnl.common.gui.container.ContainerActiveFormationPlane;
import com.science.gtnl.common.gui.container.ContainerCustomPriority;
import com.science.gtnl.common.gui.container.ContainerDirePatternEncoder;
import com.science.gtnl.common.gui.container.ContainerMECellDock;
import com.science.gtnl.common.gui.container.ContainerMEChisel;
import com.science.gtnl.common.gui.container.ContainerSuperDualInterfaceFluid;
import com.science.gtnl.common.gui.container.ContainerSuperInterface;
import com.science.gtnl.common.gui.container.ContainerUniversalBusIO;
import com.science.gtnl.common.gui.container.ContainerUniversalStorageBus;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableAdvancedWorkbench;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableAnvil;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableBasicWorkbench;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableChest;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableCompressedChest;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableEnchanting;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableEnderChest;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableFurnace;
import com.science.gtnl.common.gui.container.portableWorkbench.ContainerPortableInfinityChest;
import com.science.gtnl.common.gui.container.portableWorkbench.PortableCellWorkbenchContainer;
import com.science.gtnl.common.gui.portableWorkbench.PortableCellWorkbenchGuiFactory;
import com.science.gtnl.common.item.cellworkbench.PortableCellWorkbenchHost;
import com.science.gtnl.common.item.items.PortableChestType;
import com.science.gtnl.common.part.PartActiveFormationPlane;
import com.science.gtnl.common.part.PartMECellDock;
import com.science.gtnl.common.part.PartSuperDualInterface;
import com.science.gtnl.common.part.PartSuperInterface;
import com.science.gtnl.common.part.PartUniversalExportBus;
import com.science.gtnl.common.part.PartUniversalImportBus;
import com.science.gtnl.common.part.PartUniversalStorageBus;
import com.science.gtnl.common.part.universal.UniversalBusPrimaryGui;
import com.science.gtnl.common.wireless.remote.GTNLRemoteDeviceGui;
import com.science.gtnl.loader.ItemLoader;
import com.science.gtnl.utils.enums.GTNLItemList;
import com.science.gtnl.utils.enums.GuiType;

import appeng.api.config.Upgrades;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerOreFilter;
import appeng.helpers.IOreFilterable;
import appeng.helpers.IPriorityHost;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.automation.PartUpgradeable;

public class GTNLGuiHandler {

    public static Object serverElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        ForgeDirection side = ForgeDirection.getOrientation(ID & 7);
        GuiType type = GuiType.getGuiType(ID >> 3);
        return switch (type) {
            case DetravScannerGUI -> null;
            case PortableBasicWorkBenchGUI -> new ContainerPortableBasicWorkbench(player, world, player.getHeldItem());
            case PortableAdvancedWorkBenchGUI -> new ContainerPortableAdvancedWorkbench(
                player.inventory,
                player.worldObj,
                player.getHeldItem());
            case PortableFurnaceGUI -> new ContainerPortableFurnace(
                player.inventory,
                player.worldObj,
                player.getHeldItem());
            case PortableAnvilGUI -> new ContainerPortableAnvil(player.inventory, player);
            case PortableEnderChestGUI -> new ContainerPortableEnderChest(
                player.inventory,
                player.getInventoryEnderChest());
            case PortableEnchantingGUI -> new ContainerPortableEnchanting(player.inventory, world);
            case PortableCompressedChestGUI -> new ContainerPortableCompressedChest(
                player.getHeldItem(),
                player.inventory);
            case PortableInfinityChestGUI -> new ContainerPortableInfinityChest(player.getHeldItem(), player.inventory);
            case PortableCopperChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.COPPER);
            case PortableIronChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.IRON);
            case PortableSilverChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.SILVER);
            case PortableSteelChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.STEEL);
            case PortableGoldenChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.GOLD);
            case PortableDiamondChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.DIAMOND);
            case PortableCrystalChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.CRYSTAL);
            case PortableObsidianChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.OBSIDIAN);
            case PortableNetheriteChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.NETHERITE);
            case PortableDarkSteelChestGUI -> new ContainerPortableChest(
                player.inventory,
                player.getHeldItem(),
                PortableChestType.DARKSTEEL);
            case PortableCellWorkbenchGUI -> createPortableCellWorkbenchContainer(player, x);
            case PortableCellWorkbenchOreFilterGUI -> createPortableCellWorkbenchOreFilter(player, x);
            case PortableCellWorkbenchRestrictionGUI -> createPortableCellWorkbenchRestriction(player, x);
            case CelestweaveBackpackGUI -> CelestweaveBackpack.serverContainer(player);
            default -> tileElement(type, side, player, world.getTileEntity(x, y, z));
        };
    }

    public static Object tileElement(GuiType type, ForgeDirection side, EntityPlayer player, TileEntity t) {
        return switch (type) {
            case DirePatternEncoderGUI -> {
                if (t instanceof TileEntityDirePatternEncoder d) {
                    yield new ContainerDirePatternEncoder(player.inventory, d);
                }
                yield null;
            }
            case MEChiselGUI -> {
                if (t instanceof TileEntityMEChisel d) {
                    yield new ContainerMEChisel(player.inventory, d);
                }
                yield null;
            }
            case SuperInterfaceGUI -> {
                if (t instanceof TileEntitySuperInterface si) {
                    var container = new ContainerSuperInterface(player.inventory, si);
                    container.setOpenContext(openContext(si, t, side));
                    yield container;

                } else if (t instanceof IPartHost host) {
                    IPart part = host.getPart(side);
                    if (part instanceof PartSuperInterface si) {
                        var container = new ContainerSuperInterface(player.inventory, si);
                        container.setOpenContext(openContext(si, t, side));
                        yield container;
                    }
                }
                yield null;
            }
            case SuperDualInterfaceGUI -> {
                if (t instanceof TileEntitySuperDualInterface si) {
                    var container = new ContainerSuperInterface(player.inventory, si);
                    container.setOpenContext(openContext(si, t, side));
                    yield container;

                } else if (t instanceof IPartHost host) {
                    IPart part = host.getPart(side);
                    if (part instanceof PartSuperDualInterface si) {
                        var container = new ContainerSuperInterface(player.inventory, si);
                        container.setOpenContext(openContext(si, t, side));
                        yield container;
                    }
                }
                yield null;
            }
            case SuperDualInterfaceFluidGUI -> {
                if (t instanceof TileEntitySuperDualInterface si) {
                    var container = new ContainerSuperDualInterfaceFluid(player.inventory, si);
                    container.setOpenContext(openContext(si, t, side));
                    yield container;
                } else if (t instanceof IPartHost host) {
                    IPart part = host.getPart(side);
                    if (part instanceof PartSuperDualInterface si) {
                        var container = new ContainerSuperDualInterfaceFluid(player.inventory, si);
                        container.setOpenContext(openContext(si, t, side));
                        yield container;
                    }
                }
                yield null;
            }
            case ActiveFormationPlaneGUI -> {
                if (t instanceof IPartHost host) {
                    IPart part = host.getPart(side);
                    if (part instanceof PartActiveFormationPlane si) {
                        var container = new ContainerActiveFormationPlane(player.inventory, si);
                        container.setOpenContext(openContext(si, t, side));
                        yield container;
                    }
                }
                yield null;
            }
            case UniversalStorageBusGUI -> {
                if (t instanceof IPartHost host && host.getPart(side) instanceof PartUniversalStorageBus bus) {
                    var container = new ContainerUniversalStorageBus(player.inventory, bus);
                    container.setOpenContext(openContext(bus, t, side));
                    yield container;
                }
                yield null;
            }
            case UniversalBusGUI -> {
                if (t instanceof IPartHost host && host.getPart(side) instanceof PartSharedItemBus<?>bus
                    && (bus instanceof PartUniversalImportBus || bus instanceof PartUniversalExportBus)) {
                    var container = new ContainerUniversalBusIO(player.inventory, bus);
                    container.setOpenContext(openContext(bus, t, side));
                    yield container;
                }
                yield null;
            }
            case UniversalOreFilterGUI -> {
                if (t instanceof IPartHost host && host.getPart(side) instanceof IOreFilterable filterable
                    && host.getPart(side) instanceof PartUpgradeable part
                    && part.getInstalledUpgrades(Upgrades.ORE_FILTER) > 0) {
                    final GuiType returnGui;
                    final ItemStack icon;
                    if (part instanceof PartUniversalStorageBus) {
                        returnGui = GuiType.UniversalStorageBusGUI;
                        icon = GTNLItemList.PartUniversalStorageBus.get(1);
                    } else if (part instanceof PartUniversalImportBus) {
                        returnGui = GuiType.UniversalBusGUI;
                        icon = GTNLItemList.PartUniversalImportBus.get(1);
                    } else if (part instanceof PartUniversalExportBus) {
                        returnGui = GuiType.UniversalBusGUI;
                        icon = GTNLItemList.PartUniversalExportBus.get(1);
                    } else {
                        yield null;
                    }
                    var container = new ContainerOreFilter(player.inventory, filterable);
                    container.setOpenContext(openContext(part, t, side));
                    container.setPrimaryGui(new UniversalBusPrimaryGui(returnGui, icon, t, side));
                    yield container;
                }
                yield null;
            }
            case MECellDockGUI -> {
                if (t instanceof IPartHost host) {
                    IPart part = host.getPart(side);
                    if (part instanceof PartMECellDock dock) {
                        var container = new ContainerMECellDock(player.inventory, dock);
                        container.setOpenContext(openContext(dock, t, side));
                        yield container;
                    }
                }
                yield null;
            }
            case CustomPriorityGUI -> {
                IPriorityHost priorityHost = null;
                if (t instanceof TileEntitySuperInterface si) {
                    priorityHost = si;
                } else if (t instanceof TileEntitySuperDualInterface si) {
                    priorityHost = si;
                } else if (t instanceof IPartHost host) {
                    IPart part = host.getPart(side);
                    if (part instanceof PartSuperInterface si) {
                        priorityHost = si;
                    } else if (part instanceof PartSuperDualInterface si) {
                        priorityHost = si;
                    } else if (part instanceof PartActiveFormationPlane si) {
                        priorityHost = si;
                    } else if (part instanceof PartMECellDock dock) {
                        priorityHost = dock;
                    } else if (part instanceof PartUniversalStorageBus bus) {
                        priorityHost = bus;
                    }
                }
                if (priorityHost != null) {
                    var container = new ContainerCustomPriority(player.inventory, priorityHost);
                    container.setOpenContext(openContext(priorityHost, t, side));
                    yield container;
                }
                yield null;
            }
            default -> null;
        };
    }

    private static ContainerOpenContext openContext(Object host, TileEntity tile, ForgeDirection side) {
        ContainerOpenContext context = new ContainerOpenContext(host);
        context.setWorld(tile.getWorldObj());
        context.setX(tile.xCoord);
        context.setY(tile.yCoord);
        context.setZ(tile.zCoord);
        context.setSide(side);
        return context;
    }

    public static void openGui(EntityPlayer player, GuiType type, ForgeDirection side, TileEntity tile) {
        if (tile == null) return;
        openGui(player, type, side, tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord);
    }

    public static void openGui(EntityPlayer player, GuiType type, ForgeDirection side, World world, int x, int y,
        int z) {
        if (player == null || world == null) return;
        if (side == null) side = ForgeDirection.UNKNOWN;
        if (GTNLRemoteDeviceGui.openInSession(player, type, side, world, x, y, z)) return;

        int fullID = (type.getID() << 3) | (side.ordinal() & 7);

        player.openGui(ScienceNotLeisure.instance, fullID, world, x, y, z);
    }

    private static Object createPortableCellWorkbenchContainer(EntityPlayer player, int inventorySlot) {
        if (!isPortableCellWorkbenchSlot(player, inventorySlot)) return null;
        return new PortableCellWorkbenchContainer(
            player.inventory,
            new PortableCellWorkbenchHost(player.inventory, ItemLoader.portableCellWorkbenchItem, inventorySlot));
    }

    private static Object createPortableCellWorkbenchOreFilter(EntityPlayer player, int inventorySlot) {
        PortableCellWorkbenchHost host = createPortableCellWorkbenchHost(player, inventorySlot);
        return host == null ? null : PortableCellWorkbenchGuiFactory.createOreFilter(player.inventory, host);
    }

    private static Object createPortableCellWorkbenchRestriction(EntityPlayer player, int inventorySlot) {
        PortableCellWorkbenchHost host = createPortableCellWorkbenchHost(player, inventorySlot);
        return host == null ? null : PortableCellWorkbenchGuiFactory.createCellRestriction(player.inventory, host);
    }

    private static PortableCellWorkbenchHost createPortableCellWorkbenchHost(EntityPlayer player, int inventorySlot) {
        if (!isPortableCellWorkbenchSlot(player, inventorySlot)) return null;
        return new PortableCellWorkbenchHost(player.inventory, ItemLoader.portableCellWorkbenchItem, inventorySlot);
    }

    private static boolean isPortableCellWorkbenchSlot(EntityPlayer player, int inventorySlot) {
        return inventorySlot >= 0 && inventorySlot < player.inventory.getSizeInventory()
            && player.inventory.getStackInSlot(inventorySlot) != null
            && player.inventory.getStackInSlot(inventorySlot)
                .getItem() == ItemLoader.portableCellWorkbenchItem;
    }
}
