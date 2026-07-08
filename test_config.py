import json
from pathlib import Path

# Simulate what CoreConfigManager generates for vless+Reality
config = {
    "stats": {},
    "log": {"loglevel": "warning"},
    "policy": {
        "levels": {"8": {"handshake": 4, "connIdle": 300, "uplinkOnly": 1, "downlinkOnly": 1}},
        "system": {"statsOutboundUplink": True, "statsOutboundDownlink": True}
    },
    "inbounds": [{
        "tag": "socks",
        "port": 10808,
        "protocol": "socks",
        "settings": {"auth": "noauth", "udp": True, "userLevel": 8},
        "sniffing": {"enabled": True, "destOverride": ["http", "tls", "quic"]}
    }],
    "outbounds": [{
        "tag": "proxy",
        "protocol": "vless",
        "settings": {
            "vnext": [{
                "address": "nl2.saqanet.ru",
                "port": 443,
                "users": [{
                    "id": "c108f243-ea31-41ac-ac58-b34ad54a4f63",
                    "encryption": "none",
                    "flow": "xtls-rprx-vision"
                }]
            }]
        },
        "streamSettings": {
            "network": "tcp",
            "security": "reality",
            "realitySettings": {
                "serverName": "microsoft.com",
                "fingerprint": "chrome",
                "publicKey": "8BsHKENu9JlZbSh_7r5XaW2WLFuGdMXRdIz53m3WgRU",
                "shortId": "a979e31b9d33c1c7",
                "spiderX": ""
            }
        },
        "mux": {"enabled": False}
    },
    {"protocol": "freedom", "tag": "direct", "streamSettings": {"sockopt": {"domainStrategy": "UseIP"}}},
    {"protocol": "blackhole", "tag": "block", "settings": {"response": {"type": "http"}}},
    {"protocol": "dns", "tag": "dns-out", "settings": None, "streamSettings": None, "mux": None}],
    "routing": {
        "domainStrategy": "AsIs",
        "rules": [
            {"inboundTag": ["socks"], "port": "53", "outboundTag": "dns-out"},
            {"outboundTag": "direct", "inboundTag": ["domestic-dns0"], "domain": None},
            {"outboundTag": "proxy", "inboundTag": ["dns-module"], "domain": None}
        ]
    },
    "dns": {
        "hosts": {},
        "servers": ["1.1.1.1"],
        "tag": "dns-module",
        "queryStrategy": "UseIPv4"
    }
}

print("=== VALIDATION ===")
errors = []

# Check outbound
ob = config["outbounds"][0]
if ob["protocol"] != "vless":
    errors.append(f"Wrong protocol: {ob['protocol']}")
if ob["settings"]["vnext"][0]["address"] != "nl2.saqanet.ru":
    errors.append("Wrong server address")
if ob["settings"]["vnext"][0]["port"] != 443:
    errors.append("Wrong port")
if ob["settings"]["vnext"][0]["users"][0]["flow"] != "xtls-rprx-vision":
    errors.append("Wrong flow")
ss = ob["streamSettings"]
if ss["security"] != "reality":
    errors.append(f"Wrong security: {ss['security']}")
if ss["realitySettings"]["serverName"] != "microsoft.com":
    errors.append("Wrong SNI")
if ss["realitySettings"]["fingerprint"] != "chrome":
    errors.append("Wrong fingerprint")

# Check DNS
dns = config["dns"]
if dns["queryStrategy"] != "UseIPv4":
    errors.append("DNS not IPv4-only")

# Check routing - DNS rule
dns_rule = config["routing"]["rules"][0]
if dns_rule.get("port") != "53":
    errors.append("DNS routing rule missing port 53")
if dns_rule.get("outboundTag") != "dns-out":
    errors.append("DNS routing rule wrong target")

# Check DNS outbound
dns_out = [o for o in config["outbounds"] if o["tag"] == "dns-out"]
if not dns_out:
    errors.append("Missing dns-out outbound")

print(f"Errors: {len(errors)}")
for e in errors:
    print(f"  - {e}")

if not errors:
    print("Config looks VALID")

# Save for reference
Path("D:/SAQANet-Android/test_xray_config.json").write_text(json.dumps(config, indent=2))
print("\nSaved to test_xray_config.json")
