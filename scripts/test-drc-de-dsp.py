#!/usr/bin/env python3
"""Real system FFmpeg DSP; source graphs are exactly the shipped Lua revisions.
No Android/libmpv or listening claim. All inputs are synthetic.
"""
import array, hashlib, pathlib, re, subprocess
root=pathlib.Path(__file__).resolve().parents[1]
s=(root/'app/src/main/assets/mpv/zavro_drc_de_repair.lua').read_text()
graphs=re.findall(r"'(acompressor=[^']+|dialoguenhance=[^']+)'",s)
assert len(graphs)==8
signal='aevalsrc=0.2*sin(2*PI*900*t)+0.03*sin(2*PI*4000*t)|0.2*sin(2*PI*900*t)-0.03*sin(2*PI*4000*t):s=48000:d=0.5'
def dsp(graph):
 r=subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i',signal,'-af',graph,'-ac','2','-f','f64le','-acodec','pcm_f64le','pipe:1'],capture_output=True,timeout=20)
 assert r.returncode==0,'Synthetic DSP graph rejected'
 a=array.array('d');a.frombytes(r.stdout)
 assert len(a)>10000 and all(-1<x<1 for x in a),'Invalid/clipped synthetic DSP output'
 return hashlib.sha256(r.stdout).hexdigest()
base=dsp('anull');drc=[];de=[]
for graph in graphs:
 digest=dsp(graph);assert digest!=base
 (drc if graph.startswith('acompressor') else de).append(digest)
assert len(set(drc))==3 and len(set(de))==5,'Distinct parameters must cause distinct real DSP outputs'
# Both activation orders, foreign compressor + EQ survive graph composition. No arnndn
# model is published; original private model/voice tests remain local and separate.
extra=['acompressor=ratio=1.2:threshold=0.9', 'equalizer=f=14000:t=q:w=1:g=4']
for a,b in [(graphs[0],graphs[3]),(graphs[3],graphs[0]),(graphs[2],graphs[7]),(graphs[7],graphs[2])]:
 dsp(','.join(extra+[a,b]))
# Unsupported/bad graphs must actually fail DSP, not just return a synthetic ACK.
for bad in ['zavro_nonexistent_filter_v1158','dialoguenhance=enhance=99','acompressor=ratio=0']:
 r=subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i',signal,'-af',bad,'-f','null','-'],capture_output=True,timeout=20)
 assert r.returncode!=0,'Expected native graph rejection'
print('15/15 real system FFmpeg DSP cases PASS: 8 distinct levels, 4 mixed graphs, 3 rejections. Android DSP/listening NOT RUN.')
