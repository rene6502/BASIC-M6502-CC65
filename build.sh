#!/bin/sh
java Converter.java m6502.asm m6502.s
java Formatter.java m6502.s m6502-cbm.s REALIO=3
java Formatter.java m6502.s m6502-min.s REALIO=3 EXTIO=0 TIME=0 CBMRND=0

echo
ca65 --version
ca65 -D REALIO=3 --feature force_range -o /tmp/m6502.obj m6502.s
ld65 --config m6502-cbm.cfg -o m6502.bin /tmp/m6502.obj

echo
printf "%-7s %s\n" "File:"   "m6502.bin"
printf "%-7s %s\n" "Length:" "$(stat -c %s m6502.bin) bytes"
printf "%-7s %s\n" "MD5:"    "$(md5sum m6502.bin | cut -d' ' -f1)"

ca65 --feature force_range -o /tmp/m6502-cbm.obj m6502-cbm.s
ld65 --config m6502-cbm.cfg -o /tmp/m6502-cbm.bin /tmp/m6502-cbm.obj

ca65 --feature force_range -o /tmp/m6502-min.obj m6502-min.s
ld65 --config m6502-cbm.cfg -o /tmp/m6502-min.bin /tmp/m6502-min.obj

echo
printf "%-7s %s\n" "File:"   "m6502-cbm.bin"
printf "%-7s %s\n" "Length:" "$(stat -c %s /tmp/m6502-cbm.bin) bytes"
printf "%-7s %s\n" "MD5:"    "$(md5sum /tmp/m6502-cbm.bin | cut -d' ' -f1)"

echo
printf "%-7s %s\n" "File:"   "m6502-min.bin"
printf "%-7s %s\n" "Length:" "$(stat -c %s /tmp/m6502-min.bin) bytes"
printf "%-7s %s\n" "MD5:"    "$(md5sum /tmp/m6502-min.bin | cut -d' ' -f1)"
