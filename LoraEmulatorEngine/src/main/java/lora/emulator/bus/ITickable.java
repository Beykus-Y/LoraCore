package lora.emulator.bus;

public interface ITickable {
    /**
     * @param cycles Количество системных тактов, которые нужно прожить.
     */
    void tick(long cycles);
}