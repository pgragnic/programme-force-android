import sys; sys.path.insert(0,'.')
import rt, render, numpy as np, matplotlib.pyplot as plt
exec(open('test1.py').read().split('fig,axs')[0])
def hz(Q, az, el, ax):
    out,R,T=b.skin(Q); g=b.G[0]; m=(g['bi'][:,0]==b.idx['handR']); c=out[0][0][m].mean(0)
    a,e=np.radians(az),np.radians(el)
    Ry=np.array([[np.cos(a),0,np.sin(a)],[0,1,0],[-np.sin(a),0,np.cos(a)]]); Rx=np.array([[1,0,0],[0,np.cos(e),-np.sin(e)],[0,np.sin(e),np.cos(e)]])
    p=(Rx@Ry)@c
    render.view(out,az=az,el=el,ax=ax); ax.set_xlim(p[0]-14,p[0]+14); ax.set_ylim(p[1]-12,p[1]+16)
fig,axs=plt.subplots(2,3,figsize=(9,6))
for j,(az,el) in enumerate([(-90,10),(0,10),(-40,40)]):
    hz(push,az,el,axs[0][j]); hz(curl,az,el,axs[1][j])
plt.tight_layout(); plt.savefig('zoom.png',dpi=70)
