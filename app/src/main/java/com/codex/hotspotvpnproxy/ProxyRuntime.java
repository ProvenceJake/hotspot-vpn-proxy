package com.codex.hotspotvpnproxy;

import com.codex.hotspotvpnproxy.proxy.HttpProxyServer;
import com.codex.hotspotvpnproxy.proxy.Socks5ProxyServer;

import java.io.IOException;

public final class ProxyRuntime {
    private static final ProxyRuntime INSTANCE = new ProxyRuntime();

    private final Object lock = new Object();
    private final TrafficCounter httpCounter = new TrafficCounter();
    private final TrafficCounter socksCounter = new TrafficCounter();

    private HttpProxyServer httpServer;
    private Socks5ProxyServer socksServer;
    private ProxyConfig config;
    private String lastError = "";

    private ProxyRuntime() {
    }

    public static ProxyRuntime get() {
        return INSTANCE;
    }

    public void start(ProxyConfig newConfig) throws IOException {
        synchronized (lock) {
            stopLocked();
            config = newConfig;
            lastError = "";
            httpCounter.reset();
            socksCounter.reset();

            try {
                if (newConfig.httpEnabled) {
                    httpServer = new HttpProxyServer(
                            newConfig.bindAddress,
                            newConfig.httpPort,
                            newConfig.lanOnly,
                            httpCounter);
                    httpServer.start();
                }
                if (newConfig.socksEnabled) {
                    socksServer = new Socks5ProxyServer(
                            newConfig.bindAddress,
                            newConfig.socksPort,
                            newConfig.lanOnly,
                            socksCounter);
                    socksServer.start();
                }
            } catch (IOException e) {
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                stopLocked();
                throw e;
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            stopLocked();
            config = null;
            lastError = "";
        }
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(
                    isRunningLocked(),
                    config,
                    httpCounter.getSentBytes(),
                    httpCounter.getReceivedBytes(),
                    socksCounter.getSentBytes(),
                    socksCounter.getReceivedBytes(),
                    lastError);
        }
    }

    private boolean isRunningLocked() {
        return (httpServer != null && httpServer.isRunning())
                || (socksServer != null && socksServer.isRunning());
    }

    private void stopLocked() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        if (socksServer != null) {
            socksServer.stop();
            socksServer = null;
        }
    }

    public static final class Snapshot {
        public final boolean running;
        public final ProxyConfig config;
        public final long httpSentBytes;
        public final long httpReceivedBytes;
        public final long socksSentBytes;
        public final long socksReceivedBytes;
        public final String lastError;

        private Snapshot(boolean running, ProxyConfig config, long httpSentBytes,
                long httpReceivedBytes, long socksSentBytes, long socksReceivedBytes,
                String lastError) {
            this.running = running;
            this.config = config;
            this.httpSentBytes = httpSentBytes;
            this.httpReceivedBytes = httpReceivedBytes;
            this.socksSentBytes = socksSentBytes;
            this.socksReceivedBytes = socksReceivedBytes;
            this.lastError = lastError;
        }
    }
}
