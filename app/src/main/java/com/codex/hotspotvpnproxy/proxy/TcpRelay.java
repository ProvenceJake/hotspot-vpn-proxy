package com.codex.hotspotvpnproxy.proxy;

import com.codex.hotspotvpnproxy.TrafficCounter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

final class TcpRelay {
    private static final int BUFFER_SIZE = 16 * 1024;

    private TcpRelay() {
    }

    static void bridge(final Socket client, final Socket remote, final TrafficCounter counter,
            ExecutorService executor) throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(2);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                pump(client, remote, true, counter, done);
            }
        });
        executor.execute(new Runnable() {
            @Override
            public void run() {
                pump(remote, client, false, counter, done);
            }
        });
        done.await();
    }

    private static void pump(Socket from, Socket to, boolean sentDirection,
            TrafficCounter counter, CountDownLatch done) {
        try {
            InputStream input = from.getInputStream();
            OutputStream output = to.getOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                output.flush();
                if (sentDirection) {
                    counter.addSent(read);
                } else {
                    counter.addReceived(read);
                }
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly(from);
            closeQuietly(to);
            done.countDown();
        }
    }

    static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
