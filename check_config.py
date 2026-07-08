import json
from pathlib import Path

template = Path('V2rayNG/app/src/main/assets/v2ray_config.json').read_text()
config = json.loads(template)

print('=== INBOUNDS ===')
for ib in config.get('inbounds', []):
    print(f"  tag={ib.get('tag')} protocol={ib.get('protocol')} port={ib.get('port')}")
    sniff = ib.get('sniffing', {})
    print(f"    sniffing enabled={sniff.get('enabled')} destOverride={sniff.get('destOverride')}")

print()
print('=== OUTBOUNDS ===')
for ob in config.get('outbounds', []):
    print(f"  tag={ob.get('tag')} protocol={ob.get('protocol')}")

print()
print('=== DNS ===')
dns = config.get('dns', {})
print(f"  servers={dns.get('servers')} queryStrategy={dns.get('queryStrategy')}")

print()
print('=== ROUTING ===')
routing = config.get('routing', {})
print(f"  domainStrategy={routing.get('domainStrategy')}")
for rule in routing.get('rules', []):
    print(f"  rule: inboundTag={rule.get('inboundTag')} outboundTag={rule.get('outboundTag')} port={rule.get('port')}")
