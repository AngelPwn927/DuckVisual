import base64,re,os,sys
S=os.path.dirname(os.path.abspath(__file__))
out=S
src=open(S+'/src.html').read()
base='https://github.com/dima6546446-boop/Xd/raw/claude/modest-maxwell-nbqvn1/'
src=src.replace('{{EXE}}',base+'DuckLauncher/dist/Duck%20Launcher.exe').replace('{{JAR}}',base+'DuckVisual/release/duck-visual-1.0.0.jar')
src=re.sub(r'\{\{IMG:(\w+)\}\}',lambda m:'data:image/jpeg;base64,'+base64.b64encode(open(f'{S}/{m.group(1)}.jpg','rb').read()).decode(),src)

e=src.index('</style>')+8
full='<!doctype html>\n<html lang="ru">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n<meta name="description" content="Duck Visual — визуалы для Minecraft 1.21.4 и Duck Launcher.">\n'+src[:e]+'\n</head>\n<body>\n'+src[e:]+'\n</body>\n</html>\n'
open(out+'/index.html','w').write(full)
