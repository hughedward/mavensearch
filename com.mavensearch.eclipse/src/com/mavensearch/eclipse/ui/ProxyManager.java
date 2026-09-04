package com.mavensearch.eclipse.ui;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;

/** Eclipse IProxyService → java.net.ProxySelector 桥。每次 select 现查配置，代理变更即时生效。 */
public final class ProxyManager extends ProxySelector {

    private final IProxyService service;

    public ProxyManager(IProxyService service) {
        this.service = service;
    }

    @Override
    public List<Proxy> select(URI uri) {
        List<Proxy> out = new ArrayList<>();
        try {
            for (IProxyData data : service.select(uri)) {
                if (data == null || data.getHost() == null) {
                    continue;
                }
                Proxy.Type type = IProxyData.SOCKS_PROXY_TYPE.equals(data.getType())
                        ? Proxy.Type.SOCKS
                        : Proxy.Type.HTTP;
                int port = data.getPort() == -1 ? 443 : data.getPort();
                out.add(new Proxy(type, new InetSocketAddress(data.getHost(), port)));
            }
        } catch (RuntimeException e) {
            // 代理服务异常 → 直连
        }
        if (out.isEmpty()) {
            out.add(Proxy.NO_PROXY);
        }
        return out;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        // 无操作：HttpClient 自动重试其他代理/直连
    }
}
