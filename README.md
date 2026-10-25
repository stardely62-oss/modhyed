# Modhyed

![Скриншот последней сборки](https://github.com/stardely62-oss/modhyed/releases/download/dev/screenshot.png)

Fabric-мод для **Minecraft 1.20.1** от euf. Пока что это тестовый мод для проверки всей связки:
- **Эуф-кристалл.** Рецепт: изумруд + осколок аметиста → 2 кристалла.
- **Эуф-блок.** Крафтится из 9 кристаллов, светится и разбирается обратно на 9 кристаллов.

Мод работает с лицензией и без неё, в любом лаунчере с Fabric.

## Установка (TLauncher)

1. В TLauncher выбери версию **Fabric 1.20.1** и запусти её один раз до главного меню, потом закрой.
2. Открой [последнюю сборку](https://github.com/stardely62-oss/modhyed/releases/tag/dev) и скачай `modhyed-updater.zip`.
3. Распакуй архив куда угодно и запусти **`update-modhyed.bat`**.
4. Скрипт сам:
   - найдёт папку `mods`;
   - определит версию Fabric Loader и поставит подходящий Fabric API;
   - скачает свежий мод, а старую версию удалит.
5. Запускай в TLauncher **Fabric 1.20.1**.

Чтобы обновиться, просто запусти `update-modhyed.bat` ещё раз.

**Варианты запуска** (в PowerShell или из ярлыка):
- `update-modhyed.bat -Stable`: последний стабильный релиз вместо dev-сборки.
- `update-modhyed.bat -VersionName "Fabric 1.20.1 (2)"`: если версия в TLauncher называется иначе.

**Ручная установка:** положи в `%APPDATA%\.minecraft\mods` jar мода из релиза и [Fabric API для 1.20.1](https://modrinth.com/mod/fabric-api/versions?g=1.20.1).

## Как устроена разработка

- Код пишется агентом (Claude Code + [Universal Modder](https://github.com/rehan-remade/universal-modder)).
- Сборка и проверка идут только в GitHub Actions ([workflow](.github/workflows/build.yml)):
  1. `./gradlew build` собирает jar.
  2. `./gradlew runGametest` запускает headless-сервер и прогоняет [GameTest-ы](src/gametest/java/euf/modhyed/test/ModhyedGameTests.java).
  3. `scripts/ci-harness.sh` запускает настоящий клиент под Xvfb. Он заходит в мир, ставит блок настоящим кликом, проверяет результат и делает скриншот.
  4. При зелёном `main` обновляется релиз [`dev`](https://github.com/stardely62-oss/modhyed/releases/tag/dev). Тег `v*` выпускает стабильный релиз.
- Логи, скриншоты и отчёты каждого запуска лежат в артефактах на вкладке Actions.
- Текстуры рисует скрипт [`tools/gen_textures.py`](tools/gen_textures.py).

## Лицензия

MIT
