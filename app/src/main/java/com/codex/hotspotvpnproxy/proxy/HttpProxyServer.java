package com.codex.hotspotvpnproxy.proxy;

import com.codex.hotspotvpnproxy.NetworkUtils;
import com.codex.hotspotvpnproxy.TrafficCounter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HttpProxyServer {
    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private final String bindAddress;
    private final int port;
    private final boolean lanOnly;
    private final TrafficCounter counter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool(new NamedThreadFactory("http-proxy"));

    private ServerSocket serverSocket;
    private Thread acceptThread;

    public HttpProxyServer(String bindAddress, int port, boolean lanOnly, TrafficCounter counter) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.lanOnly = lanOnly;
        this.counter = counter;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        InetAddress address = InetAddress.getByName(bindAddress);
        serverSocket.bind(new InetSocketAddress(address, port));
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "http-proxy-accept");
        acceptThread.start();
    }

    public void stop() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        executor.shutdownNow();
    }

    public boolean isRunning() {
        return running.get();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                final Socket client = serverSocket.accept();
                client.setTcpNoDelay(true);
                if (lanOnly && !NetworkUtils.isPrivateOrLocal(client.getInetAddress())) {
                    TcpRelay.closeQuietly(client);
                    continue;
                }
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        handleClient(client);
                    }
                });
            } catch (IOException e) {
                if (running.get()) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleClient(Socket client) {
        Socket remote = null;
        try {
            InputStream input = client.getInputStream();
            OutputStream clientOutput = client.getOutputStream();
            byte[] headerBytes = readHttpHeader(input);
            if (headerBytes == null) {
                return;
            }

            String headerText = new String(headerBytes, StandardCharsets.ISO_8859_1);
            String[] lines = headerText.split("\\r?\\n");
            if (lines.length == 0) {
                sendHttpError(clientOutput, 400, "Bad Request");
                return;
            }

            String[] requestParts = lines[0].split(" ", 3);
            if (requestParts.length != 3) {
                sendHttpError(clientOutput, 400, "Bad Request");
                return;
            }

            String method = requestParts[0].toUpperCase(Locale.US);
            if ("CONNECT".equals(method)) {
                HostAndPort hostAndPort = parseHostAndPort(requestParts[1], 443);
                remote = connect(hostAndPort.host, hostAndPort.port);
                byte[] ok = "HTTP/1.1 200 Connection Established\r\n\r\n"
                        .getBytes(StandardCharsets.ISO_8859_1);
                clientOutput.write(ok);
                clientOutput.flush();
                counter.addReceived(ok.length);
                TcpRelay.bridge(client, remote, counter, executor);
            } else {
                HttpTarget target = parseHttpTarget(requestParts[1], lines);
                remote = connect(target.host, target.port);
                byte[] forwardedHeader = buildForwardHeader(requestParts[0], target, requestParts[2], lines);
                remote.getOutputStream().write(forwardedHeader);
                remote.getOutputStream().flush();
                counter.addSent(forwardedHeader.length);
                TcpRelay.bridge(client, remote, counter, executor);
            }
        } catch (Exception e) {
            try {
                sendHttpError(client.getOutputStream(), 502, "Proxy Error");
            } catch (IOException ignored) {
            }
        } finally {
            TcpRelay.closeQuietly(client);
            TcpRelay.closeQuietly(remote);
        }
    }

    private Socket connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        return socket;
    }

    private byte[] readHttpHeader(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
        byte[] pattern = new byte[] {'\r', '\n', '\r', '\n'};
        int matched = 0;
        int next;
        while ((next = input.read()) != -1) {
            output.write(next);
            if (next == pattern[matched]) {
                matched++;
                if (matched == pattern.length) {
                    return output.toByteArray();
                }
            } else {
                matched = next == pattern[0] ? 1 : 0;
            }
            if (output.size() > MAX_HEADER_BYTES) {
                throw new IOException("HTTP header is too large");
            }
        }
        return output.size() == 0 ? null : output.toByteArray();
    }

    private HttpTarget parseHttpTarget(String requestTarget, String[] lines)
            throws IOException, URISyntaxException {
        String hostHeader = findHeader(lines, "Host");
        if (requestTarget.startsWith("http://") || requestTarget.startsWith("https://")) {
            URI uri = new URI(requestTarget);
            if (!"http".equalsIgnoreCase(uri.getScheme())) {
                throw new IOException("HTTPS requests must use CONNECT");
            }
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            String path = uri.getRawPath();
            if (path == null || path.length() == 0) {
                path = "/";
            }
            if (uri.getRawQuery() != null) {
                path = path + "?" + uri.getRawQuery();
            }
            return new HttpTarget(host, port, path, hostHeaderFor(host, port, 80));
        }

        if (hostHeader == null || hostHeader.length() == 0) {
            throw new IOException("Missing Host header");
        }
        HostAndPort hostAndPort = parseHostAndPort(hostHeader, 80);
        String path = requestTarget.length() == 0 ? "/" : requestTarget;
        return new HttpTarget(hostAndPort.host, hostAndPort.port, path, hostHeader);
    }

    private byte[] buildForwardHeader(String method, HttpTarget target, String version, String[] lines) {
        StringBuilder builder = new StringBuilder();
        builder.append(method).append(' ').append(target.path).append(' ').append(version).append("\r\n");
        boolean wroteHost = false;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            if ("Proxy-Connection".equalsIgnoreCase(name)
                    || "Connection".equalsIgnoreCase(name)
                    || "Keep-Alive".equalsIgnoreCase(name)) {
                continue;
            }
            if ("Host".equalsIgnoreCase(name)) {
                wroteHost = true;
                builder.append("Host: ").append(target.hostHeader).append("\r\n");
            } else {
                builder.append(line).append("\r\n");
            }
        }
        if (!wroteHost) {
            builder.append("Host: ").append(target.hostHeader).append("\r\n");
        }
        builder.append("Connection: close\r\n\r\n");
        return builder.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String findHeader(String[] lines, String expectedName) {
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            if (expectedName.equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private HostAndPort parseHostAndPort(String value, int defaultPort) throws IOException {
        String target = value.trim();
        if (target.startsWith("[")) {
            int end = target.indexOf(']');
            if (end == -1) {
                throw new IOException("Invalid IPv6 host");
            }
            String host = target.substring(1, end);
            int port = defaultPort;
            if (target.length() > end + 1 && target.charAt(end + 1) == ':') {
                port = Integer.parseInt(target.substring(end + 2));
            }
            return new HostAndPort(host, port);
        }

        int colon = target.lastIndexOf(':');
        if (colon > 0 && target.indexOf(':') == colon) {
            return new HostAndPort(target.substring(0, colon),
                    Integer.parseInt(target.substring(colon + 1)));
        }
        return new HostAndPort(target, defaultPort);
    }

    private String hostHeaderFor(String host, int port, int defaultPort) {
        if (port == defaultPort) {
            return host;
        }
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    private void sendHttpError(OutputStream output, int code, String message) throws IOException {
        byte[] body = (message + "\n").getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " " + message + "\r\n"
                + "Connection: close\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.ISO_8859_1);
        output.write(headerBytes);
        output.write(body);
        output.flush();
        counter.addReceived(headerBytes.length + body.length);
    }

    private static final class HttpTarget {
        final String host;
        final int port;
        final String path;
        final String hostHeader;

        HttpTarget(String host, int port, String path, String hostHeader) {
            this.host = host;
            this.port = port;
            this.path = path;
            this.hostHeader = hostHeader;
        }
    }

    private static final class HostAndPort {
        final String host;
        final int port;

        HostAndPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private int index = 0;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    }
}
