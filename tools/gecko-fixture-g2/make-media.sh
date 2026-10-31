#!/usr/bin/env bash
# Synthetic MP4 only; never touches Android or user storage.
set -euo pipefail
command -v ffmpeg >/dev/null
command -v ffprobe >/dev/null
root="$(cd "$(dirname "$0")" && pwd)"
folder="$root/site/media"
mkdir -p "$folder"
ffmpeg -nostdin -hide_banner -loglevel error -y -f lavfi -i 'color=c=blue:s=160x90:r=30' -t 2 -an -c:v libx264 -profile:v baseline -pix_fmt yuv420p -movflags +faststart "$folder/a.mp4"
ffmpeg -nostdin -hide_banner -loglevel error -y -f lavfi -i 'color=c=red:s=320x180:r=30' -t 5 -an -c:v libx264 -profile:v baseline -pix_fmt yuv420p -movflags +faststart "$folder/b.mp4"
ffprobe -v error -show_entries stream=width,height -show_entries format=duration -of json "$folder/a.mp4"
ffprobe -v error -show_entries stream=width,height -show_entries format=duration -of json "$folder/b.mp4"
echo "Generated only ignored synthetic files in site/media."
