import base64,re,os,sys
S=os.path.dirname(os.path.abspath(__file__))
src=open(S+'/src.html').read()
base='https://github.com/dima6546446-boop/Xd/raw/claude/modest-maxwell-nbqvn1/'
rel='https://github.com/AngelPwn927/DuckVisual/releases/latest/download/'
src=src.replace('{{EXE}}',rel+'DuckLauncher.exe').replace('{{JAR}}',rel+'duck-visual.jar')
a='ChinaHat TargetESP JumpCircles Trails Trajectories HitParticles DamageIndicators CritIndicator Hitmarker KillEffects GlowESP Nametags HealthTags BoxESP HitBoxes Tracers Arrows CustomModels Freelook Zoom MotionBlur'.split()
b='Watermark ArrayList TargetHud Music Cooldowns Potions Keybinds ArmorHud CustomHotbar TabList ScoreboardHud CustomSky WorldColor WorldParticles ChunkAnimator BreakEffects ItemPhysics Theme ClickGui CustomFont MainMenu'.split()
mq=lambda l:''.join('<span>%s</span>'%x for x in l*2)
src=src.replace('{{MQ1}}',mq(a)).replace('{{MQ2}}',mq(b))
src=re.sub(r'\{\{IMG:(\w+)\}\}',lambda m:'data:image/jpeg;base64,'+base64.b64encode(open(f'{S}/{m.group(1)}.jpg','rb').read()).decode(),src)

e=src.index('</style>')+8
full='<!doctype html>\n<html lang="ru">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n<meta name="description" content="Duck Visual — визуалы для Minecraft 1.21.4 и Duck Launcher.">\n'+src[:e]+'\n</head>\n<body>\n'+src[e:]+'\n</body>\n</html>\n'
open(S+'/index.html','w').write(full)
