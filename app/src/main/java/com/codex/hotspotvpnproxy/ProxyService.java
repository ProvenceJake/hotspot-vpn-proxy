package com.codex.hotspotvpnproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

public final class ProxyService extends Service {
    public static final String ACTION_START = "com.codex.hotspotvpnproxy.START";
    public static final String ACTION_STOP = "com.codex.hotspotvpnproxy.STOP";

    private static final String CHANNEL_ID = "proxy_service";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            ProxyRuntime.get().stop();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("正在启动代理服务"));
        ProxyConfig config = ProxyConfig.load(this);
        try {
            ProxyRuntime.get().start(config);
            updateNotification(config);
        } catch (IOException e) {
            updateNotification("启动失败: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ProxyRuntime.get().stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateNotification(ProxyConfig config) {
        StringBuilder text = new StringBuilder();
        if (config.httpEnabled) {
            text.append("HTTP ").append(config.httpPort);
        }
        if (config.socksEnabled) {
            if (text.length() > 0) {
                text.append(" / ");
            }
            text.append("SOCKS5 ").append(config.socksPort);
        }
        if (text.length() == 0) {
            text.append("没有启用代理端口");
        }
        updateNotification(text.toString());
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, ProxyService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle("VPN 热点代理运行中")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_proxy)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent);
        return builder.build();
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "代理服务",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("保持 HTTP 和 SOCKS5 代理在后台运行");
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
