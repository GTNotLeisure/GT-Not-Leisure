package com.science.gtnl.common.me;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import net.minecraftforge.oredict.OreDictionary.OreRegisterEvent;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.prioitylist.IPartitionList;
import appeng.util.prioitylist.OreFilteredList;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@EventBusSubscriber
public class OreDictionaryFilter implements IPartitionList<IAEItemStack>, Predicate<IAEItemStack> {

    private static final AtomicLong REGISTRATION_VERSION = new AtomicLong();

    private final String expression;
    private volatile Compiled compiled;

    public OreDictionaryFilter(String expression) {
        this.expression = expression;
        this.compiled = compile(expression);
    }

    public String expression() {
        return expression;
    }

    public static Predicate<IAEItemStack> makeFilter(String expression) {
        OreDictionaryFilter filter = new OreDictionaryFilter(expression);
        return filter.isEmpty() ? null : filter;
    }

    @SubscribeEvent
    public static void onOreRegistration(OreRegisterEvent event) {
        REGISTRATION_VERSION.incrementAndGet();
    }

    @Override
    public boolean test(IAEItemStack stack) {
        Predicate<IAEItemStack> predicate = current().predicate();
        return predicate != null && predicate.test(stack);
    }

    @Override
    public boolean isListed(IAEItemStack stack) {
        return test(stack);
    }

    @Override
    public boolean isEmpty() {
        return current().predicate() == null;
    }

    @Override
    public Iterable<IAEItemStack> getItems() {
        return null;
    }

    public Compiled current() {
        Compiled current = compiled;
        if (current.registrationVersion() != REGISTRATION_VERSION.get()) {
            current = compile(expression);
            compiled = current;
        }
        return current;
    }

    public static Compiled compile(String expression) {
        long version = REGISTRATION_VERSION.get();
        return new Compiled(version, OreFilteredList.makeFilter(expression));
    }

    public record Compiled(long registrationVersion, Predicate<IAEItemStack> predicate) {}
}
