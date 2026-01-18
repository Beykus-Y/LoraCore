## LoraC Language Reference

### Введение
LoraC — это высокоуровневый язык, схожий по синтаксису с C, который компилируется в Lora Assembly (ASM) и исполняется на виртуальном процессоре LoraCore. Точка входа программы — функция `main`, на которую в ASM выполняется безусловный переход.

См. реализацию компилятора: [LoraCompiler.java](file:///f:/Backup/modm/loracore/src/main/java/com/loracore/lang/LoraCompiler.java) и ассемблера: [TextAssembler.java](file:///f:/Backup/modm/loracore/src/main/java/com/loracore/lang/TextAssembler.java). Пример стиля — [bios.lc](file:///f:/Backup/modm/loracore/src/main/resources/assets/loracore/os/src/bios.lc).

### Структура программы
- Глобальные константы: `const NAME = VALUE;` (десятичные или шестнадцатеричные `0x...`). Собираются на этапе препроцессинга и доступны в выражениях.
- Точка входа: функция `fn main() { ... }`. Компилятор генерирует в ASM `JMP main`.
- Функции объявляются ключевым словом `fn` и компилируются в метки ASM с завершающим `RET`.

```text
const PNP_ADDR = 0xFFF000;

fn main() {
    halt;
}
```

### Переменные и типы
- Объявление: `let name = expression;`
- Типы: целые 32-битные значения (знаковые).
- Хранение: локальные переменные компилируются как статические метки в `.data` секции ASM. Инициализация делается через запись значения по адресу метки. Переменные имеют локальную область имён внутри функции, но физическое хранение — статическое, то есть значение сохраняется между вызовами.

```text
fn main() {
    let x = 10;
    let y = x + 5;
    halt;
}
```

### Доступ к памяти
- Запись: `mem[address] = value;`
- Чтение: `let v = mem[address];` или использование `mem[...]` внутри выражений.
- Адрес — это выражение: поддерживаются вычисления вида `base + offset`.
- Генерация ASM:
  - Чтение: адрес вычисляется в регистре, затем `LD Rdest, Raddr`.
  - Запись: адрес и значение вычисляются, затем `ST Raddr, Rval`.

```text
const BASE = 0x1000;

fn write_once() {
    let addr = BASE + 4;
    mem[addr] = 123;
}

fn read_once() {
    let value = mem[BASE + 8];
}
```

### Функции (fn)
- Объявление: `fn name(arg0, arg1, ...) { ... }`
- Аргументы сопоставляются с регистрами: `arg0 -> R0`, `arg1 -> R1`, и т.д.
- Вызов: `name(expr0, expr1);` — аргументы вычисляются и помещаются в соответствующие регистры, затем выполняется `CALL name`.
- Возврат: явного `return` нет. Рекомендация — использовать регистры (обычно `R0`) или память для передачи результата.

```text
fn add(a, b) {
    let sum = a + b;
}

fn main() {
    add(10, 20);
}
```

### Управляющие конструкции
- Условие `if (cond) { ... }` — поддерживаются сравнения `==`, `!=`, `>`, `<`. Ветка `else` отсутствует.
- Цикл `while (cond) { ... }` — проверка условия перед каждой итерацией.
- `break;` — выход из ближайшего цикла.
- `halt;` — остановка процессора.

```text
fn scan(base, count) {
    while (count > 0) {
        let t = mem[base];
        if (t == 2) {
            break;
        }
        base = base + 16;
        count = count - 1;
    }
}
```

### Математика и логика
- Арифметика: `+`, `-`
- Сравнения: `==`, `!=`, `>`, `<`
- Доступ к памяти в выражениях: `mem[expr]`
- При вычислении больших литералов используются инструкции загрузки верхней/нижней части (например, `LUI`, `ORI`) для формирования 32-битного значения.

```text
fn math(a, b) {
    let s = a + b;
    let d = a - b;
    if (s > d) {
        halt;
    }
}
```

### Примеры кода
Пример из BIOS (упрощённый стиль):

```text
const GPU_TYPE = 2;

fn main() {
    let pnp = 0xFFF000;
    let count = mem[pnp + 4];
    let current = pnp + 8;

    let vram = 0;
    let vram_size = 0;

    while (count > 0) {
        let type = mem[current];
        if (type == GPU_TYPE) {
            vram = mem[current + 4];
            vram_size = mem[current + 8];
            break;
        }
        current = current + 16;
        count = count - 1;
    }

    if (vram != 0) {
        fill(vram, vram_size, 0xFF081030);
    }

    halt;
}

fn fill(base, size, color) {
    let offset = 0;
    while (offset < size) {
        let addr = base + offset;
        mem[addr] = color;
        offset = offset + 4;
    }
}
```

Пример «Memory Fill» минимальной программы:

```text
const VRAM = 0x200000;
const SIZE = 0x1000;
const COLOR = 0x00FF00FF;

fn main() {
    let i = 0;
    while (i < SIZE) {
        mem[VRAM + i] = COLOR;
        i = i + 4;
    }
    halt;
}
```

### Ограничения и примечания
- Ветка `else` и `return` пока не поддерживаются.
- Локальные переменные компилируются в статическое хранилище `.data`; изменённые значения сохраняются между вызовами функций.
- Аргументы функций считаются «read-only» внутри выражений присваивания: попытка присвоения в идентификатор аргумента вызовет ошибку компиляции.
- Для дальних переходов/вызовов адреса меток патчатся младшими 16 битами; для работы с большими адресами используйте загрузку адреса в регистр и косвенные переходы.
