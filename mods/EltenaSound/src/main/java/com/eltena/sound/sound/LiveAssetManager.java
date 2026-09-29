package com.eltena.sound.sound;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

final class LiveAssetManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEBUG_PROPERTY = "eltenasound.debug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";

    private final Map<String, LiveAssetManifestEntry> manifests = new LinkedHashMap<>();
    private final Map<String, TransferSession> transfers = new HashMap<>();
    private final Map<String, PlayLiveAssetPayload> pendingPlays = new HashMap<>();
    private final List<ExternalLiveAudioInstance> activeAudio = new ArrayList<>();
    private final Path cacheDir = FMLPaths.CONFIGDIR.get().resolve("eltenasound/live-assets/cache");
    private final Path tempDir = cacheDir.resolve("_incoming");

    void reset() {
        manifests.clear();
        pendingPlays.clear();
        for (TransferSession session : transfers.values()) {
            deleteIfExists(session.tempFilePath());
        }
        transfers.clear();
        for (ExternalLiveAudioInstance audio : activeAudio) {
            audio.stopNow();
        }
        activeAudio.clear();
    }

    void tick(Minecraft minecraft) {
        if (minecraft == null) {
            reset();
            return;
        }
        Iterator<ExternalLiveAudioInstance> iterator = activeAudio.iterator();
        while (iterator.hasNext()) {
            ExternalLiveAudioInstance audio = iterator.next();
            if (audio.tick()) {
                iterator.remove();
            }
        }
    }

    void applyManifest(List<LiveAssetManifestEntry> assets) {
        manifests.clear();
        for (LiveAssetManifestEntry entry : assets) {
            manifests.put(entry.id(), entry);
        }
    }

    void queuePlay(Minecraft minecraft, PlayLiveAssetPayload payload) {
        if (payload == null || payload.assetId() == null || payload.assetId().isBlank()) {
            return;
        }
        LiveAssetManifestEntry entry = asManifestEntry(payload);
        manifests.put(entry.id(), entry);
        if (isCachedAndValid(entry)) {
            playNow(minecraft, payload, entry);
            return;
        }
        pendingPlays.put(payload.assetId(), payload);
    }

    void beginTransfer(LiveAssetTransferStartPayload payload) {
        if (payload == null || payload.assetId() == null || payload.assetId().isBlank()) {
            return;
        }
        try {
            Files.createDirectories(tempDir);
            Files.createDirectories(cacheDir);
            LiveAssetManifestEntry entry = new LiveAssetManifestEntry(
                payload.assetId(),
                payload.assetType(),
                payload.version(),
                payload.sha256(),
                payload.fileName(),
                payload.sizeBytes(),
                payload.category()
            );
            manifests.put(entry.id(), entry);
            Path tempFile = tempDir.resolve(cacheFileName(entry) + ".part");
            deleteIfExists(tempFile);
            Files.createFile(tempFile);
            transfers.put(
                payload.assetId(),
                new TransferSession(entry, tempFile, payload.totalChunks(), payload.chunkSize(), 0)
            );
        } catch (IOException exception) {
            transfers.remove(payload.assetId());
        }
    }

    void acceptTransferChunk(LiveAssetTransferChunkPayload payload) {
        if (payload == null) {
            return;
        }
        TransferSession session = transfers.get(payload.assetId());
        if (session == null) {
            return;
        }
        if (payload.index() != session.receivedChunks()) {
            deleteIfExists(session.tempFilePath());
            transfers.remove(payload.assetId());
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(payload.dataBase64());
            Files.write(session.tempFilePath(), decoded, java.nio.file.StandardOpenOption.APPEND);
            transfers.put(
                payload.assetId(),
                session.withReceivedChunks(session.receivedChunks() + 1)
            );
        } catch (IOException | IllegalArgumentException exception) {
            deleteIfExists(session.tempFilePath());
            transfers.remove(payload.assetId());
        }
    }

    void completeTransfer(Minecraft minecraft, LiveAssetTransferCompletePayload payload) {
        if (payload == null) {
            return;
        }
        TransferSession session = transfers.remove(payload.assetId());
        if (session == null) {
            return;
        }
        if (session.receivedChunks() != session.totalChunks()) {
            deleteIfExists(session.tempFilePath());
            return;
        }
        String actualSha256 = sha256(session.tempFilePath());
        if (!session.entry().sha256().equalsIgnoreCase(actualSha256)) {
            if (isDebugEnabled()) {
                LOGGER.warn(translate("log.eltenasound.live_sha_mismatch", session.entry().id()));
            }
            deleteIfExists(session.tempFilePath());
            return;
        }
        try {
            Path finalPath = cachePath(session.entry());
            Files.createDirectories(finalPath.getParent());
            Files.move(session.tempFilePath(), finalPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            deleteIfExists(session.tempFilePath());
            return;
        }

        PlayLiveAssetPayload pending = pendingPlays.remove(session.entry().id());
        if (pending != null) {
            playNow(minecraft, pending, session.entry());
        }
    }

    void stopCategory(ClientSoundCategory category) {
        Iterator<ExternalLiveAudioInstance> iterator = activeAudio.iterator();
        while (iterator.hasNext()) {
            ExternalLiveAudioInstance audio = iterator.next();
            if (audio.category() != category) {
                continue;
            }
            audio.stopNow();
            iterator.remove();
        }
    }

    Path cacheDir() {
        return cacheDir;
    }

    private void playNow(
        Minecraft minecraft,
        PlayLiveAssetPayload payload,
        LiveAssetManifestEntry entry
    ) {
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException exception) {
            return;
        }

        Path path = cachePath(entry);
        if (!Files.isRegularFile(path)) {
            pendingPlays.put(payload.assetId(), payload);
            return;
        }

        ExternalLiveAudioInstance audio = ExternalLiveAudioInstance.start(
            minecraft,
            payload.assetId(),
            payload.category(),
            path,
            (float) Math.max(0.0D, payload.volume()),
            (float) Math.max(0.01D, payload.pitch())
        );
        if (audio == null) {
            return;
        }
        activeAudio.add(audio);
        if (isDebugEnabled()) {
            LOGGER.info(translate("log.eltenasound.play_live_asset", payload.assetId(), payload.category().name()));
        }
    }

    private boolean isCachedAndValid(LiveAssetManifestEntry entry) {
        Path path = cachePath(entry);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        return entry.sha256().equalsIgnoreCase(sha256(path));
    }

    private Path cachePath(LiveAssetManifestEntry entry) {
        return cacheDir.resolve(cacheFileName(entry));
    }

    private String cacheFileName(LiveAssetManifestEntry entry) {
        return sanitize(entry.id()) + "-v" + entry.version() + "-" + sanitize(entry.fileName());
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "asset";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private String sha256(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException exception) {
            return "";
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private LiveAssetManifestEntry asManifestEntry(PlayLiveAssetPayload payload) {
        return new LiveAssetManifestEntry(
            payload.assetId(),
            payload.assetType(),
            payload.version(),
            payload.sha256(),
            payload.fileName(),
            0L,
            payload.category()
        );
    }

    private boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }

    private String translate(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private record TransferSession(
        LiveAssetManifestEntry entry,
        Path tempFilePath,
        int totalChunks,
        int chunkSize,
        int receivedChunks
    ) {
        private TransferSession withReceivedChunks(int nextReceivedChunks) {
            return new TransferSession(entry, tempFilePath, totalChunks, chunkSize, nextReceivedChunks);
        }
    }
}
