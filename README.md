<div align="center">

<img src="docs/images/banner.png" alt="Duck Visual" width="100%">

# 🦆 Duck Visual

**Визуалы для Minecraft 1.21.4 и лаунчер, который ставит всё сам.**
**Бесплатно.**

[![Скачать](https://img.shields.io/badge/%D0%A1%D0%BA%D0%B0%D1%87%D0%B0%D1%82%D1%8C-DuckLauncher.exe-ffb52e?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/AngelPwn927/DuckVisual/releases/latest/download/DuckLauncher.exe)
[![Telegram](https://img.shields.io/badge/Telegram-@DuckVisual-2aabee?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/duckvisual)

[![Release](https://img.shields.io/github/v/release/AngelPwn927/DuckVisual?color=ffb52e&label=%D0%B2%D0%B5%D1%80%D1%81%D0%B8%D1%8F)](https://github.com/AngelPwn927/DuckVisual/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/AngelPwn927/DuckVisual/total?color=ff8a1f&label=%D1%81%D0%BA%D0%B0%D1%87%D0%B8%D0%B2%D0%B0%D0%BD%D0%B8%D0%B9)](https://github.com/AngelPwn927/DuckVisual/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-62b47a)
![Fabric](https://img.shields.io/badge/Fabric-0.16+-dbb68f)

**Русский** · [English](README.en.md)

</div>

---

## ⚡ Как начать

1. Скачай **[DuckLauncher.exe](https://github.com/AngelPwn927/DuckVisual/releases/latest/download/DuckLauncher.exe)** (Windows 10/11, без установки).
2. Запусти. Если Windows пишет «защитил ваш компьютер», нажми **«Подробнее» → «Выполнить в любом случае»**: у файла пока нет платной подписи.
3. Введи ник и нажми **ИГРАТЬ**. Лаунчер сам скачает Java, Minecraft, Fabric, Duck Visual и моды для FPS.
4. В игре нажми **Right Shift**, откроется меню Duck Visual.

> Уже есть свой лаунчер? Скачай [`duck-visual.jar`](https://github.com/AngelPwn927/DuckVisual/releases/latest/download/duck-visual.jar) и положи в `mods` вместе с [Fabric API](https://modrinth.com/mod/fabric-api) (Fabric 1.21.4).

## ✨ Duck Visual

<table>
<tr>
<td width="50%"><img src="docs/images/menu.jpg" alt="Меню Duck Visual"><br><sub><b>Своё меню</b> на Right Shift: поиск, бинды, настройки каждой функции, 26 палитр</sub></td>
<td width="50%"><img src="docs/images/target.jpg" alt="TargetESP и TargetHud"><br><sub><b>TargetESP + TargetHud</b>: подсветка цели, HP-бар, частицы при ударе</sub></td>
</tr>
<tr>
<td><img src="docs/images/esp.jpg" alt="BoxESP"><br><sub><b>BoxESP, HitBoxes, Cooldowns</b>: видно мобов, игроков и кулдауны</sub></td>
<td><img src="docs/images/custom-model.jpg" alt="CustomModels"><br><sub><b>CustomModels</b>: утка, амогус, кролик или пингвин вместо скина</sub></td>
</tr>
</table>

**61 функция**, все можно настроить и забиндить:

| Бой | Игроки и мир | HUD | Стиль |
|---|---|---|---|
| TargetESP · Trajectories · CritIndicator · DamageIndicators · Hitmarker · HitParticles · KillEffects · TargetCircle | Nametags · HealthTags · GlowESP · BoxESP · Tracers · ChinaHat · JumpCircles · CustomSky · WorldParticles | Watermark · ArrayList · TargetHud · **Music** · Cooldowns · Potions · Keybinds · ArmorHud · TabList | 26 палитр · шрифт Montserrat · меню Minecraft в стиле Duck · Freelook · Zoom · MotionBlur |

🎵 **Music HUD** показывает, что играет в браузере (YouTube, Яндекс Музыка, VK, SoundCloud), Spotify и других плеерах.

## 🚀 Duck Launcher

<table>
<tr>
<td width="33%"><img src="docs/images/launcher.jpg" alt="Duck Launcher"></td>
<td width="33%"><img src="docs/images/launcher-mods.jpg" alt="Каталог модов"></td>
<td width="33%"><img src="docs/images/launcher-servers.jpg" alt="Серверы"></td>
</tr>
</table>

- **Всё ставится само:** Java нужной версии, Minecraft, Fabric, Duck Visual.
- **Любая версия** от 1.8.9 до последней, Fabric или Vanilla, у каждой своя папка.
- **17 модов для FPS:** Sodium, Lithium, ModernFix, FerriteCore, EntityCulling и другие, каждый можно выключить.
- **Профиль «Максимум FPS»**, мощная видеокарта на ноутбуках, запуск игры с модами примерно за 20 секунд.
- **Каталог модов** Modrinth и CurseForge прямо в лаунчере, нужные библиотеки ставятся сами.
- **Вход без лицензии** или через **Ely.by** (скины).
- **Duck Server** `n1.pg19.su:25795` уже в списке серверов.

## 🛠 Сборка из исходников

| Папка | Что это | Как собрать |
|---|---|---|
| [`DuckVisual`](DuckVisual) | мод (Java 21, Fabric) | `./gradlew build` |
| [`DuckLauncher`](DuckLauncher) | лаунчер (Go 1.24, WebView2) | `./build.sh` |

## 💛 Поддержать

Duck Visual бесплатный и таким останется.
- Поставь ⭐ этому репозиторию, так его увидит больше людей.
- Поддержи звёздами в Telegram: [t.me/duckvisual/2](https://t.me/duckvisual/2).
- Расскажи друзьям и кидай идеи и баги в [канал](https://t.me/duckvisual).

---

<sub>Duck Visual и Duck Launcher не являются продуктами Mojang AB и не связаны с Mojang AB или Microsoft. Minecraft — товарный знак Mojang AB. Игровые файлы загружаются с официальных серверов Mojang.</sub>
