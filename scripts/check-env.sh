#!/usr/bin/env bash
# Checks that the tools PolyFrames needs are installed, and that none of
# them is a Windows executable reached through a WSL mount. Run this first.
set -u

status=0

report () {           # $1 = command, $2 = version command
  printf '%-9s ' "$1"
  local p
  p="$(command -v "$1" 2>/dev/null || true)"
  if [ -z "$p" ]; then
    printf 'MISSING\n'
    status=1
    return
  fi
  case "$p" in
    /mnt/[a-z]/*)
      printf 'WINDOWS  %s\n' "$p"
      printf '          ^ this is the Windows executable, reached through the\n'
      printf '            WSL mount. Install the Linux one instead.\n'
      status=1
      return
      ;;
  esac
  printf '%-8s %s\n' "ok" "$($2 2>&1 | head -n 1)"
}

echo "== tools =="
report java    "java -version"
report javac   "javac -version"
report sbt     "sbt --script-version"
report curl    "curl --version"
report git     "git --version"
report python3 "python3 --version"

echo
echo "== platform =="
if grep -qi microsoft /proc/version 2>/dev/null; then
  echo "WSL       yes"
  printf 'memory    %s\n' "$(awk '/MemTotal/ {printf "%.1f GiB visible to WSL", $2/1048576}' /proc/meminfo)"
  echo "          Section 5 reports this figure, so do not change .wslconfig"
  echo "          between measurement runs."
else
  echo "WSL       no"
  printf 'memory    %s\n' "$(awk '/MemTotal/ {printf "%.1f GiB", $2/1048576}' /proc/meminfo)"
fi
printf 'cores     %s\n' "$(nproc)"
printf 'workdir   %s\n' "$(pwd)"
case "$(pwd)" in
  /mnt/[a-z]/*)
    echo "          ^ this is a Windows drive. Builds are much slower there."
    echo "            Move the project under your Linux home directory."
    status=1
    ;;
esac

echo
if [ "$status" -ne 0 ]; then
  echo "Not ready. See README.md, section Environment."
  exit 1
fi
echo "Ready. Next: sbt core/test:compile"
