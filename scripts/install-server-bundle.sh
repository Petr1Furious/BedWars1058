#!/usr/bin/env bash
# Installs local.aspaper:server-bundle:<mc> into ~/.m2, the compile-only dependency of
# server_platform_paper_v26_R1: the patched (Mojang-named) server jar plus every library it uses.
#
# usage: scripts/install-server-bundle.sh <mc-version> <server-dir>
#   <server-dir> must have been started once with that version, so that it contains
#   asp-server.jar (or another paperclip jar), versions/<mc>/*.jar and cache/mojang_<mc>.jar.
# Needs unzip, jar and mvn on PATH, e.g. nix-shell -p maven temurin-bin-25.
set -euo pipefail
mc=$1
dir=$2
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

paperclip=${PAPERCLIP:-$dir/asp-server.jar}
server_jar=$(ls "$dir"/versions/"$mc"/*.jar | head -n1)
mojang_jar=$dir/cache/mojang_$mc.jar

mkdir -p "$work/libs" "$work/merge"
unzip -q -o "$paperclip" 'META-INF/libraries/*' -d "$work/libs"
unzip -q -o "$mojang_jar" 'META-INF/libraries/*' -d "$work/libs"

cd "$work/merge"
# the server jar wins over libraries; native transports are not needed to compile
for jar in "$server_jar" $(find "$work/libs" -name '*.jar' | grep -v -E 'native-(epoll|kqueue)'); do
  unzip -qq -n "$jar" -x "META-INF/*.SF" "META-INF/*.RSA" "META-INF/*.DSA" "META-INF/versions/*" module-info.class 2>/dev/null || true
done
jar cf "$work/server-bundle-$mc.jar" -C "$work/merge" .
mvn -q -B install:install-file -Dfile="$work/server-bundle-$mc.jar" -DgroupId=local.aspaper \
  -DartifactId=server-bundle -Dversion="$mc" -Dpackaging=jar -DgeneratePom=true
echo "installed local.aspaper:server-bundle:$mc"
