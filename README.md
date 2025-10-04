# Microsoft BASIC for 6502 Microprocessor - cc65 version

This project provides a version of [BASIC-M6502](https://github.com/microsoft/BASIC-M6502)
that is compatible with the [cc65 compiler suite](https://cc65.github.io/).\
It includes a Java program that translates the original MACRO-10 source file into a cc65 file, along with a short build script for the Commodore version.\
The produced Commodore version is **Commodore BASIC 2** for the **PET** and identical to
[this](https://github.com/mist64/msbasic/blob/master/orig/cbmbasic2.bin) version built by Michael Steil.

## Targets

- The **Commodore** build is the only one that produces an output identical to the released version.\
  This makes it the only target with a verifiable result, and therefore the main focus of this project.
- The **KIM** and **OSI** targets can still be assembled, but Microsoft's source code evolved significantly after their release.\
  The current codebase can no longer reproduce those early versions, so they cannot be verified.
- The **Apple** target is based on newer sources, but Apple introduced many unpublished extensions
  (e.g., graphics and I/O functions). Because of this, its output also cannot be verified.

## Build

The converter requires Java 21.\
These cc65 versions have been used successfully:

- Linux: ca65 V2.18 - Debian 2.19-2
- Windows: ca65 V2.19 - Git 357f64e

Make sure to pass the symbol definition for the target `-D REALIO=3` and
the feature flag `force_range` to the ca65 assembler.
  
## Example build on Debian 13

```sh
>./build.sh
Convert MACRO-10 source file to cc65 syntax
Input File: m6502.asm
Output File: m6502.s

ca65 V2.18 - Debian 2.19-2
CONFIG: REALIO=3
CONFIG: TARGET=COMMODORE
CONFIG: ADDITIONAL PRECISION
CONFIG: LONG ERRORS
CONFIG: SAVE AND LOAD
CONFIG: ROM
CONFIG: USE ROR INSTRUCTION

File:   commodore.bin
Length: 8670 bytes
MD5:    65fbddc1114c5ca4648cf31d6a9a2891
```

## Files

| File                             | Description                                       |
|----------------------------------|---------------------------------------------------|
| [m6502.asm](m6502.asm)           | original MACRO-10 source file                     |
| [m6502.s](m6502.s)               | cc65 version capable of building multiple targets |
| [Converter.java](Converter.java) | The converter program                             |

## Original README

## Historical Significance

This assembly language source code represents one of the most historically significant pieces of software from the early personal computer era. It is the complete source code for **Microsoft BASIC Version 1.1 for the 6502 microprocessor**, originally developed and copyrighted by Microsoft in 1976-1978.

### Why This Document is Historically Important

#### 1. Foundation of the Personal Computer Revolution

- This BASIC interpreter was the software foundation that powered many of the most influential early personal computers
- It democratized programming by making it accessible to non-technical users through a simple, English-like programming language
- Without this software, the personal computer revolution might have developed very differently

#### 2. Microsoft's Early Success

- This represents some of Microsoft's earliest and most successful software
- The licensing of this BASIC interpreter to multiple computer manufacturers was crucial to Microsoft's early business model
- It established Microsoft as a dominant force in personal computer software before MS-DOS or Windows

#### 3. Multi-Platform Compatibility

- This single codebase was designed to run on multiple different computer systems of the era
- The conditional compilation system allowed the same source code to target different hardware platforms
- This approach influenced how software would be developed for decades to come

## Supported Computer Systems

The source code includes conditional compilation support for multiple pioneering computer systems:

- **Apple II** (`REALIO=4`) - Steve Jobs and Steve Wozniak's revolutionary home computer
- **Commodore PET** (`REALIO=3`) - One of the first complete personal computers
- **Ohio Scientific (OSI)** (`REALIO=2`) - Popular among hobbyists and schools
- **MOS Technology KIM-1** (`REALIO=1`) - An influential single-board computer
- **PDP-10 Simulation** (`REALIO=0`) - For development and testing purposes

## Technical Specifications

- **Language**: 6502 Assembly Language
- **Target Processor**: MOS Technology 6502 8-bit microprocessor
- **Memory Footprint**: 8KB ROM version
- **Features**: Complete BASIC interpreter with floating-point arithmetic
- **Architecture**: Designed for both ROM and RAM configurations

## Key Features

### Programming Language Support

- Full BASIC language implementation
- Floating-point arithmetic
- String handling and manipulation
- Array support (both integer and string arrays)
- Mathematical functions and operators
- Input/output operations

### Memory Management

- Efficient memory utilization for 8-bit systems
- String garbage collection
- Dynamic variable storage
- Stack-based expression evaluation

### Hardware Abstraction

- Configurable I/O routines for different computer systems
- Terminal width adaptation
- Character input/output abstraction
- Optional disk storage support

## Development History

The source code includes detailed revision history showing active development:

- **July 27, 1978**: Fixed critical bugs in FOR loop variable handling and statement parsing
- **July 1, 1978**: Memory optimization and garbage collection improvements  
- **March 9, 1978**: Enhanced string function capabilities
- **February 25, 1978**: Input flag corrections and numeric precision improvements
- **February 11, 1978**: Reserved word parsing enhancements
- **January 24, 1978**: User-defined function improvements

## Cultural Impact

### Educational Influence

- This BASIC interpreter introduced millions of people to computer programming
- It was the first programming language for countless programmers who later became industry leaders
- The simple, interactive nature of BASIC made computers approachable for non-technical users

### Industry Standardization

- Microsoft's BASIC became the de facto standard for personal computer programming
- The design patterns and conventions established here influenced later programming languages and development tools
- The multi-platform approach pioneered techniques still used in modern software development

### Business Model Innovation

- The licensing of this software to multiple hardware manufacturers created Microsoft's early business model
- It demonstrated the viability of software as a standalone business, separate from hardware
- This approach became the template for the entire software industry

## Technical Innovation

### Compiler Technology

- Advanced macro system for code generation
- Sophisticated conditional compilation for multi-platform support
- Efficient symbol table management
- Optimized code generation for memory-constrained systems

### Runtime System

- Stack-based expression evaluator
- Dynamic memory management
- Real-time garbage collection
- Interactive command processing

## Legacy

This source code represents the foundation upon which the modern software industry was built. The techniques, patterns, and business models pioneered in this BASIC interpreter directly influenced:

- The development of MS-DOS and subsequent Microsoft operating systems
- The standardization of programming language implementations
- The establishment of software licensing as a business model
- The democratization of computer programming

## File Information

- **Filename**: `m6502.asm`
- **Lines of Code**: 6,955 lines
- **Copyright**: Microsoft Corporation, 1976-1978
- **Version**: 1.1
- **Assembly Format**: Compatible with period assemblers for 6502 development

---

*This document represents a crucial piece of computing history - the source code that helped launch the personal computer revolution and established Microsoft as a software industry leader.*
