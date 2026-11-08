import sys; sys.path.insert(0,'.')
import rt, render, numpy as np, matplotlib.pyplot as plt
b=rt.Body('corps2.bin'); render.G_idx=[g['idx'] for g in b.G]
def mir(d):
    Q={}
    for k,v in d.items():
        Q[k]=v
        if k.endswith('1') and k[:-1] in ('E','W','F','U','K','A','T','Sh'):
            w=list(v); w[0]=-w[0]; Q[k[:-1]+'2']=w
        if k=='c1': Q['c2']=v
    return Q
push=mir({'P':[0,30,0],'S':[0,32,25],'N':[0,34,45],'H':[0,38,58],'E1':[22,18,42],'W1':[20,4,44],'F1':[20,1,53],'U1':[0,-1,0],'c1':.05,
          'K1':[9,18,-40],'A1':[10,9,-78],'T1':[10,1,-75]})
curl=mir({'P':[0,76,0],'S':[0,104,-3],'N':[0,114,0],'H':[0,130,6],'E1':[19,92,-2],'W1':[19,98,22],'F1':[19,102,29],'U1':[0,1,-.3],'c1':.9,
          'K1':[9,41,2],'A1':[11,6,1]})
fig,axs=plt.subplots(1,4,figsize=(12,4))
for i,(Q,az) in enumerate([(push,-70),(push,-20),(curl,-60),(curl,-20)]):
    out,R,T=b.skin(Q); render.view(out,az=az,ax=axs[i])
    if Q is push: axs[i].set_xlim(-80,80); axs[i].set_ylim(-5,80)
    else: axs[i].set_xlim(-40,60); axs[i].set_ylim(50,140)
plt.tight_layout(); plt.savefig('test1.png',dpi=80); print('ok')
