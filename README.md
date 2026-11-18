<p align="center"><img src="docs/assets/app-icon.svg" width="120" height="120" alt="Hotspot VPN Proxy icon"></p>
<h1 align="center">Hotspot VPN Proxy</h1>
<p align="center">Share your Android phone’s VPN with devices connected to its hotspot.</p>
<p align="center"><img src="https://img.shields.io/badge/Android-6.0%2B-2f935c?style=flat-square" alt="Android 6.0+"> <img src="https://img.shields.io/badge/APK-1.0_debug-334155?style=flat-square" alt="1.0 debug"></p>
<p align="center"><strong>English</strong> · <a href="README.zh-CN.md">简体中文</a></p>
<p align="center"><a href="https://github.com/ProvenceJake/hotspot-vpn-proxy/releases/download/v1.0/HotspotVpnProxy-1.0.apk"><strong>Download APK</strong></a> · <a href="https://github.com/ProvenceJake/hotspot-vpn-proxy/releases">Releases</a> · <a href="https://github.com/ProvenceJake/hotspot-vpn-proxy/issues">Report an issue</a></p>

---

## Connect a device

1. Install the APK on the Android phone that will share its connection. The download is a debug-signed build with a Chinese interface.
2. Connect the phone to your VPN and enable its Wi-Fi hotspot. For a per-app VPN, include **VPN 热点代理** in the VPN configuration.
3. Open **VPN 热点代理** and tap **启动 / 应用** to start the proxy.
4. Join the hotspot from the second device. In its Wi-Fi settings, choose a manual HTTP proxy. Use the hotspot gateway address as the host and `8080` as the port. SOCKS5 clients can use the same host with port `1080`.

The app lists the phone’s local addresses. Match the address to the gateway shown in the client’s Wi-Fi details. Only apps configured to use the proxy send traffic through it. Outbound connections follow the phone’s VPN routing rules.

## Proxy settings

| Protocol | Default port | Traffic |
| --- | --- | --- |
| HTTP | 8080 | Web requests and HTTPS via CONNECT |
| SOCKS5 | 1080 | TCP CONNECT and UDP ASSOCIATE |

You can change the listening address and ports before starting. Use a trusted hotspot and keep the LAN-only setting enabled. The dashboard updates upload and download rates every second and shows the total bytes relayed in each direction. The foreground notification includes a stop action.

## Build

Open the repository in Android Studio with Android SDK Platform 36 installed. On macOS, the included script uses Android Studio’s JDK and defaults to SDK Build Tools 36.1.0.

```sh
./build-debug.sh
```

The signed APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
