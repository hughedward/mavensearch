package com.mavensearch.eclipse.ui;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.mavensearch.eclipse.client.DepsDevClient;
import com.mavensearch.eclipse.client.HttpSupport;
import com.mavensearch.eclipse.client.MavenCentralClient;
import com.mavensearch.eclipse.client.MavenMetadataClient;
import com.mavensearch.eclipse.index.IndexSearcher;
import com.mavensearch.eclipse.index.IndexUpdater;
import com.mavensearch.eclipse.service.SearchService;
import com.mavensearch.eclipse.service.VersionService;

/** 插件单例：持有 core 服务与索引状态。 */
public final class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.mavensearch.eclipse";
    public static final String INDEX_URL =
            "https://hughedward.github.io/mavensearch/index/maven-central-index.txt.gz";

    private static Activator plugin;

    private HttpSupport http;
    private SearchService searchService;
    private VersionService versionService;
    private IndexUpdater indexUpdater;
    private final AtomicReference<IndexSearcher> searcherRef = new AtomicReference<>();
    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        ServiceReference<IProxyService> ref = context.getServiceReference(IProxyService.class);
        IProxyService proxies = ref == null ? null : context.getService(ref);
        http = HttpSupport.create(proxies == null ? null : new ProxyManager(proxies));
        indexUpdater = new IndexUpdater(INDEX_URL, getStateDir(), http);
        searchService = new SearchService(searcherRef::get, new MavenCentralClient(http, null));
        versionService = new VersionService(new MavenMetadataClient(http, null), new DepsDevClient(http, null));
        // 启动 0.5s 后台加载本地索引（不阻塞启动）
        Job.create("Load Maven Search index", m -> {
            indexUpdater.loadLocal().ifPresent(store -> {
                searcherRef.set(new IndexSearcher(store));
                indexReady.set(true);
            });
            m.done();
        }).schedule(500);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (versionService != null) {
            versionService.shutdown();
        }
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }

    public SearchService getSearchService() {
        return searchService;
    }

    public VersionService getVersionService() {
        return versionService;
    }

    public IndexUpdater getIndexUpdater() {
        return indexUpdater;
    }

    public Path getStateDir() {
        return getStateLocation().toFile().toPath();
    }

    public AtomicReference<IndexSearcher> searcherRef() {
        return searcherRef;
    }

    public boolean isIndexReady() {
        return indexReady.get();
    }

    /** IndexUpdater/IndexStartup 下载成功后回填。 */
    public void indexLoaded(IndexSearcher searcher) {
        searcherRef.set(searcher);
        indexReady.set(true);
    }
}
