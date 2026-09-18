#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
screenshots_dir="$repo_root/test-artifacts/screenshots/locksmith-management"

echo "Local visual review: TotemLocksmith"
echo
echo "Screenshots:"
if compgen -G "$screenshots_dir/*.png" >/dev/null; then
    find "$screenshots_dir" -maxdepth 1 -type f -name '*.png' -print | sort
else
    echo "  No screenshots found. Run the client GUI GameTests first."
fi

echo
echo "16x16 assets:"
for asset in \
    "$repo_root/src/main/resources/assets/totem-locksmith/icon.png" \
    "$repo_root/src/main/resources/assets/totem/textures/item/locksmith/padlock.png" \
    "$repo_root/src/main/resources/assets/totem/textures/item/locksmith/key_blank.png" \
    "$repo_root/src/main/resources/assets/totem/textures/item/locksmith/bound_key.png"; do
    file "$asset"
done

cat <<'CHECKLIST'

Review locally at native scale and integer zoom:
- vanilla spacing, widgets, font, tooltips, focus, and clipping;
- readable English and Traditional Chinese text;
- crisp pixels, correct transparency, and recognizable padlock identity;
- screenshots match the intended production screen and supported GUI scales.

Record approval or rejection locally before publishing.
CHECKLIST
