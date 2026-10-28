package net.starclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    void setRightClickDelay(int rightClickDelay);

    @Mutable
    @Accessor("user")
    void starclient$setUser(User user);

    @Mutable
    @Accessor("profileFuture")
    void starclient$setProfileFuture(CompletableFuture<ProfileResult> profileFuture);

    @Mutable
    @Accessor("userApiService")
    void starclient$setUserApiService(UserApiService userApiService);

    @Mutable
    @Accessor("userPropertiesFuture")
    void starclient$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> userPropertiesFuture);

    @Mutable
    @Accessor("profileKeyPairManager")
    void starclient$setProfileKeyPairManager(ProfileKeyPairManager profileKeyPairManager);
}