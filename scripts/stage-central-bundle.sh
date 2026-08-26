#!/bin/bash
## Stages a signed Maven Central Portal bundle for the tvOS fork's compose-multiplatform
## artifacts (dev.sajidali.compose.components:components-resources* and
## dev.sajidali.compose:compose-gradle-plugin) out of ~/.m2, exactly like
## compose-multiplatform-core's scripts/stage-central-bundle.sh does for the core fork.
##
## It NEVER uploads anything. Run the two mavenLocal publishes first:
##   ./gradlew -p components -Pcompose.useMavenLocal=true \
##       -Ppublication.groupId=dev.sajidali.compose.components :resources:library:publishToMavenLocal
##   ./gradlew -p gradle-plugins -Ppublication.groupId=dev.sajidali.compose :compose:publishToMavenLocal
## then:
##   PUBLISH_SIGNING_PASSWORD=... ./scripts/stage-central-bundle.sh <version>
## Signing uses the gpg keyring key FB85D8A8 (same key as the core fork bundle).
set -euo pipefail

VERSION="${1:?usage: $0 <version>   (e.g. 1.12.0)}"
KEY_ID="${PUBLISH_SIGNING_KEY_ID:-FB85D8A8}"
: "${PUBLISH_SIGNING_PASSWORD:?export PUBLISH_SIGNING_PASSWORD (gpg passphrase for $KEY_ID)}"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
M2_REPO="${HOME}/.m2/repository"
STAGING="$ROOT_DIR/build/central-staging-repo"
BUNDLE="$ROOT_DIR/build/central-bundle.zip"

for flag in "$@"; do case "$flag" in --upload|--publish|--push) echo "refusing: this script never uploads" >&2; exit 1;; esac; done

rm -rf "$STAGING" "$BUNDLE"; mkdir -p "$STAGING"

echo "=== Step 1: copy the $VERSION version directories of the two artifact families out of ~/.m2 ==="
found=0
for base in dev/sajidali/compose/components dev/sajidali/compose/compose-gradle-plugin; do
    [ -d "$M2_REPO/$base" ] || continue
    while IFS= read -r -d '' vdir; do
        rel="${vdir#$M2_REPO/}"
        mkdir -p "$STAGING/$(dirname "$rel")"
        cp -R "$vdir" "$STAGING/$rel"
        found=$((found+1))
    done < <(find "$M2_REPO/$base" -type d -name "$VERSION" -print0)
done
echo "  module version directories staged: $found"
[ "$found" -gt 0 ] || { echo "nothing found at version $VERSION under ~/.m2/dev/sajidali/compose/{components,compose-gradle-plugin}" >&2; exit 1; }
# a plugin marker must never be staged under an overridden group (the build suppresses it; double-check)
find "$STAGING" -path '*gradle.plugin*' -print | grep . && { echo "plugin marker leaked into the bundle" >&2; exit 1; } || true
find "$STAGING" -name 'maven-metadata-local.xml*' -delete

echo "=== Step 2: sources/javadoc completeness (Central checks presence only) ==="
generated=0
while IFS= read -r -d '' pom; do
    dir=$(dirname "$pom"); base=$(basename "$pom" .pom)
    if ! ls "$dir/$base"-sources.jar >/dev/null 2>&1; then echo "  WARN no sources jar: $base"; fi
    if ! ls "$dir/$base"-javadoc.jar >/dev/null 2>&1; then
        tmp=$(mktemp -d); echo "Javadoc is not generated for this artifact." > "$tmp/README.txt"
        (cd "$tmp" && zip -q "$dir/$base-javadoc.jar" README.txt); rm -rf "$tmp"; generated=$((generated+1))
    fi
done < <(find "$STAGING" -type f -name '*.pom' -print0)
echo "  stub javadoc jars generated: $generated"

echo "=== Step 3: sign every payload file with $KEY_ID and generate md5/sha1 ==="
signed=0; sums=0
while IFS= read -r -d '' f; do
    case "$f" in *.asc|*.md5|*.sha1) continue;; esac
    if [ ! -f "$f.asc" ]; then
        gpg --batch --yes --pinentry-mode loopback --passphrase "$PUBLISH_SIGNING_PASSWORD" \
            --local-user "$KEY_ID" --armor --detach-sign --output "$f.asc" "$f"
        signed=$((signed+1))
    fi
    [ -f "$f.md5" ]  || { md5 -q "$f" > "$f.md5"; sums=$((sums+1)); }
    [ -f "$f.sha1" ] || { shasum -a 1 "$f" | awk '{print $1}' > "$f.sha1"; sums=$((sums+1)); }
done < <(find "$STAGING" -type f -print0)
echo "  signatures created: $signed, checksum files created: $sums"
sample=$(find "$STAGING" -name '*.pom' | head -1); gpg --verify "$sample.asc" "$sample" 2>&1 | grep -E 'Good signature' || { echo "signature verification failed" >&2; exit 1; }

echo "=== Step 4: zip (Central Portal layout: repo-root-relative paths) ==="
(cd "$STAGING" && zip -q -r "$BUNDLE" . -x "*.DS_Store")
echo "  bundle: $BUNDLE ($(du -h "$BUNDLE" | cut -f1), $(unzip -Z1 "$BUNDLE" | wc -l | tr -d ' ') files)"
echo "=== DONE (nothing uploaded). Manual next step:"
echo "# curl --request POST --header \"Authorization: Bearer \$CENTRAL_TOKEN\" \\"
echo "#   --form bundle=@\"$BUNDLE\" \\"
echo "#   \"https://central.sonatype.com/api/v1/publisher/upload?name=dev.sajidali-compose-multiplatform-tvos-fork-$VERSION&publishingType=USER_MANAGED\""
