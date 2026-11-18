package com.codex.hotspotvpnproxy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(47, 147, 92);
    private static final int BACKGROUND = Color.rgb(8, 36, 43);
    private static final int PANEL = Color.rgb(10, 47, 57);
    private static final int TEXT = Color.rgb(230, 241, 244);
    private static final int MUTED = Color.rgb(154, 178, 186);

    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText bindAddressInput;
    private EditText httpPortInput;
    private EditText socksPortInput;
    private Switch httpSwitch;
    private Switch socksSwitch;
    private Switch lanOnlySwitch;
    private TextView statusText;
    private TextView vpnText;
    private TextView ipText;
    private TextView realtimeSpeedText;
    private TextView totalTrafficText;
    private TextView httpStatsText;
    private TextView socksStatsText;
    private TextView clientHintText;

    private long lastTimestamp;
    private long lastHttpSent;
    private long lastHttpReceived;
    private long lastSocksSent;
    private long lastSocksReceived;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(GREEN);
            getWindow().setNavigationBarColor(Color.rgb(7, 25, 31));
        }
        requestNotificationPermission();
        buildContentView();
        loadConfigIntoForm();
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastTimestamp = 0;
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private void buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        TextView title = new TextView(this);
        title.setText("手机 VPN 热点代理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(20), 0, dp(20), 0);
        title.setBackgroundColor(GREEN);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(88)));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        statusText = makeLargeText();
        content.addView(statusText);
        vpnText = makeMutedText();
        content.addView(vpnText);
        ipText = makeMutedText();
        ipText.setPadding(0, dp(8), 0, dp(8));
        content.addView(ipText);
        clientHintText = makeMutedText();
        content.addView(clientHintText);

        content.addView(makeRealtimeSpeedBlock());
        content.addView(makeDivider());

        bindAddressInput = makeInput("0.0.0.0", InputType.TYPE_CLASS_TEXT);
        content.addView(makeInputBlock("绑定 IP", bindAddressInput));

        httpSwitch = makeSwitch();
        content.addView(makeSwitchRow("HTTP/HTTPS 代理", httpSwitch));
        httpPortInput = makeInput("8080", InputType.TYPE_CLASS_NUMBER);
        content.addView(makeInputBlock("HTTP 端口", httpPortInput));
        httpStatsText = makeStatsText();
        content.addView(httpStatsText);

        content.addView(makeDivider());

        socksSwitch = makeSwitch();
        content.addView(makeSwitchRow("SOCKS5 代理（TCP/UDP）", socksSwitch));
        socksPortInput = makeInput("1080", InputType.TYPE_CLASS_NUMBER);
        content.addView(makeInputBlock("SOCKS5 端口", socksPortInput));
        socksStatsText = makeStatsText();
        content.addView(socksStatsText);

        content.addView(makeDivider());

        lanOnlySwitch = makeSwitch();
        content.addView(makeSwitchRow("只允许局域网连接", lanOnlySwitch));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        buttonRow.setPadding(0, dp(18), 0, 0);

        Button startButton = new Button(this);
        startButton.setText("启动 / 应用");
        startButton.setAllCaps(false);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startProxy();
            }
        });
        buttonRow.addView(startButton, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button stopButton = new Button(this);
        stopButton.setText("停止");
        stopButton.setAllCaps(false);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopProxy();
            }
        });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        stopParams.leftMargin = dp(12);
        buttonRow.addView(stopButton, stopParams);
        content.addView(buttonRow);

        setContentView(root);
    }

    private void loadConfigIntoForm() {
        ProxyConfig config = ProxyConfig.load(this);
        bindAddressInput.setText(config.bindAddress);
        httpPortInput.setText(String.valueOf(config.httpPort));
        socksPortInput.setText(String.valueOf(config.socksPort));
        httpSwitch.setChecked(config.httpEnabled);
        socksSwitch.setChecked(config.socksEnabled);
        lanOnlySwitch.setChecked(config.lanOnly);
    }

    private void startProxy() {
        ProxyConfig config = readConfigFromForm();
        if (config == null) {
            return;
        }
        if (!config.hasEnabledProxy()) {
            Toast.makeText(this, "至少启用一个代理端口", Toast.LENGTH_SHORT).show();
            return;
        }
        config.save(this);
        Intent intent = new Intent(this, ProxyService.class);
        intent.setAction(ProxyService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "代理服务已启动", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void stopProxy() {
        Intent intent = new Intent(this, ProxyService.class);
        intent.setAction(ProxyService.ACTION_STOP);
        startService(intent);
        refreshStatus();
    }

    private ProxyConfig readConfigFromForm() {
        int httpPort = ProxyConfig.parsePort(httpPortInput.getText().toString(), 8080);
        int socksPort = ProxyConfig.parsePort(socksPortInput.getText().toString(), 1080);
        boolean httpEnabled = httpSwitch.isChecked();
        boolean socksEnabled = socksSwitch.isChecked();
        if (httpEnabled && socksEnabled && httpPort == socksPort) {
            Toast.makeText(this, "HTTP 和 SOCKS5 端口不能相同", Toast.LENGTH_SHORT).show();
            return null;
        }
        httpPortInput.setText(String.valueOf(httpPort));
        socksPortInput.setText(String.valueOf(socksPort));
        return new ProxyConfig(
                bindAddressInput.getText().toString(),
                httpPort,
                socksPort,
                httpEnabled,
                socksEnabled,
                lanOnlySwitch.isChecked());
    }

    private void refreshStatus() {
        ProxyRuntime.Snapshot snapshot = ProxyRuntime.get().snapshot();
        boolean vpnActive = NetworkUtils.isVpnActive(this);
        vpnText.setText(vpnActive ? "VPN 状态 已检测到" : "VPN 状态 未检测到");
        ipText.setText("本机 IP " + NetworkUtils.getLocalIpSummary());

        ProxyConfig formConfig = readConfigForHintOnly();
        clientHintText.setText("客户端填写 " + NetworkUtils.getLocalIpSummary()
                + " : " + (formConfig.httpEnabled ? formConfig.httpPort : formConfig.socksPort));

        if (snapshot.running) {
            statusText.setText("代理运行中");
        } else if (snapshot.lastError != null && snapshot.lastError.length() > 0) {
            statusText.setText("启动失败 " + snapshot.lastError);
        } else {
            statusText.setText("代理已停止");
        }

        long now = SystemClock.elapsedRealtime();
        long elapsed = lastTimestamp == 0 ? 1000 : Math.max(1, now - lastTimestamp);
        long httpSentSpeed = speed(snapshot.httpSentBytes - lastHttpSent, elapsed);
        long httpReceivedSpeed = speed(snapshot.httpReceivedBytes - lastHttpReceived, elapsed);
        long socksSentSpeed = speed(snapshot.socksSentBytes - lastSocksSent, elapsed);
        long socksReceivedSpeed = speed(snapshot.socksReceivedBytes - lastSocksReceived, elapsed);
        long totalSentSpeed = httpSentSpeed + socksSentSpeed;
        long totalReceivedSpeed = httpReceivedSpeed + socksReceivedSpeed;
        long totalSentBytes = snapshot.httpSentBytes + snapshot.socksSentBytes;
        long totalReceivedBytes = snapshot.httpReceivedBytes + snapshot.socksReceivedBytes;

        realtimeSpeedText.setText("下行 " + formatSpeed(totalReceivedSpeed)
                + "    上行 " + formatSpeed(totalSentSpeed));
        totalTrafficText.setText("累计下行 " + formatBytes(totalReceivedBytes)
                + "    累计上行 " + formatBytes(totalSentBytes));

        httpStatsText.setText("接收 " + formatSpeed(httpReceivedSpeed)
                + "  总量 " + formatBytes(snapshot.httpReceivedBytes)
                + "\n发送 " + formatSpeed(httpSentSpeed)
                + "  总量 " + formatBytes(snapshot.httpSentBytes));
        socksStatsText.setText("接收 " + formatSpeed(socksReceivedSpeed)
                + "  总量 " + formatBytes(snapshot.socksReceivedBytes)
                + "\n发送 " + formatSpeed(socksSentSpeed)
                + "  总量 " + formatBytes(snapshot.socksSentBytes));

        lastTimestamp = now;
        lastHttpSent = snapshot.httpSentBytes;
        lastHttpReceived = snapshot.httpReceivedBytes;
        lastSocksSent = snapshot.socksSentBytes;
        lastSocksReceived = snapshot.socksReceivedBytes;
    }

    private ProxyConfig readConfigForHintOnly() {
        return new ProxyConfig(
                bindAddressInput.getText().toString(),
                ProxyConfig.parsePort(httpPortInput.getText().toString(), 8080),
                ProxyConfig.parsePort(socksPortInput.getText().toString(), 1080),
                httpSwitch.isChecked(),
                socksSwitch.isChecked(),
                lanOnlySwitch.isChecked());
    }

    private long speed(long byteDelta, long elapsedMillis) {
        return Math.max(0, byteDelta) * 1000L / elapsedMillis;
    }

    private TextView makeLargeText() {
        TextView textView = new TextView(this);
        textView.setTextColor(TEXT);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        textView.setPadding(0, 0, 0, dp(8));
        return textView;
    }

    private TextView makeMutedText() {
        TextView textView = new TextView(this);
        textView.setTextColor(MUTED);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setLineSpacing(dp(2), 1.0f);
        return textView;
    }

    private View makeRealtimeSpeedBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(14), dp(12), dp(14), dp(12));
        block.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(14), 0, dp(4));
        block.setLayoutParams(params);

        TextView label = new TextView(this);
        label.setText("实时网速");
        label.setTextColor(MUTED);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        block.addView(label);

        realtimeSpeedText = new TextView(this);
        realtimeSpeedText.setTextColor(TEXT);
        realtimeSpeedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        realtimeSpeedText.setPadding(0, dp(6), 0, dp(4));
        realtimeSpeedText.setSingleLine(false);
        block.addView(realtimeSpeedText);

        totalTrafficText = makeMutedText();
        block.addView(totalTrafficText);
        return block;
    }

    private TextView makeStatsText() {
        TextView textView = makeMutedText();
        textView.setPadding(0, dp(8), 0, dp(10));
        return textView;
    }

    private Switch makeSwitch() {
        Switch switchView = new Switch(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            switchView.setShowText(false);
        }
        return switchView;
    }

    private View makeSwitchRow(String label, Switch switchView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(8));

        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextColor(TEXT);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        row.addView(textView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(switchView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View makeInputBlock(String label, EditText input) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(2), 0, dp(8));

        TextView textView = makeMutedText();
        textView.setText(label);
        block.addView(textView);
        block.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)));
        return block;
    }

    private EditText makeInput(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setTextColor(TEXT);
        editText.setHintTextColor(MUTED);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        editText.setPadding(dp(10), 0, dp(10), 0);
        editText.setBackgroundColor(PANEL);
        return editText;
    }

    private View makeDivider() {
        View view = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1));
        params.setMargins(0, dp(14), 0, dp(12));
        view.setLayoutParams(params);
        view.setBackgroundColor(Color.rgb(20, 64, 74));
        return view;
    }

    private String formatSpeed(long bytesPerSecond) {
        return formatBytes(bytesPerSecond) + "/s";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int index = -1;
        do {
            value /= 1024.0;
            index++;
        } while (value >= 1024.0 && index < units.length - 1);
        return String.format(Locale.US, value >= 10 ? "%.1f%s" : "%.2f%s", value, units[index]);
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 33);
        }
    }
}
