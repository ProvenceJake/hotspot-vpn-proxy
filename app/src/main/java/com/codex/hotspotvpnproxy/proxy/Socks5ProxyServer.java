package com.codex.hotspotvpnproxy.proxy;

import com.codex.hotspotvpnproxy.NetworkUtils;
import com.codex.hotspotvpnproxy.TrafficCounter;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Socks5ProxyServer {
    private static final int CMD_CONNECT = 0x01;
    private static final int CMD_UDP_ASSOCIATE = 0x03;
    private static final int ATYP_IPV4 = 0x01;
    private static final int ATYP_DOMAIN = 0x03;
    private static final int ATYP_IPV6 = 0x04;
    private static final int REP_SUCCEEDED = 0x00;
    private static final int REP_GENERAL_FAILURE = 0x01;
    private static final int REP_COMMAND_NOT_SUPPORTED = 0x07;
    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private final String bindAddress;
    private final int port;
    private final boolean lanOnly;
    private final TrafficCounter counter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool(new NamedThreadFactory("socks5-proxy"));

    private ServerSocket serverSocket;
    private Thread acceptThread;

    public Socks5ProxyServer(String bindAddress, int port, boolean lanOnly, TrafficCounter counter) {
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
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), port));
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "socks5-proxy-accept");
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
        DatagramSocket udpSocket = null;
        try {
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();
            if (!readGreeting(input, output)) {
                return;
            }

            int version = readUnsignedByte(input);
            if (version != 0x05) {
                throw new IOException("Not a SOCKS5 request");
            }
            int command = readUnsignedByte(input);
            readUnsignedByte(input);
            int atyp = readUnsignedByte(input);
            Address target = readAddress(input, atyp);
            int targetPort = readPort(input);

            if (command == CMD_CONNECT) {
                remote = new Socket();
                remote.setTcpNoDelay(true);
                remote.connect(new InetSocketAddress(target.host, targetPort), CONNECT_TIMEOUT_MS);
                sendReply(output, REP_SUCCEEDED, remote.getLocalAddress(), remote.getLocalPort());
                TcpRelay.bridge(client, remote, counter, executor);
            } else if (command == CMD_UDP_ASSOCIATE) {
                udpSocket = new DatagramSocket(null);
                udpSocket.setReuseAddress(true);
                udpSocket.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), 0));
                udpSocket.setSoTimeout(1000);
                sendReply(output, REP_SUCCEEDED, udpSocket.getLocalAddress(), udpSocket.getLocalPort());
                keepUdpAssociation(input, udpSocket);
            } else {
                sendReply(output, REP_COMMAND_NOT_SUPPORTED, null, 0);
            }
        } catch (Exception e) {
            try {
                sendReply(client.getOutputStream(), REP_GENERAL_FAILURE, null, 0);
            } catch (IOException ignored) {
            }
        } finally {
            TcpRelay.closeQuietly(client);
            TcpRelay.closeQuietly(remote);
            if (udpSocket != null) {
                udpSocket.close();
            }
        }
    }

    private boolean readGreeting(InputStream input, OutputStream output) throws IOException {
        int version = readUnsignedByte(input);
        if (version != 0x05) {
            return false;
        }
        int methodCount = readUnsignedByte(input);
        byte[] methods = readFully(input, methodCount);
        boolean supportsNoAuth = false;
        for (byte method : methods) {
            if ((method & 0xff) == 0x00) {
                supportsNoAuth = true;
                break;
            }
        }
        output.write(new byte[] {0x05, supportsNoAuth ? (byte) 0x00 : (byte) 0xff});
        output.flush();
        return supportsNoAuth;
    }

    private void keepUdpAssociation(final InputStream tcpInput, final DatagramSocket udpSocket)
            throws IOException {
        final AtomicBoolean alive = new AtomicBoolean(true);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                udpLoop(udpSocket, alive);
            }
        });

        try {
            while (running.get() && tcpInput.read() != -1) {
                // The TCP connection only keeps the UDP association alive.
            }
        } finally {
            alive.set(false);
            udpSocket.close();
        }
    }

    private void udpLoop(DatagramSocket udpSocket, AtomicBoolean alive) {
        InetSocketAddress clientEndpoint = null;
        byte[] buffer = new byte[65_535];
        while (running.get() && alive.get() && !udpSocket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                udpSocket.receive(packet);
                InetSocketAddress source = new InetSocketAddress(packet.getAddress(), packet.getPort());
                byte[] payload = Arrays.copyOfRange(packet.getData(), packet.getOffset(),
                        packet.getOffset() + packet.getLength());

                if (clientEndpoint == null || clientEndpoint.equals(source)) {
                    clientEndpoint = source;
                    UdpRequest request = parseUdpRequest(payload);
                    DatagramPacket forward = new DatagramPacket(
                            request.payload,
                            request.payload.length,
                            InetAddress.getByName(request.host),
                            request.port);
                    udpSocket.send(forward);
                    counter.addSent(request.payload.length);
                } else if (clientEndpoint != null) {
                    byte[] response = buildUdpResponse(packet.getAddress(), packet.getPort(), payload);
                    DatagramPacket reply = new DatagramPacket(
                            response,
                            response.length,
                            clientEndpoint.getAddress(),
                            clientEndpoint.getPort());
                    udpSocket.send(reply);
                    counter.addReceived(payload.length);
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException ignored) {
                if (!udpSocket.isClosed()) {
                    break;
                }
            }
        }
    }

    private UdpRequest parseUdpRequest(byte[] packet) throws IOException {
        if (packet.length < 4 || packet[0] != 0x00 || packet[1] != 0x00 || packet[2] != 0x00) {
            throw new IOException("Invalid UDP request");
        }
        int offset = 3;
        int atyp = packet[offset++] & 0xff;
        Address address = readAddress(packet, offset, atyp);
        offset = address.nextOffset;
        if (packet.length < offset + 2) {
            throw new IOException("Missing UDP port");
        }
        int port = ((packet[offset] & 0xff) << 8) | (packet[offset + 1] & 0xff);
        offset += 2;
        byte[] payload = Arrays.copyOfRange(packet, offset, packet.length);
        return new UdpRequest(address.host, port, payload);
    }

    private byte[] buildUdpResponse(InetAddress sourceAddress, int sourcePort, byte[] payload)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x00);
        output.write(0x00);
        output.write(0x00);
        byte[] addressBytes = sourceAddress.getAddress();
        if (addressBytes.length == 4) {
            output.write(ATYP_IPV4);
        } else if (addressBytes.length == 16) {
            output.write(ATYP_IPV6);
        } else {
            throw new IOException("Unsupported address type");
        }
        output.write(addressBytes);
        output.write((sourcePort >> 8) & 0xff);
        output.write(sourcePort & 0xff);
        output.write(payload);
        return output.toByteArray();
    }

    private void sendReply(OutputStream output, int reply, InetAddress bindAddress, int bindPort)
            throws IOException {
        InetAddress replyAddress = bindAddress;
        if (replyAddress == null || replyAddress.isAnyLocalAddress()) {
            replyAddress = NetworkUtils.getBestLocalAddress();
        }
        byte[] addressBytes = replyAddress.getAddress();
        int atyp = addressBytes.length == 4 ? ATYP_IPV4 : ATYP_IPV6;
        output.write(0x05);
        output.write(reply);
        output.write(0x00);
        output.write(atyp);
        output.write(addressBytes);
        output.write((bindPort >> 8) & 0xff);
        output.write(bindPort & 0xff);
        output.flush();
    }

    private Address readAddress(InputStream input, int atyp) throws IOException {
        if (atyp == ATYP_IPV4) {
            byte[] raw = readFully(input, 4);
            return new Address(InetAddress.getByAddress(raw).getHostAddress(), -1);
        }
        if (atyp == ATYP_DOMAIN) {
            int length = readUnsignedByte(input);
            byte[] raw = readFully(input, length);
            return new Address(new String(raw, StandardCharsets.ISO_8859_1), -1);
        }
        if (atyp == ATYP_IPV6) {
            byte[] raw = readFully(input, 16);
            return new Address(InetAddress.getByAddress(raw).getHostAddress(), -1);
        }
        throw new IOException("Unsupported address type");
    }

    private Address readAddress(byte[] packet, int offset, int atyp) throws IOException {
        if (atyp == ATYP_IPV4) {
            if (packet.length < offset + 4) {
                throw new EOFException();
            }
            byte[] raw = Arrays.copyOfRange(packet, offset, offset + 4);
            return new Address(InetAddress.getByAddress(raw).getHostAddress(), offset + 4);
        }
        if (atyp == ATYP_DOMAIN) {
            if (packet.length < offset + 1) {
                throw new EOFException();
            }
            int length = packet[offset] & 0xff;
            if (packet.length < offset + 1 + length) {
                throw new EOFException();
            }
            String host = new String(packet, offset + 1, length, StandardCharsets.ISO_8859_1);
            return new Address(host, offset + 1 + length);
        }
        if (atyp == ATYP_IPV6) {
            if (packet.length < offset + 16) {
                throw new EOFException();
            }
            byte[] raw = Arrays.copyOfRange(packet, offset, offset + 16);
            return new Address(InetAddress.getByAddress(raw).getHostAddress(), offset + 16);
        }
        throw new IOException("Unsupported address type");
    }

    private int readPort(InputStream input) throws IOException {
        int high = readUnsignedByte(input);
        int low = readUnsignedByte(input);
        return (high << 8) | low;
    }

    private int readUnsignedByte(InputStream input) throws IOException {
        int value = input.read();
        if (value == -1) {
            throw new EOFException();
        }
        return value;
    }

    private byte[] readFully(InputStream input, int count) throws IOException {
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = input.read(data, offset, count - offset);
            if (read == -1) {
                throw new EOFException();
            }
            offset += read;
        }
        return data;
    }

    private static final class Address {
        final String host;
        final int nextOffset;

        Address(String host, int nextOffset) {
            this.host = host;
            this.nextOffset = nextOffset;
        }
    }

    private static final class UdpRequest {
        final String host;
        final int port;
        final byte[] payload;

        UdpRequest(String host, int port, byte[] payload) {
            this.host = host;
            this.port = port;
            this.payload = payload;
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
