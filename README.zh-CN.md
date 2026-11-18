<p align="center"><img src="docs/assets/app-icon.svg" width="120" height="120" alt="VPN 热点代理图标"></p>
<h1 align="center">VPN 热点代理</h1>
<p align="center">让连接热点的设备通过安卓手机的 VPN 上网。</p>
<p align="center"><img src="https://img.shields.io/badge/Android-6.0%2B-2f935c?style=flat-square" alt="Android 6.0+"> <img src="https://img.shields.io/badge/APK-1.0_debug-334155?style=flat-square" alt="1.0 debug"></p>
<p align="center"><a href="README.md">English</a> · <strong>简体中文</strong></p>
<p align="center"><a href="https://github.com/ProvenceJake/hotspot-vpn-proxy/releases/download/v1.0/HotspotVpnProxy-1.0.apk"><strong>下载 APK</strong></a> · <a href="https://github.com/ProvenceJake/hotspot-vpn-proxy/releases">版本记录</a> · <a href="https://github.com/ProvenceJake/hotspot-vpn-proxy/issues">反馈问题</a></p>

---

## 连接设备

1. 把 APK 安装到用来开热点的安卓手机上。下载包使用调试签名，应用界面为中文。
2. 在手机上连接 VPN，开启 Wi-Fi 热点。如果 VPN 使用分应用模式，请将「VPN 热点代理」加入 VPN 代理范围。
3. 打开「VPN 热点代理」，点击「启动 / 应用」。
4. 在另一台设备上连接此热点，打开 Wi-Fi 设置，选择手动 HTTP 代理。主机填热点网关地址，端口填 `8080`。支持 SOCKS5 的应用可使用相同地址和端口 `1080`。

软件会列出手机的本机 IP。若出现多个地址，在客户端的 Wi-Fi 详情中找到「网关」或「路由器」，选与它一致的地址。只有使用代理的应用流量才会转发，出口取决于手机 VPN 的路由规则。

## 代理设置

| 协议 | 默认端口 | 支持的流量 |
| --- | --- | --- |
| HTTP | 8080 | HTTP 请求，以及通过 CONNECT 转发的 HTTPS 连接 |
| SOCKS5 | 1080 | TCP CONNECT 和 UDP ASSOCIATE |

启动前可修改监听地址和端口。请在可信热点上使用，并保持「只允许局域网连接」开启。主页每秒刷新上下行网速，同时显示两个方向的累计转发流量。可从前台服务通知中停止代理。

## 从源码构建

安装 Android SDK Platform 36 后，用 Android Studio 打开仓库。在 macOS 上也可以运行以下脚本，默认使用 Android Studio 自带的 JDK 和 SDK Build Tools 36.1.0。

```sh
./build-debug.sh
```

签名后的安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。
