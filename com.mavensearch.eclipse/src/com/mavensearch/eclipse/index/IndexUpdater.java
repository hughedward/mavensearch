package com.mavensearch.eclipse.index;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.mavensearch.eclipse.client.HttpSupport;

/**
 * 索引的本地持久化与远端更新。条件请求（ETag 优先）→ 304 零流量；下载先校验再原子替换，
 * 失败保留旧索引。state location 下两个文件：索引本体 + .meta（ETag/Last-Modified sidecar）。
 */
public final class IndexUpdater {

    private static final String FILE_NAME = "maven-central-index.txt.gz";
    private static final String META_NAME = FILE_NAME + ".meta";
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(120);

    private final String indexUrl;
    private final Path stateDir;
    private final HttpSupport http;
    private volatile String lastError;

    public IndexUpdater(String indexUrl, Path stateDir, HttpSupport http) {
        this.indexUrl = indexUrl;
        this.stateDir = stateDir;
        this.http = http;
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    /** 启动加载。损坏即删除文件并返回 empty。 */
    public Optional<IndexStore> loadLocal() {
        Path file = stateDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(IndexStore.load(file));
        } catch (IOException e) {
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(stateDir.resolve(META_NAME));
            } catch (IOException ignored) {
                // 删不掉就下次再删
            }
            return Optional.empty();
        }
    }

    /** 检查远端并按需替换。有更新且替换成功返回 true；304/失败返回 false。 */
    public boolean refresh() {
        lastError = null;
        String etag = null;
        long lastModified = -1;
        List<String> meta;
        try {
            meta = Files.readAllLines(stateDir.resolve(META_NAME), StandardCharsets.UTF_8);
            if (meta.size() >= 2) {
                etag = "-".equals(meta.get(0)) ? null : meta.get(0);
                lastModified = Long.parseLong(meta.get(1));
            }
        } catch (IOException | RuntimeException e) {
            // 无 sidecar：全量下载
        }
        Path tmp = null;
        try {
            HttpSupport.ConditionalResponse r = http.get(indexUrl, DOWNLOAD_TIMEOUT, etag, lastModified);
            if (r.notModified()) {
                return false;
            }
            if (r.status() != 200) {
                lastError = "HTTP " + r.status();
                return false;
            }
            tmp = Files.createTempFile(stateDir, "index-", ".tmp");
            Files.write(tmp, r.body());
            IndexStore candidate = IndexStore.load(tmp); // 校验，损坏抛 IOException
            if (candidate.count() == 0) {
                Files.deleteIfExists(tmp);
                lastError = "empty index";
                return false;
            }
            atomicMove(tmp, stateDir.resolve(FILE_NAME));
            Files.writeString(stateDir.resolve(META_NAME),
                    (r.etag() == null ? "-" : r.etag()) + "\n" + Math.max(r.lastModifiedMillis(), 0) + "\n",
                    StandardCharsets.UTF_8);
            return true;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // tmp 清理失败无碍——loadLocal 只认 FILE_NAME
                }
            }
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return false;
        }
    }

    private static void atomicMove(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
