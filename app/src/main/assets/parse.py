import json
import os

list_path = 'app/src/main/assets/channels_list.txt'
json_path = 'app/src/main/assets/channels.json'

with open(list_path, 'r', encoding='utf-8') as f:
    lines = [line.strip() for line in f if line.strip()]

countries_list = []

# First, add the "Live Matches" country at the top, as required in the structure.
countries_list.append({
    "country": "Live Matches",
    "channels": [
        {
            "name": "🔴 Real Madrid vs Barcelona (La Liga)",
            "url": "https://static.france24.com/live/F24_EN_LO_HLS/live_web.m3u8"
        },
        {
            "name": "🔴 Manchester City vs Arsenal (Premier League)",
            "url": "https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-n6pess5lwbghr/2M_ES.m3u8"
        }
    ]
})

country_headers = {
    "🇲🇦 Morocco": "Morocco",
    "🇩🇿 Algeria": "Algeria",
    "🇹🇳 Tunisia": "Tunisia",
    "🇱🇾 Libya": "Libya",
    "🇪🇬 Egypt": "Egypt",
    "🇸🇦 Saudi Arabia": "Saudi Arabia",
    "🇦🇪 United Arab Emirates": "United Arab Emirates",
    "🇶🇦 Qatar": "Qatar"
}

current_country = None
current_channels = []

i = 0
while i < len(lines):
    line = lines[i]
    
    if line in country_headers:
        if current_country:
            countries_list.append({
                "country": current_country,
                "channels": current_channels
            })
        current_country = country_headers[line]
        current_channels = []
        i += 1
    else:
        channel_name = line
        if i + 1 < len(lines):
            channel_url = lines[i+1]
            if channel_url.startswith("http://") or channel_url.startswith("https://") or channel_url.startswith("rtmp://"):
                # Clean duplicate urls within the same country
                exists = False
                for ch in current_channels:
                    if ch["url"] == channel_url:
                        exists = True
                        break
                if not exists:
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

# Save to channels.json
with open(json_path, 'w', encoding='utf-8') as f:
    json.dump(countries_list, f, indent=2, ensure_ascii=False)

print(f"Successfully generated channels.json with {len(countries_list)} sections.")
for item in countries_list:
    print(f"  - {item['country']}: {len(item['channels'])} channels")
