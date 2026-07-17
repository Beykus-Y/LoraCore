# LoraCore 2.0.0 — Autonomous Native LoraOS

## English

LoraCore expands Minecraft with an OpenAI-compatible assistant, AI-generated
world details, villager dialogue and quests, and a programmable virtual tablet
powered by the custom Lora-1 architecture.

### Features

- **AI assistant:** run `/ask` to open a dedicated chat interface with persistent
  conversation history.
- **Dynamic structures:** discovered structures receive AI-generated names and
  descriptions based on their type and biome.
- **Villager personalities:** villagers can receive unique names, personalities
  and contextual dialogue.
- **Quest system:** accept item-delivery quests from villagers, earn rewards and
  track active quests in a dedicated quest log.
- **LoraCore tablet:** boot LoraOS inside a server-authoritative virtual computer
  with a 32-bit CPU, RAM, storage, BIOS, bootloader, GPU and keyboard.
- **Hardware simulation:** CPU tiers, clock frequency, voltage, temperature and
  stability affect the virtual machine.
- **Modern VM architecture:** paging/MMU, hardware interrupts, memory-mapped I/O,
  persistent disks and synchronized tablet graphics.
- **Configurable AI provider:** use OpenAI or another compatible API endpoint and
  select the model from the in-game configuration screen.
- **Multilingual:** English, Russian and pre-reform Russian localization is
  included; AI output follows the player's language.

### What's new in 2.0.0

- Native LoraOS and Recovery on a bundled, immediately bootable factory disk.
- LoraBoot dualboot, persistent LoraFS and a usable command shell.
- Read-only World Bridge for safe survival-aware programs.
- Craftable tablet hardware and automatic backed-up migration of legacy disks.

### Usage

- Run `/ask` to open the AI chat.
- Sneak and right-click a villager to open the dialogue interface.
- Press `J` to open the quest log.
- Right-click with a tablet to boot it.
- Sneak and right-click with a tablet to inspect its hardware configuration.

An API key is required only for AI-powered features. The virtual computer and
other local systems can be used without an API key.

### Requirements

- Minecraft 1.20.6
- Fabric Loader 0.15.10 or newer
- Fabric API
- Java 21

Cardinal Components API modules are bundled with LoraCore. Mod Menu and Cloth
Config API are recommended for convenient in-game configuration.

### Installation

1. Install Fabric Loader and Fabric API for Minecraft 1.20.6.
2. Place `loracore-2.0.0.jar` in the Minecraft `mods` directory.
3. Optionally install Mod Menu and Cloth Config API.
4. Launch the game and configure the API URL, key and model if you want to use
   AI features.

Issues and source code: [GitHub](https://github.com/Beykus-Y/LoraCore)

Developer: **Beykus-Y**
License: **MIT**

---

## Русский

LoraCore расширяет Minecraft ИИ-помощником с поддержкой OpenAI-совместимых API,
динамическими деталями мира, диалогами и квестами жителей, а также
программируемым виртуальным планшетом на собственной архитектуре Lora-1.

### Возможности

- **ИИ-помощник:** команда `/ask` открывает отдельный интерфейс чата с историей
  переписки.
- **Динамические структуры:** найденные структуры получают созданные ИИ названия
  и описания с учётом типа и биома.
- **Личности жителей:** жители получают уникальные имена, характеры и контекстные
  диалоги.
- **Система квестов:** принимайте задания на доставку предметов, получайте награды
  и отслеживайте активные задания в журнале.
- **Планшет LoraCore:** запускайте LoraOS внутри серверной виртуальной машины с
  32-битным CPU, RAM, накопителем, BIOS, загрузчиком, GPU и клавиатурой.
- **Симуляция оборудования:** уровни CPU, частота, напряжение, температура и
  стабильность влияют на виртуальный компьютер.
- **Современная VM:** paging/MMU, аппаратные прерывания, MMIO, постоянные диски и
  синхронизация графики планшета.
- **Настраиваемый провайдер ИИ:** можно использовать OpenAI или другой
  совместимый API и выбрать модель в игровом меню настроек.
- **Локализация:** английский, русский и дореформенный русский; ответы ИИ
  адаптируются к языку игрока.

### Что нового в 2.0.0

- Нативные LoraOS и Recovery на встроенном готовом к загрузке диске.
- Dualboot LoraBoot, постоянная LoraFS и полноценный командный shell.
- Read-only World Bridge для безопасного взаимодействия в выживании.
- Крафтовое железо и миграция старых дисков с обязательной резервной копией.

### Использование

- `/ask` — открыть чат с ИИ.
- Shift+ПКМ по жителю — открыть диалог.
- `J` — открыть журнал квестов.
- ПКМ с планшетом — загрузить планшет.
- Shift+ПКМ с планшетом — открыть конфигурацию его оборудования.

API-ключ требуется только функциям ИИ. Виртуальный компьютер и остальные
локальные системы могут работать без него.

### Требования

- Minecraft 1.20.6
- Fabric Loader 0.15.10 или новее
- Fabric API
- Java 21

Модули Cardinal Components API включены в LoraCore. Для удобной настройки в игре
рекомендуются Mod Menu и Cloth Config API.

### Установка

1. Установите Fabric Loader и Fabric API для Minecraft 1.20.6.
2. Поместите `loracore-2.0.0.jar` в папку `mods`.
3. При желании установите Mod Menu и Cloth Config API.
4. Запустите игру и настройте URL API, ключ и модель, если нужны функции ИИ.

Исходный код и сообщения об ошибках: [GitHub](https://github.com/Beykus-Y/LoraCore)

Разработчик: **Beykus-Y**
Лицензия: **MIT**
