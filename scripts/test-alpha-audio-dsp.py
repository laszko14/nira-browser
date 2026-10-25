#!/usr/bin/env python3
"""Synthetic sine through the exact canonical five-band graphs in system FFmpeg.
No device claim: Android libavfilter still needs AlphaAudioDeviceTest + listening.
"""
import array
import math
import pathlib
import re
import subprocess

root=pathlib.Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/prirai/android/nira/media/alpha/audio/RexEqualizerAudio.kt').read_text()
frequencies=[int(x) for x in re.search(r'frequencies = listOf\(([^)]+)\)',source)[1].split(',')]
presets=re.findall(r'"([^"]+)" to listOf\(([^)]+)\)',source)

def rms(graph=None):
    args=['ffmpeg','-v','error','-f','lavfi','-i','sine=frequency=14000:sample_rate=48000:duration=0.3']
    if graph:args+=['-af',graph]
    args+=['-f','f64le','-acodec','pcm_f64le','pipe:1']
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=False,timeout=15)
    if result.returncode:raise AssertionError('Synthetic filter processing failed')
    samples=array.array('d');samples.frombytes(result.stdout)
    assert len(samples)>1000
    return math.sqrt(sum(x*x for x in samples)/len(samples))

baseline=rms()
for name,gains in presets:
    values=[int(x.strip()) for x in gains.split(',')]
    graph=','.join(f'equalizer=f={f}:t=q:w=1:g={g}' for f,g in zip(frequencies,values))
    value=rms(graph)
    assert math.isfinite(value) and value>0
    if name=='Flat':assert abs(value/baseline-1)<.002
    if name=='Treble Boost':assert 1.4<value/baseline<2.1
    print('PASS:',name)
print(f'{len(presets)} FFmpeg DSP graphs passed; Treble Boost changes RMS. System FFmpeg, not Android.')
