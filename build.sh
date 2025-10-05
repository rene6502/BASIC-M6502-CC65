#!/bin/sh
java Converter.java m6502.asm m6502.s

echo
ca65 --version
ca65 -D REALIO=3 --feature force_range -o /tmp/commodore.obj m6502.s
ld65 --config commodore.cfg -o commodore.bin /tmp/commodore.obj

echo
printf "%-7s %s\n" "File:"   "commodore.bin"
printf "%-7s %s\n" "Length:" "$(stat -c %s commodore.bin) bytes"
printf "%-7s %s\n" "MD5:"    "$(md5sum commodore.bin | cut -d' ' -f1)"

java Commodore.java m6502.s m6502-cbm.s

ca65 -D REALIO=3 --feature force_range -o /tmp/m6502-cbm.obj m6502-cbm.s
ld65 --config commodore.cfg -o /tmp/m6502-cbm.bin /tmp/m6502-cbm.obj

echo
printf "%-7s %s\n" "File:"   "m6502-cbm.bin"
printf "%-7s %s\n" "Length:" "$(stat -c %s /tmp/m6502-cbm.bin) bytes"
printf "%-7s %s\n" "MD5:"    "$(md5sum /tmp/m6502-cbm.bin | cut -d' ' -f1)"
