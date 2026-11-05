#!/usr/bin/env python3
"""Prepare test-only WhatsApp folders on an emulator; authorize their parent in the app."""
import argparse
import shlex
import subprocess
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--adb',default='adb')
args=parser.parse_args()
assets=Path(__file__).resolve().parents[1]/'app/src/androidTest/assets'
base='/sdcard/Android/media/com.whatsapp/WhatsApp'
def adb(*command):
    subprocess.run([args.adb,*map(str,command)],check=True)
for path in ['Media/.Statuses','accounts/1002/Media/.Statuses','accounts/1007/Media/.Statuses']:
    adb('shell','mkdir -p '+shlex.quote(base+'/'+path))
for i in range(1,4):adb('push',assets/f'status{i}.jpg',base+f'/Media/.Statuses/status{i}.jpg')
adb('push',assets/'qa-video.mp4',base+'/Media/.Statuses/status-video.mp4')
for i in range(9):adb('push',assets/f'status{i%3+1}.jpg',base+f'/accounts/1002/Media/.Statuses/account-image{i:02}.jpg')
adb('push',assets/'qa-video.mp4',base+'/accounts/1002/Media/.Statuses/account-video.mp4')
adb('push',assets/'status3.jpg',base+'/accounts/1007/Media/.Statuses/account-last.jpg')
print('Ready: 13 images and 2 videos. Authorize the WhatsApp parent folder in the app.')
