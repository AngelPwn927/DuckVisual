import base64,re,sys,os
S=os.path.dirname(os.path.abspath(__file__))
src=open(S+'/src.html').read()
dl='https://github.com/dima6546446-boop/Xd/raw/claude/modest-maxwell-nbqvn1/DuckLauncher/dist/Duck%20Launcher.exe'
src=src.replace('{{DOWNLOAD}}',dl)
src=re.sub(r'\{\{IMG:(\w+)\}\}',lambda m:'data:image/jpeg;base64,'+base64.b64encode(open(f'{S}/{m.group(1)}.jpg','rb').read()).decode(),src)


full='<!doctype html>\n<html lang="ru">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n<meta name="description" content="Duck Launcher — лаунчер Minecraft с Duck Visual, модами для FPS и любыми версиями.">\n'
title_end=src.index('</style>')+len('</style>')
full+=src[:title_end]+'\n</head>\n<body>\n'+src[title_end:]+'\n</body>\n</html>\n'
open(S+'/index.html','w').write(full)
print(len(src))
