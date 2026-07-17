# LoraOS 2.0

LoraOS 2.0 is a native operating system for the Lora-1 virtual CPU. BIOS,
LoraBoot, LoraOS, Recovery and the initial disk image are bundled in the main
LoraCore JAR. No Java TabletOS or manual world-folder installation is used.

## Boot flow

1. Universal BIOS scans the PnP ROM and records GPU, disk and keyboard MMIO.
2. BIOS loads the 512-byte stage-1 sector from LBA 0.
3. Stage 1 loads LoraBoot stage 2 from LBA 1-4.
4. LoraBoot reads LDB2 configuration at LBA 5 and its table at LBA 6-7.
5. Key `1` boots LoraOS; key `2` boots Recovery. LoraOS is the timeout default.
6. If the selected image cannot be read, LoraBoot tries the other slot once.

## Shell commands

- `help` — list commands.
- `about` — show the native OS version.
- `hw` — show PnP device count and MMIO addresses.
- `clear` — clear the terminal.
- `ls` — list the built-in writable files.
- `cat note` — read the persistent note.
- `write note` — replace the persistent note with the next entered line.
- `world` — show player coordinates and the reachable target block snapshot.

## Disk layout

| LBA | Contents |
| --- | --- |
| 0 | stage-1 boot sector |
| 1-4 | LoraBoot stage 2 |
| 5 | LDB2 boot configuration |
| 6-7 | 16-entry system image table |
| 8-15 | reserved |
| 16+ | `system.bin`, then `recovery.bin` |
| 64 | LoraFS superblock and directory |
| 65-68 | persistent `note` data |

Legacy disks are backed up as `disk.bin.pre-2.0.bak` before a fresh 2.0 image
is installed. A disk already carrying LDB2 is never reflashed automatically.

## World Bridge ABI v1

PnP type `0x06` exposes a 64-byte read-only MMIO window. It reports ABI version,
capabilities, player block position and facing, plus the block reached by a
normal five-block raycast and its incoming redstone power. Writes are ignored;
the ABI cannot place/break blocks, run commands or reach arbitrary coordinates.

## Reproducible build

`gradlew generateSystemDisk` compiles BIOS, both loader stages, LoraOS and
Recovery and creates the factory disk. `gradlew build` depends on this task and
therefore packages the generated system in every release JAR.
