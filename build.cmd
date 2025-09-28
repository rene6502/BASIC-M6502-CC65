@echo off
java Converter.java m6502.asm m6502.s

ca65 --version
ca65 -D REALIO=3 --feature force_range -o commodore.obj m6502.s
ld65 --config commodore.cfg -o commodore.bin commodore.obj
