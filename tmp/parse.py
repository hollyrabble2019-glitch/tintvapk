import json

with open('/tmp/channels_list.txt', 'r', encoding='utf-8') as f:
    lines = [line.strip() for line in f if line.strip()]

countries_list = []
current_country = None
current_channels = []

known_countries = [
    "Morocco", "Algeria", "Tunisia", "Libya", "Egypt", "Saudi Arabia", "United Arab Emirates", "Qatar"
]

i = 0
while i < len(lines):
    line = lines[i]
    is_country = False
    for kc in known_countries:
        if kc in line:
            is_country = True
            country_name = kc
            break
    
    if is_country:
        if current_country:
            countries_list.append({
                "country": current_country,
                "channels": current_channels
            })
        current_country = country_name
        current_channels = []
        i += 1
    else:
        channel_name = line
        if i + 1 < len(lines):
            channel_url = lines[i+1]
            if channel_url.startswith("http://") or channel_url.startswith("https://") or channel_url.startswith("rtmp://"):
                current_channels.append({
                    "name": channel_name,
                    "url": channel_url
                })
                i += 2
                continue
        i += 1

if current_country:
    countries_list.append({
        "country": current_country,
        "channels": current_channels
    })

# Add Live Matches if not already present, to preserve that section if we want
# or we can keep it inside the assets loading logic in Kotlin.
# Let's write out the json
with open('/tmp/parsed_channels.json', 'w', encoding='utf-8') as f:
    json.dump(countries_list, f, indent=2, ensure_ascii=False)

print(f"Parsed {len(countries_list)} countries successfully.")
for c in countries_list:
    print(f"- {c['country']}: {len(c['channels'])} channels")
