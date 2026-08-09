package fr.lordfinn.crazyphone.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import net.minecraft.Util;

import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a Minecraft username to that account's REAL GameProfile (real UUID) via Mojang's public
 * profile-lookup API - the exact same GameProfileRepository mechanism vanilla itself uses server-side for
 * things like {@code /whitelist add <name>}. This needs no authentication at all: an account's skin is
 * public data, resolvable from just its username, entirely independent of whatever account (or lack of
 * one - an offline/cracked connection) the local client is actually logged in as. This is what lets a
 * bust portrait show a player's real skin even when testing two offline dev identities against a local
 * cracked server (see CrazyPhoneInCallScreenScreen).
 */
public final class MojangProfileLookup {
    private static GameProfileRepository repository;
    private static final Map<String, CompletableFuture<GameProfile>> CACHE = new ConcurrentHashMap<>();

    private MojangProfileLookup() {
    }

    private static synchronized GameProfileRepository repository() {
        if (repository == null)
            repository = new YggdrasilAuthenticationService(Proxy.NO_PROXY).createProfileRepository();
        return repository;
    }

    /** Cached per username for the client's lifetime - a real account's UUID doesn't change mid-session,
     * and this avoids re-hitting Mojang's API every time the same participant's bust is rebuilt (e.g.
     * reopening the InCall screen). Resolves to {@code null} (never fails the future) if the name doesn't
     * match a real account or the lookup couldn't reach Mojang - callers fall back to a synthetic profile
     * in that case, same as before this existed. */
    public static CompletableFuture<GameProfile> lookup(String name) {
        return CACHE.computeIfAbsent(name, n -> CompletableFuture.supplyAsync(() -> {
            GameProfile[] result = new GameProfile[1];
            repository().findProfilesByNames(new String[]{n}, new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(GameProfile profile) {
                    result[0] = profile;
                }

                @Override
                public void onProfileLookupFailed(String failedName, Exception exception) {
                    // Not a real account, or Mojang's API is unreachable - result stays null, handled by
                    // the caller falling back to a synthetic (default-skin) profile.
                }
            });
            return result[0];
        }, Util.backgroundExecutor()));
    }
}
