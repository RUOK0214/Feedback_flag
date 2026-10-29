package io.github.ruok0214.feedbackflag;

import net.minecraft.network.chat.Component;
import java.util.List;

/** Rounded mean RGB of the sixteen vanilla 26.2 concrete block textures. */
final class ConcretePalette {
    record Color(String name, int rgb) {
        Component label() { return Component.translatable("block.minecraft." + name + "_concrete"); }
    }
    static final List<Color> COLORS = List.of(
            new Color("white", 0xCFD5D6), new Color("orange", 0xE06101),
            new Color("magenta", 0xA9309F), new Color("light_blue", 0x2489C7),
            new Color("yellow", 0xF1AF15), new Color("lime", 0x5EA918),
            new Color("pink", 0xD6658F), new Color("gray", 0x373A3E),
            new Color("light_gray", 0x7D7D73), new Color("cyan", 0x157788),
            new Color("purple", 0x64209C), new Color("blue", 0x2D2F8F),
            new Color("brown", 0x603C20), new Color("green", 0x495B24),
            new Color("red", 0x8E2121), new Color("black", 0x080A0F));
    private ConcretePalette() { }
}
