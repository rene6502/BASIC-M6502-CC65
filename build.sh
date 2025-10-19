#!/bin/sh
java Converter.java m6502.asm m6502.s
java Commodore.java m6502.s m6502-cbm.s
java -DEXTIO=0 Commodore.java m6502.s m6502-cbm-no-extio.s

echo
ca65 --version
ca65 -D REALIO=3 --feature force_range -o /tmp/commodore.obj m6502.s
ld65 --config commodore.cfg -o commodore.bin /tmp/commodore.obj

echo
printf "%-7s %s\n" "File:"   "commodore.bin"
printf "%-7s %s\n" "Length:" "$(stat -c %s commodore.bin) bytes"
printf "%-7s %s\n" "MD5:"    "$(md5sum commodore.bin | cut -d' ' -f1)"


ca65 --feature force_range -o /tmp/m6502-cbm.obj m6502-cbm.s
ld65 --config commodore.cfg -o /tmp/m6502-cbm.bin /tmp/m6502-cbm.obj

ca65 --feature force_range -o /tmp/m6502-cbm-no-extio.obj m6502-cbm-no-extio.s
ld65 --config commodore.cfg -o /tmp/m6502-cbm-no-extio.bin /tmp/m6502-cbm-no-extio.obj

echo
printf "%-7s %s\n" "File:"   "m6502-cbm.bin"
printf "%-7s %s\n" "Length:" "$(stat -c %s /tmp/m6502-cbm.bin) bytes"
printf "%-7s %s\n" "MD5:"    "$(md5sum /tmp/m6502-cbm.bin | cut -d' ' -f1)"

echo
printf "%-7s %s\n" "File:"   "m6502-cbm-no-extio.bin"
printf "%-7s %s\n" "Length:" "$(stat -c %s /tmp/m6502-cbm-no-extio.bin) bytes"
printf "%-7s %s\n" "MD5:"    "$(md5sum /tmp/m6502-cbm-no-extio.bin | cut -d' ' -f1)"
