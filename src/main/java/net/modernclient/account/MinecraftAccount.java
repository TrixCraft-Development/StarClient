package net.modernclient.account;

import java.util.Objects;
import java.util.UUID;

public final class MinecraftAccount {

    public enum AccountType {
        MICROSOFT,
        OFFLINE
    }

    private final AccountType type;
    private String username;
    private UUID uuid;
    private String accessToken;
    private String refreshToken;
    private String clientId;
    private final boolean initialSession;

    public MinecraftAccount(
            AccountType type,
            String username,
            UUID uuid,
            String accessToken,
            String refreshToken,
            String clientId,
            boolean initialSession
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.username = Objects.requireNonNull(username, "username");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.accessToken = accessToken == null ? "" : accessToken;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        this.clientId = clientId == null ? "" : clientId;
        this.initialSession = initialSession;
    }

    public static MinecraftAccount offline(String username, UUID uuid) {
        return new MinecraftAccount(AccountType.OFFLINE, username, uuid, "", "", "", false);
    }

    public static MinecraftAccount microsoft(
            String username,
            UUID uuid,
            String accessToken,
            String refreshToken,
            String clientId
    ) {
        return new MinecraftAccount(AccountType.MICROSOFT, username, uuid, accessToken, refreshToken, clientId, false);
    }

    public AccountType getType() {
        return type;
    }

    public boolean isMicrosoft() {
        return type == AccountType.MICROSOFT;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken == null ? "" : refreshToken;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId == null ? "" : clientId;
    }

    public boolean isInitialSession() {
        return initialSession;
    }
}
