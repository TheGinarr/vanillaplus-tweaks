"""Prueft jede externe Methode/jedes Feld im kompilierten Plugin gegen den echten Quellcode
(Paper-API 26.3, Adventure 5.2.0, PlaceholderAPI 2.12.3): Name, Parameteranzahl, Parametertypen, Rueckgabetyp."""
import os, re, subprocess, sys, glob, functools
ROOTS=['/home/claude/papermc/paper/paper-api/src/main/java',
       '/home/claude/repos/adventure/api/src/main/java','/home/claude/repos/adventure/key/src/main/java',
       '/home/claude/repos/adventure/text-serializer-plain/src/main/java',
       '/home/claude/repos/PlaceholderAPI/src/main/java']
CLASSES=sys.argv[1]

def find_src(fqn):
    top=fqn.split('$')[0]
    for r in ROOTS:
        p=os.path.join(r,top+'.java')
        if os.path.exists(p): return p
    return None

def strip_comments(s):
    s=re.sub(r'/\*.*?\*/','',s,flags=re.S); s=re.sub(r'//[^\n]*','',s); return s

def strip_annotations(s):
    # remove annotations incl. args, also type-use annotations like @NotNull, org.jetbrains.annotations.Range(from=..)
    prev=None
    while prev!=s:
        prev=s
        s=re.sub(r'@[\w.]+\s*\((?:[^()]|\([^()]*\))*\)','',s)
    s=re.sub(r'@[\w.]+','',s)
    return s

def class_body(src, fqn):
    """Text des (ggf. verschachtelten) Typs."""
    parts=fqn.split('/')[-1].split('$')
    text=src
    for name in parts[1:]:
        m=re.search(r'\b(class|interface|enum|record)\s+'+name+r'\b',text)
        if not m: return None
        i=text.index('{',m.end()); depth=0; j=i
        while True:
            if text[j]=='{': depth+=1
            elif text[j]=='}':
                depth-=1
                if depth==0: break
            j+=1
        text=text[m.start():j+1]
    return text

def header_of(body, simple):
    m=re.search(r'\b(class|interface|enum|record)\s+'+simple+r'\b[^{]*\{',body)
    return m.group(0) if m else ''

def imports(src):
    return dict((x.split('.')[-1],x.replace('.','/')) for x in re.findall(r'^import\s+([\w.]+)\s*;',src,re.M))

def resolve(simple, src, fqn):
    simple=simple.strip()
    if '.' in simple:
        q=simple.replace('.','/')
        # nested via outer simple name?
        first=simple.split('.')[0]
        imp=imports(src)
        if first in imp: return imp[first]+'$'+'$'.join(simple.split('.')[1:])
        if find_src(q): return q
        pkg='/'.join(fqn.split('/')[:-1])
        if find_src(pkg+'/'+first): return pkg+'/'+first+'$'+'$'.join(simple.split('.')[1:])
        return q
    imp=imports(src)
    if simple in imp: return imp[simple]
    pkg='/'.join(fqn.split('/')[:-1])
    if find_src(pkg+'/'+simple): return pkg+'/'+simple
    # nested type of the same outer file
    outer=fqn.split('$')[0]
    if re.search(r'\b(class|interface|enum|record)\s+'+simple+r'\b',src): return outer+'$'+simple
    return 'java/lang/'+simple

@functools.lru_cache(maxsize=None)
def supertypes(fqn):
    p=find_src(fqn)
    if not p: return []
    src=strip_annotations(strip_comments(open(p).read()))
    body=class_body(src,fqn) if '$' in fqn else src
    if body is None: return []
    simple=fqn.split('/')[-1].split('$')[-1]
    h=header_of(body,simple)
    h=re.sub(r'<[^<>]*(<[^<>]*>[^<>]*)*>','',h)  # generics weg
    res=[]
    for kw in ('extends','implements'):
        m=re.search(r'\b'+kw+r'\s+([\w.,\s]+?)(?=\bimplements\b|\bpermits\b|\{)',h)
        if m:
            for t in m.group(1).split(','):
                t=t.strip()
                if t: res.append(resolve(t,src,fqn))
    if 'enum ' in h: res.append('java/lang/Enum')
    return res

PRIM={'V':'void','Z':'boolean','B':'byte','C':'char','S':'short','I':'int','J':'long','F':'float','D':'double'}
def parse_desc(d):
    out=[];i=0
    while i<len(d):
        dims=0
        while d[i]=='[': dims+=1;i+=1
        if d[i]=='L':
            j=d.index(';',i); t=d[i+1:j].split('/')[-1].split('$')[-1]; i=j+1
        else: t=PRIM[d[i]]; i+=1
        out.append(t+'[]'*dims)
    return out

def norm_type(t, typevars):
    t=re.sub(r'\.\s+','.',t)  # Typ-Annotationen wie "pkg.@Nullable Typ" hinterlassen "pkg. Typ"
    t=re.sub(r'<.*>','',t).strip()
    t=t.replace('...','[]').replace('final ','').strip()
    base=t.rstrip('[]'); dims=t[len(base):]
    base=base.split('.')[-1]
    if base in typevars: base=typevars[base]
    return base+dims

import functools
@functools.lru_cache(maxsize=None)
def methods_in(fqn):
    p=find_src(fqn)
    if not p: return None
    src=strip_annotations(strip_comments(open(p).read()))
    body=class_body(src,fqn) if '$' in fqn else src
    if body is None: return None
    found=[]
    # type variables of class
    simple=fqn.split('/')[-1].split('$')[-1]
    h=header_of(body,simple)
    tv={}
    mh=re.search(simple+r'\s*<(.*?)>\s*(extends|implements|permits|\{)',h)
    if mh:
        for part in re.split(r',(?![^<]*>)',mh.group(1)):
            mm=re.match(r'\s*(\w+)(?:\s+extends\s+([\w.]+))?',part)
            if mm: tv[mm.group(1)]=(mm.group(2) or 'Object').split('.')[-1]
    # Linearer Scan: jedes "name(" pruefen, Text davor bis zum letzten ; { } ist Modifier + Rueckgabetyp
    for m in re.finditer(r'(\w+)\s*\(',body):
        name=m.group(1)
        if name in ('if','for','while','switch','return','new','catch','synchronized','super','this','throw'): continue
        k=m.start()-1
        while k>=0 and body[k] not in ';{}=': k-=1
        if k>=0 and body[k]=='=': continue
        pre=body[k+1:m.start()].strip()
        if not pre or pre.endswith('.') or pre.endswith('new') or 'return' in pre.split(): continue
        # Parameter bis zur passenden Klammer
        depth=0;j=m.end()-1;params=''
        while j<len(body):
            ch=body[j]
            if ch=='(': depth+=1
            elif ch==')':
                depth-=1
                if depth==0: break
            if depth>=1 and not (ch=='(' and depth==1): params+=ch
            j+=1
        after=body[j+1:j+60].lstrip()
        if not (after.startswith('{') or after.startswith(';') or after.startswith('throws') or after.startswith('default')): continue
        mtvm=re.match(r'.*?(<[^()]*>)\s*[\w.\[\]<>?,\s]+$',pre)
        ltv=dict(tv)
        gm=re.search(r'(?:^|\s)<(.+?)>\s+\S',pre)
        if gm:
            for part in re.split(r',(?![^<]*>)',gm.group(1)):
                mm=re.match(r'\s*(\w+)(?:\s+extends\s+([\w.]+))?',part)
                if mm: ltv[mm.group(1)]=(mm.group(2) or 'Object').split('.')[-1]
        ret=re.sub(r'<[^<>]*(<[^<>]*>[^<>]*)*>','',pre).split()
        ret=ret[-1] if ret else ''
        plist=[]
        if params.strip():
            depth=0;cur=''
            for ch in params:
                if ch=='<': depth+=1
                if ch=='>': depth-=1
                if ch==',' and depth==0: plist.append(cur);cur=''
                else: cur+=ch
            plist.append(cur)
        types=[]
        for pp in plist:
            pp=re.sub(r'\s+',' ',pp.strip())
            toks=pp.rsplit(' ',1)
            types.append(norm_type(toks[0],ltv))
        found.append((name,types,norm_type(ret,ltv)))
    # record components & enum constants & fields
    return found, body, src

def has_field(fqn,name):
    r=methods_in(fqn)
    if not r: return False
    _,body,_=r
    return re.search(r'\b'+name+r'\b\s*(=|,|;|\()',body) is not None or re.search(r'^\s*'+name+r'\b',body,re.M) is not None

def check_method(fqn,name,desc,seen=None):
    seen=seen or set()
    if fqn in seen: return False
    seen.add(fqn)
    if fqn.startswith('java/') or fqn.startswith('com/sun/'): return True  # JDK
    if name in ('equals','hashCode','toString') and desc in ('(Ljava/lang/Object;)Z','()I','()Ljava/lang/String;'): return True  # von Object geerbt
    r=methods_in(fqn)
    if r is None: return None
    found,body,src=r
    want_params=parse_desc(desc[1:desc.index(')')]); want_ret=parse_desc(desc[desc.index(')')+1:])[0]
    simple=fqn.split('/')[-1].split('$')[-1]
    if name=='<init>':
        for n,ps,rt in [(n,ps,rt) for (n,ps,rt) in found if n==simple]+[(simple,ps,'') for (n,ps,rt) in []]:
            if ps==want_params: return True
        # constructors are matched by "simple(" pattern; methods_in requires a return token, so scan explicitly
        for m in re.finditer(r'\b'+simple+r'\s*\(([^()]*)\)\s*(throws[^{]*)?\{',body):
            params=m.group(1)
            types=[norm_type(re.sub(r'\s+',' ',p.strip()).rsplit(' ',1)[0],{}) for p in params.split(',')] if params.strip() else []
            if types==want_params: return True
        # keine eigenen Konstruktoren -> Java erzeugt automatisch einen leeren
        if want_params==[] and not re.search(r'\b'+simple+r'\s*\(',body.split('{',1)[1] if '{' in body else ''):
            return True
        return False
    for n,ps,rt in found:
        if n==name and len(ps)==len(want_params):
            ok=all(a==b or a=='Object' for a,b in zip(ps,want_params))
            if ok and (rt==want_ret or rt=='Object' or want_ret=='Object'): return True
            if ok: return ('RET',rt,want_ret)
    for sup in supertypes(fqn):
        res=check_method(sup,name,desc,seen)
        if res is True: return True
    return False

out=subprocess.run(['bash','-c',f"cd {CLASSES} && for c in $(find . -name '*.class'); do javap -v -p \"$c\"; done"],capture_output=True,text=True).stdout
refs=set(re.findall(r'= (Methodref|InterfaceMethodref|Fieldref)\s+#\d+\.#\d+\s+// ([\w/$]+)\.("?[\w<>$]+"?):(\S+)',out))
bad=0;checked=0
for kind,owner,name,desc in sorted(refs):
    if owner.startswith('java/') or owner.startswith('vanillaplustweaks/'): continue
    name=name.strip('"')
    checked+=1
    if kind=='Fieldref':
        ok=has_field(owner,name)
    else:
        ok=check_method(owner,name,desc)
    if ok is not True:
        bad+=1; print('PROBLEM',kind,owner,name,desc,ok)
print(f'{checked} externe Verweise geprueft, {bad} Probleme')
