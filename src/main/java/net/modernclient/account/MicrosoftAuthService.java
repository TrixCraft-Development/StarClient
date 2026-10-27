package net.modernclient.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class MicrosoftAuthService {

    private static final String[] CLIENT_IDS = {
            "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb",
            "54fd49e4-2103-4044-9603-2b028c814ec3",
            "00000000402b5328"
    };

    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private MicrosoftAuthService() {
    }

    public record DeviceCodeSession(
            String clientId,
            String deviceCode,
            String userCode,
            String verificationUri,
            int expiresIn,
            int intervalSeconds
    ) {
    }

    public static DeviceCodeSession requestDeviceCode() throws Exception {
        Exception lastError = null;
        for (String clientId : CLIENT_IDS) {
            try {
                String form = "client_id=" + urlEncode(clientId)
                        + "&scope=" + urlEncode("XboxLive.signin offline_access");
                HttpRequest request = HttpRequest.newBuilder(URI.create(DEVICE_CODE_URL))
                        .timeout(Duration.ofSeconds(12))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();

                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (response.statusCode() == 200 && json.has("device_code") && json.has("user_code")) {
                    String deviceCode = json.get("device_code").getAsString();
                    String userCode = json.get("user_code").getAsString();
                    String verificationUri = json.has("verification_uri")
                            ? json.get("verification_uri").getAsString()
                            : "https://www.microsoft.com/link";
                    int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 900;
                    int interval = json.has("interval") ? Math.max(3, json.get("interval").getAsInt()) : 5;
                    return new DeviceCodeSession(clientId, deviceCode, userCode, verificationUri, expiresIn, interval);
                }
                String errDesc = json.has("error_description") ? json.get("error_description").getAsString() : response.body();
                lastError = new IOException("Device code request failed (" + response.statusCode() + "): " + errDesc);
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("Unable to start Microsoft Device Code flow.");
    }

    public static MinecraftAccount pollAndCompleteLogin(
            DeviceCodeSession session,
            BooleanSupplier cancelled,
            Consumer<String> statusCallback
    ) throws Exception {
        long deadline = System.currentTimeMillis() + session.expiresIn() * 1000L;
        long sleepMs = session.intervalSeconds() * 1000L;

        while (System.currentTimeMillis() < deadline) {
            if (cancelled.getAsBoolean()) {
                throw new InterruptedException("Login cancelled.");
            }
            Thread.sleep(sleepMs);
            if (cancelled.getAsBoolean()) {
                throw new InterruptedException("Login cancelled.");
            }

            String form = "grant_type=" + urlEncode("urn:ietf:params:oauth:grant-type:device_code")
                    + "&client_id=" + urlEncode(session.clientId())
                    + "&device_code=" + urlEncode(session.deviceCode());

            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            if (response.statusCode() == 200 && json.has("access_token")) {
                String msAccessToken = json.get("access_token").getAsString();
                String msRefreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : "";
                return completeXboxAndMinecraftChain(msAccessToken, msRefreshToken, session.clientId(), statusCallback);
            }

            String error = json.has("error") ? json.get("error").getAsString() : "";
            switch (error) {
                case "authorization_pending" -> {
                }
                case "slow_down" -> sleepMs += 2000L;
                case "expired_token" ->
                        throw new IOException("Code expired. Please click Retry to generate a new code.");
                case null, default -> {
                    String desc = json.has("error_description") ? json.get("error_description").getAsString() : error;
                    throw new IOException("Microsoft login error: " + desc);
                }
            }
        }
        throw new IOException("Login timed out.");
    }

    public static MinecraftAccount refreshAccount(MinecraftAccount account, Consumer<String> statusCallback) throws Exception {
        String refreshToken = account.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return authenticateWithMinecraftToken(account.getAccessToken(), statusCallback);
        }
        String clientId = account.getClientId() != null && !account.getClientId().isBlank()
                ? account.getClientId()
                : CLIENT_IDS[0];

        if (statusCallback != null) {
            statusCallback.accept("Refreshing Microsoft token...");
        }
        String form = "client_id=" + urlEncode(clientId)
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&grant_type=refresh_token"
                + "&scope=" + urlEncode("XboxLive.signin offline_access");

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() != 200 || !json.has("access_token")) {
            throw new IOException("Failed to refresh Microsoft token (" + response.statusCode() + ")");
        }

        String msAccessToken = json.get("access_token").getAsString();
        String newRefreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;
        return completeXboxAndMinecraftChain(msAccessToken, newRefreshToken, clientId, statusCallback);
    }

    public static MinecraftAccount authenticateWithMinecraftToken(
            String mcAccessToken,
            Consumer<String> statusCallback
    ) throws Exception {
        if (statusCallback != null) {
            statusCallback.accept("Fetching Minecraft profile...");
        }
        ProfileInfo profile = fetchMinecraftProfile(mcAccessToken.trim());
        return MinecraftAccount.microsoft(profile.username(), profile.uuid(), mcAccessToken.trim(), "", "");
    }

    private static MinecraftAccount completeXboxAndMinecraftChain(
            String msAccessToken,
            String msRefreshToken,
            String clientId,
            Consumer<String> statusCallback
    ) throws Exception {
        if (statusCallback != null) {
            statusCallback.accept("Authenticating with Xbox Live...");
        }
        XblToken xbl = authenticateXboxLive(msAccessToken);

        if (statusCallback != null) {
            statusCallback.accept("Authorizing XSTS token...");
        }
        XblToken xsts = authorizeXsts(xbl.token());

        if (statusCallback != null) {
            statusCallback.accept("Logging into Minecraft Services...");
        }
        String mcAccessToken = loginWithXbox(xsts.uhs(), xsts.token());

        if (statusCallback != null) {
            statusCallback.accept("Fetching Minecraft profile...");
        }
        ProfileInfo profile = fetchMinecraftProfile(mcAccessToken);

        return MinecraftAccount.microsoft(
                profile.username(),
                profile.uuid(),
                mcAccessToken,
                msRefreshToken,
                clientId
        );
    }

    private record XblToken(String token, String uhs) {
    }

    private record ProfileInfo(UUID uuid, String username) {
    }

    private static XblToken authenticateXboxLive(String msAccessToken) throws Exception {
        HttpResponse<String> response = sendXblRequest("d=" + msAccessToken);
        if (response.statusCode() != 200) {
            response = sendXblRequest(msAccessToken);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Xbox Live authentication failed (HTTP " + response.statusCode() + ")");
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String token = json.get("Token").getAsString();
        String uhs = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui")
                .get(0).getAsJsonObject()
                .get("uhs").getAsString();
        return new XblToken(token, uhs);
    }

    private static HttpResponse<String> sendXblRequest(String rpsTicket) throws Exception {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", rpsTicket);

        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "https://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder(URI.create(XBL_AUTH_URL))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static XblToken authorizeXsts(String xblToken) throws Exception {
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);

        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        props.add("UserTokens", userTokens);

        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder(URI.create(XSTS_AUTH_URL))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("XSTS authorization failed (HTTP " + response.statusCode() + ")");
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String token = json.get("Token").getAsString();
        String uhs = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui")
                .get(0).getAsJsonObject()
                .get("uhs").getAsString();
        return new XblToken(token, uhs);
    }

    private static String loginWithXbox(String uhs, String xstsToken) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        HttpRequest request = HttpRequest.newBuilder(URI.create(MC_LOGIN_URL))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Minecraft Xbox login failed (HTTP " + response.statusCode() + ")");
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("access_token")) {
            throw new IOException("No Minecraft access_token returned.");
        }
        return json.get("access_token").getAsString();
    }

    private static ProfileInfo fetchMinecraftProfile(String mcAccessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MC_PROFILE_URL))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + mcAccessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Account does not own Minecraft Java Edition or token is invalid (HTTP " + response.statusCode() + ")");
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String rawId = json.get("id").getAsString();
        String name = json.get("name").getAsString();
        UUID uuid = parseUndashedUuid(rawId);
        return new ProfileInfo(uuid, name);
    }

    public static UUID parseUndashedUuid(String raw) {
        String clean = raw.trim().replace("-", "");
        if (clean.length() != 32) {
            return UUID.fromString(raw.trim());
        }
        String dashed = clean.substring(0, 8) + "-"
                + clean.substring(8, 12) + "-"
                + clean.substring(12, 16) + "-"
                + clean.substring(16, 20) + "-"
                + clean.substring(20, 32);
        return UUID.fromString(dashed);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
