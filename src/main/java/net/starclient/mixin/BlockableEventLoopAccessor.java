package net.starclient.mixin;

import net.minecraft.CrashReport;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Supplier;

@Mixin(BlockableEventLoop.class)
public interface BlockableEventLoopAccessor {

    @Accessor("delayedCrash")
    static void starclient$setDelayedCrash(Supplier<CrashReport> delayedCrash) {
        throw new AssertionError();
    }

    @Accessor("delayedCrash")
    static Supplier<CrashReport> starclient$getDelayedCrash() {
        throw new AssertionError();
    }

    @Invoker("dropAllTasks")
    void starclient$dropAllTasks();
}
