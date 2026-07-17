# LoraCore 2.0.0 — Autonomous Native LoraOS

## English

LoraCore brings an OpenAI-compatible assistant, AI-generated world details,
villager dialogue and quests, and a complete virtual tablet computer to
Minecraft.

### Main features

- Open `/ask` for a dedicated AI chat with conversation history.
- Discover structures with AI-generated names and biome-aware descriptions.
- Sneak-right-click villagers to meet their generated personalities, talk and
  receive item-delivery quests.
- Track quests in a dedicated interface with the `J` key.
- Boot LoraOS on a virtual tablet with a custom 32-bit Lora-1 CPU, RAM, storage,
  BIOS, bootloader, GPU and keyboard.
- Explore CPU tiers, frequency, voltage, temperature and stability simulation.
- Use paging/MMU, hardware IRQs, memory-mapped devices and persistent virtual
  disks.
- Configure an OpenAI-compatible API URL, key and model in game.
- Play in English, Russian or pre-reform Russian.

### What's new in 2.0.0

- Native LoraOS and Recovery boot directly from the bundled factory disk.
- LoraBoot dualboot menu with automatic fallback.
- Persistent LoraFS commands: `ls`, `cat note` and `write note`.
- Read-only `world` command for player, target block and redstone information.
- Complete survival recipes; no API key or manual OS installation is required.

### Controls

- `/ask` — open AI chat.
- Sneak + right-click a villager — open villager dialogue.
- `J` — open the quest log.
- Right-click with a tablet — boot the virtual computer.
- Sneak + right-click with a tablet — open hardware configuration.

The API key is required only for AI features. It is not required for the virtual
computer or other local gameplay systems.

### Compatibility and dependencies

- **Minecraft:** 1.20.6
- **Loader:** Fabric Loader 0.15.10+
- **Required:** Fabric API and Java 21
- **Bundled:** Cardinal Components API modules
- **Recommended:** Mod Menu and Cloth Config API

### Installation

1. Install Fabric Loader and Fabric API for Minecraft 1.20.6.
2. Download `loracore-2.0.0.jar` and place it in the `mods` folder.
3. Optionally install Mod Menu and Cloth Config API.
4. Configure your API provider only if you want to use AI features.

Support, issues and source code:
[github.com/Beykus-Y/LoraCore](https://github.com/Beykus-Y/LoraCore)

Developer: **Beykus-Y** · License: **MIT**

---

## Русский

LoraCore добавляет в Minecraft ИИ-помощника с поддержкой OpenAI-совместимых API,
динамические детали мира, диалоги и квесты жителей, а также полноценный
виртуальный компьютер-планшет.

### Основные возможности

- Команда `/ask` открывает отдельный чат с ИИ и историей переписки.
- Структуры получают созданные ИИ названия и описания с учётом биома.
- Shift+ПКМ по жителю открывает диалог с уникальной личностью и квестами на
  доставку предметов.
- Клавиша `J` открывает журнал активных заданий.
- LoraOS работает внутри планшета с собственным 32-битным CPU Lora-1, RAM,
  накопителем, BIOS, загрузчиком, GPU и клавиатурой.
- Моделируются уровни CPU, частота, напряжение, температура и стабильность.
- Поддерживаются paging/MMU, аппаратные IRQ, MMIO и постоянные виртуальные диски.
- В игре настраиваются URL OpenAI-совместимого API, ключ и модель.
- Включены английская, русская и дореформенная русская локализации.

### Что нового в 2.0.0

- Нативные LoraOS и Recovery загружаются прямо со встроенного фабричного диска.
- Dualboot-меню LoraBoot с автоматическим резервным слотом.
- Постоянная LoraFS и команды `ls`, `cat note`, `write note`.
- Read-only команда `world` для позиции, блока под прицелом и redstone-сигнала.
- Полная цепочка survival-рецептов; API-ключ и ручная установка ОС не нужны.

### Управление

- `/ask` — открыть чат с ИИ.
- Shift+ПКМ по жителю — открыть диалог.
- `J` — открыть журнал квестов.
- ПКМ с планшетом — загрузить виртуальный компьютер.
- Shift+ПКМ с планшетом — открыть конфигурацию оборудования.

API-ключ требуется только функциям ИИ. Для виртуального компьютера и остальных
локальных игровых систем он не нужен.

### Совместимость и зависимости

- **Minecraft:** 1.20.6
- **Загрузчик:** Fabric Loader 0.15.10+
- **Обязательно:** Fabric API и Java 21
- **Включено в мод:** модули Cardinal Components API
- **Рекомендуется:** Mod Menu и Cloth Config API

### Установка

1. Установите Fabric Loader и Fabric API для Minecraft 1.20.6.
2. Скачайте `loracore-2.0.0.jar` и поместите его в папку `mods`.
3. При желании установите Mod Menu и Cloth Config API.
4. Настройте API-провайдер, только если хотите использовать функции ИИ.

Поддержка, ошибки и исходный код:
[github.com/Beykus-Y/LoraCore](https://github.com/Beykus-Y/LoraCore)

Разработчик: **Beykus-Y** · Лицензия: **MIT**
