package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;


@Mixin(KeyMapping.class)
public class MixinKeyMappingDispatch {

    private static final ThreadLocal<List<ClickSnapshot>> newvisualkeybing$pendingClickRestore =
            ThreadLocal.withInitial(List::of);

    @Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onClick(InputConstants.Key key, CallbackInfo ci) {
        List<KeybindComboStore.Match> matches = KeybindComboStore.global().triggerMatches(key);
        boolean handled = false;
        for (KeybindComboStore.Match match : matches) {
            if (match.active()) {
                newvisualkeybing$incrementClick(match.mapping());
                handled = true;
            }
        }
        if (handled) {
            newvisualkeybing$pendingClickRestore.remove();
            ci.cancel();
        } else {
            newvisualkeybing$pendingClickRestore.set(newvisualkeybing$snapshotInactive(matches));
        }
    }

    @Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",
            at = @At("TAIL"))
    private static void newvisualkeybing$afterClick(InputConstants.Key key, CallbackInfo ci) {
        List<ClickSnapshot> snapshots = newvisualkeybing$pendingClickRestore.get();
        newvisualkeybing$pendingClickRestore.remove();
        for (ClickSnapshot snapshot : snapshots) {
            ((KeyMappingAccessor) (Object) snapshot.mapping())
                    .newvisualkeybing$setClickCount(snapshot.clickCount());
        }
    }

    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onSet(InputConstants.Key key, boolean held, CallbackInfo ci) {
        List<KeybindComboStore.Match> matches = KeybindComboStore.global().triggerMatches(key);
        boolean handled = false;
        for (KeybindComboStore.Match match : matches) {
            match.mapping().setDown(held && match.active());
            handled |= held && match.active();
        }
        if (handled) {
            ci.cancel();
        }
    }

    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            at = @At("TAIL"))
    private static void newvisualkeybing$afterSet(InputConstants.Key key, boolean held, CallbackInfo ci) {
        KeybindComboStore.global().syncComboStates();
    }

    private static void newvisualkeybing$incrementClick(KeyMapping mapping) {
        if (mapping == null) return;
        KeyMappingAccessor accessor = (KeyMappingAccessor) (Object) mapping;
        accessor.newvisualkeybing$setClickCount(accessor.newvisualkeybing$getClickCount() + 1);
    }

    private static List<ClickSnapshot> newvisualkeybing$snapshotInactive(List<KeybindComboStore.Match> matches) {
        if (matches.isEmpty()) return List.of();
        java.util.ArrayList<ClickSnapshot> snapshots = new java.util.ArrayList<>();
        for (KeybindComboStore.Match match : matches) {
            if (match.active()) continue;
            KeyMappingAccessor accessor = (KeyMappingAccessor) (Object) match.mapping();
            snapshots.add(new ClickSnapshot(match.mapping(), accessor.newvisualkeybing$getClickCount()));
        }
        return snapshots;
    }

    private record ClickSnapshot(KeyMapping mapping, int clickCount) {}
}
