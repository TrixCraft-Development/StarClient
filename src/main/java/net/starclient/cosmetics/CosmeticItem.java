package net.starclient.cosmetics;

import net.minecraft.resources.Identifier;
import net.starclient.rank.Rank;

public record CosmeticItem(
        String id,
        String displayName,
        CosmeticType type,
        Rank minRank,
        boolean isAnimated,
        String description,
        String icon,
        Identifier textureId,
        Identifier assetId,
        boolean isBuiltIn
) {
    public boolean requiresPremium() {
        return (minRank != null && minRank.hasAtLeast(Rank.PREMIUM)) || isAnimated;
    }
}
