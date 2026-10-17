# Woertliche Nachbildung der mcfunction-Listenoperationen aus armored_elytra (merge_enchantments / separate_enchantments)
import random, subprocess, sys
FIXED = len(sys.argv)>2
def merge(elytraEnch, chestEnch):
    elytraEnch=list(elytraEnch); chestEnch=list(chestEnch); final=[]
    chestRemaining=len(chestEnch); elytraTotal=len(elytraEnch)
    st={'found':0,'elytraRemaining':0,'elytraTotal':elytraTotal}
    def check_elytra():
        temp=elytraEnch[0][0]
        if temp==chestEnch[0][0]:  # success 0
            ev=elytraEnch[0][1]; cv=chestEnch[0][1]
            final.append(chestEnch[0] if cv>ev else elytraEnch[0])
            elytraEnch.pop(0); st['elytraTotal']-=1; chestEnch.pop(0); st['found']=1
        if st['found']==0: rotate_elytra()
    def rotate_elytra():
        elytraEnch.append(elytraEnch[0]); elytraEnch.pop(0); st['elytraRemaining']-=1
        if st['elytraRemaining']!=0: check_elytra()
    def shift_chest():
        nonlocal chestRemaining
        st['found']=0; st['elytraRemaining']=st['elytraTotal']
        if st['elytraTotal']!=0: check_elytra()
        if st['found']==0:
            final.append(chestEnch[0]); chestEnch.pop(0)
        chestRemaining-=1
        if chestRemaining!=0: shift_chest()
    def add_from_elytra():
        final.append(elytraEnch[-1]); elytraEnch.pop(); st['elytraTotal']-=1
        if st['elytraTotal']!=0: add_from_elytra()
    if chestRemaining!=0: shift_chest()
    if st['elytraTotal']!=0: add_from_elytra()
    d={}
    for k,v in final: d[k]=v
    return d
def separate(enchantments, elytraEnch, chestEnch):
    enchantments=list(enchantments); elytraEnch=list(elytraEnch); chestEnch=list(chestEnch)
    elytraFinal=[]; chestFinal=[]
    st={'elytraTotal':len(elytraEnch),'chestTotal':len(chestEnch),'elytraRemaining':0,'chestRemaining':0,'found':0,'remaining':0}
    def iterate_elytra():
        st['chestRemaining']=st['chestTotal']; iterate_chest()
        if st['elytraTotal']!=0 and st['chestTotal']!=0: rotate_elytra()
    def iterate_chest():
        temp=elytraEnch[0][0] if elytraEnch else None
        cid=chestEnch[0][0] if chestEnch else None
        success = 0 if (temp is not None and temp==cid) else 1
        if success==0: compare()
        if success!=0: rotate_chest()
    def compare():
        ev=elytraEnch[0][1]; cv=chestEnch[0][1]
        if cv>ev:
            elytraFinal.append(elytraEnch[0]); elytraEnch.pop(0); st['elytraTotal']-=1
        else:
            chestFinal.append(chestEnch[0]); chestEnch.pop(0); st['chestTotal']-=1
    def rotate_chest():
        chestEnch.append(chestEnch[0]); chestEnch.pop(0); st['chestRemaining']-=1
        if st['chestRemaining']!=0: iterate_chest()
    def rotate_elytra():
        elytraEnch.append(elytraEnch[0]); elytraEnch.pop(0); st['elytraRemaining']-=1
        if st['elytraRemaining']!=0: iterate_elytra()
    if FIXED:
        if elytraEnch and chestEnch:
            for (eid,ev) in list(elytraEnch):
                m=[x for x in chestEnch if x[0]==eid]
                if not m: continue
                cv=m[0][1]
                if cv>ev: elytraFinal.append((eid,ev)); elytraEnch.remove((eid,ev)); st['elytraTotal']-=1
                else: chestFinal.append(m[0]); chestEnch.remove(m[0]); st['chestTotal']-=1
    elif st['elytraTotal']!=0 and st['chestTotal']!=0:
        st['elytraRemaining']=st['elytraTotal']; iterate_elytra()
    def check_chest_one():
        temp=enchantments[0][0]
        cid=chestEnch[0][0] if chestEnch else None
        if temp==cid:
            st['found']=1; chestFinal.append(enchantments[0]); chestEnch.pop(0); st['chestTotal']-=1
        if st['found']==0: rotate_chest2()
    def rotate_chest2():
        chestEnch.append(chestEnch[0]); chestEnch.pop(0); st['chestRemaining']-=1
        if st['chestRemaining']!=0: check_chest_one()
    def shift():
        st['found']=0
        if st['chestTotal']!=0:
            st['chestRemaining']=st['chestTotal']; check_chest_one()
        if st['found']==0: elytraFinal.append(enchantments[0])
        enchantments.pop(0); st['remaining']-=1
        if st['remaining']!=0: shift()
    st['remaining']=len(enchantments)
    if st['remaining']!=0: shift()
    e={};c={}
    for k,v in elytraFinal: e[k]=v
    for k,v in chestFinal: c[k]=v
    return e,c
def fmt(d): return '{'+', '.join(f'{k}={d[k]}' for k in sorted(d))+'}'
def ser(l): return ','.join(f'{k}={v}' for k,v in l)
random.seed(1)
names=list('abcdefg'); cases=[]; expect=[]
for _ in range(4000):
    def rnd(): 
        ks=random.sample(names, random.randint(0,5)); return [(k,random.randint(1,5)) for k in ks]
    e=rnd(); c=rnd()
    if random.random()<0.5:
        cases.append('M|'+ser(e)+'|'+ser(c)); expect.append(fmt(merge(e,c)))
    else:
        cur=rnd()
        cases.append('S|'+ser(cur)+'|'+ser(e)+'|'+ser(c)); ee,cc=separate(cur,e,c); expect.append(fmt(ee)+' '+fmt(cc))
out=subprocess.run(['java','-cp',sys.argv[1],'Harness'],input='\n'.join(cases)+'\n',capture_output=True,text=True).stdout.strip().split('\n')
bad=[(cases[i],expect[i],out[i]) for i in range(len(cases)) if out[i]!=expect[i]]
print(len(cases),'Faelle,',len(bad),'Abweichungen')
for b in bad[:5]: print(b)
