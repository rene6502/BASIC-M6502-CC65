import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Converter {
  private final Set<String> varNames =
      Set.of("BUFLEN", "BUFOFS", "BUFPAG", "CLMWID", "DISKO", "EXTIO", "GETCMD", "KIMROM", "LINLEN", "NULCMD", "Q",
          "RAMLOC", "ROMLOC", "ROMSW", "RORSW", "STKEND", "TIME");

  // used to convert angled brackets in expressions to rounded brackets
  private final List<String> angledBracketsExpressions = List.of(
      "<3*ADDPRC>",
      "<2*ADDPRC>",
      "<BUF&255>",
      "<CQTIMR-2>",
      "<BUF/256>*256",
      "LDXYI\t<BUF-1>",
      "<<<LINLEN/CLMWID>-1>*CLMWID>",
      "ISVRET-1-<ISVRET-1>/256*256",
      "<<ISVRET-1>/256>");

  // used to convert octal and decimal numbers in expressions
  private final Map<String, String> expressions = Map.of(
      "10*ADDPRC+30", "8*ADDPRC+24",
      "8*ADDPRC+230", "8*ADDPRC+152",
      "11+ADDPRC", "9+ADDPRC",
      "3*ADDPRC+10", "3*ADDPRC+8",
      "\"0\"+12", "\"0\"+10",
      "^D256-7", "256-7",
      "^D256-3-ADDPRC", "256-3-ADDPRC",
      "^D256-3*ADDPRC-6", "256-3*ADDPRC-6",
      "^D256-\"0\"", "256-\"0\"",
      "RAMLOC/^D256", "RAMLOC/256");

  // store the value of symbols like "ROMSW=0"
  private Map<String, String> symbols = new HashMap<>();

  private record Block(List<String> lines, String trailing) {
  }

  private record Line(String label, String instruction, String comment, String line) {
  }

  // args[0] - the original Microsoft m6502.asm file using MACOR-10 syntax
  // args[1] - the converted files in cc65 syntax
  public static void main(String... args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("missing input and output filename");
    }
    Path inputFile = Path.of(args[0]);
    Path outputFile = Path.of(args[1]);

    System.out.printf("Convert MACRO-10 source file to cc65 syntax in=%s out=%s\n", inputFile.getFileName(),
        outputFile.getFileName());

    Converter converter = new Converter();
    List<String> lines = Files.readAllLines(inputFile);
    lines = converter.convertClean(lines);
    lines = converter.replaceTextBlocks(lines);
    lines = converter.convertUntilUnchanged(lines, converter::convertIf);
    lines = converter.convertMacros(lines);
    lines = converter.convertSymbols(lines);
    lines = converter.convertRepeat(lines);
    lines = converter.convertInstructions(lines);
    lines = converter.expandTabs(lines);
    Files.write(outputFile, lines);
  }

  // first cleanup of source
  // handles comments, titles and corrects some syntax to simplify further processing
  private List<String> convertClean(List<String> lines) {
    List<String> result = new ArrayList<>();
    String delimiter = null;
    while (!lines.isEmpty()) {
      String line = lines.removeFirst();

      // handle multi-line comment blocks
      if (delimiter == null && line.startsWith("COMMENT ")) {
        delimiter = line.substring(8, 9);
        line = "/*"; // start comment block
      } else if (delimiter != null && line.contains(delimiter)) {
        delimiter = null;
        line = "*/"; // end comment block
      }

      // comment out title and subtitles
      if (line.contains("TITLE") || line.startsWith("SUBTTL") || line.startsWith("\fSUBTTL")) {
        line = "; " + line.replace("\f", "");
      }

      // move label to separate line to simplify parsing
      Matcher matchIf = Pattern.compile("^([A-Z]+:)\\s+(IF[N,E]\\s.*)").matcher(line);
      if (matchIf.matches()) {
        result.add(matchIf.group(1));
        line = matchIf.group(2);
      }

      // remove comma, e.g. "ASL A,", "LDA RESLST,Y,", "STA 258,X,"
      Line parsed = getLine(line);
      Matcher matchComma = Pattern.compile("[AXY],").matcher(parsed.instruction());
      if (delimiter == null && matchComma.find()) {
        String register = matchComma.group().substring(0, 1);
        line = parsed.label() + matchComma.replaceAll(register) + parsed.comment();
      }

      // replace angled brackets with round brackets in expressions
      for (String expression : angledBracketsExpressions) {
        int index = line.indexOf(expression);
        if (index != -1) {
          String replace = line.substring(index, index + expression.length()).replace('<', '(').replace('>', ')');
          line = line.substring(0, index) + replace + line.substring(index + expression.length());
        }
      }

      result.add(line);
    }

    return result;
  }

  // replace text blocks by simple search and replace
  private List<String> replaceTextBlocks(List<String> lines) {
    List<String> result = new ArrayList<>(lines);

    // add REALIO validation
    result = replaceTextBlock(result, """
        \t\t\t\t;0=PDP-10 SIMULATING 6502
        """, """
        \t\t\t\t;0=PDP-10 SIMULATING 6502

        .IF REALIO <> 1 .AND REALIO <> 2 .AND REALIO <> 3 .AND REALIO <> 4
          .ERROR .SPRINTF("REALIO must be 1, 2, 3, or 4 (actual=%d)", REALIO)
        .ENDIF

        """);

    // add ZEROPAGE segment
    result = replaceTextBlock(result, """
        ; SUBTTL\tPAGE ZERO.
        """, """
        ; SUBTTL\tPAGE ZERO.
        .SEGMENT "ZEROPAGE"
        """);

    // add CODE segment
    result = replaceTextBlock(result, "\tORG\tROMLOC", ".SEGMENT \"CODE\": absolute");

    // remove default target
    result = replaceTextBlock(result, "REALIO=4\t\t\t;5=STM", "\t\t\t\t;5=STM");

    // add DC macro
    result = replaceTextBlock(result, """
        DEFINE ACRLF,<
        """, """
        DEFINE DC,<>
        DEFINE ACRLF,<
        """);

    // disable GET command for OSI
    result = replaceTextBlock(result, """
        IFE\tREALIO-2,<
        \tRORSW==0
        """, """
        IFE\tREALIO-2,<
        \tGETCMD==0
        \tRORSW==0
        """);

    // enable temporary stack only if not in ROM
    result = replaceTextBlock(result, """
        LASTWR::
        \tBLOCK\t100\t\t;SPACE FOR TEMP STACK.
        """, """
        IFE ROMSW,<
        LASTWR:
        \tBLOCK\t100\t\t;SPACE FOR TEMP STACK.>
        """);

    // rearrange comment to allow easier removal of assignment
    result = replaceTextBlock(result, """
        BUFOFS=0\t\t\t;THE AMOUNT TO OFFSET THE LOW BYTE
        \t\t\t\t;OF THE TEXT POINTER TO GET TO BUF
        \t\t\t\t;AFTER TXTPTR HAS BEEN SETUP TO POINT INTO BUF
        """, """
        \t\t\t\t;BUFOFS IS THE AMOUNT TO OFFSET THE LOW BYTE
        \t\t\t\t;OF THE TEXT POINTER TO GET TO BUF**
        \t\t\t\t;AFTER TXTPTR HAS BEEN SETUP TO POINT INTO BUF
        BUFOFS=0
        """);

    // fix LOFBUF and FBUFFR for Commodore
    result = replaceTextBlock(result, """
        LOFBUF: BLOCK\t1\t\t;THE LOW FAC BUFFER. COPYABLE.
        ;---  PAGE ZERO/ONE BOUNDARY ---.
        \t\t\t\t;MUST HAVE 13 CONTIGUOUS BYTES.
        FBUFFR: BLOCK\t3*ADDPRC+13\t;BUFFER FOR "FOUT".
        \t\t\t\t;ON PAGE 1 SO THAT STRING IS NOT COPIED.
        """, """
        IFN REALIO-3,<
        LOFBUF: BLOCK\t1\t\t;THE LOW FAC BUFFER. COPYABLE.
        ;---  PAGE ZERO/ONE BOUNDARY ---.
        \t\t\t\t;MUST HAVE 13 CONTIGUOUS BYTES.
        FBUFFR: BLOCK\t3*ADDPRC+13\t;BUFFER FOR "FOUT".
        \t\t\t\t;ON PAGE 1 SO THAT STRING IS NOT COPIED.>
        IFE REALIO-3,<
        LOFBUF=$00ff
        FBUFFR=$0100>
        """);

    // fix access to CHANNL if EXTIO=0
    result = replaceTextBlock(result, """
        IFE\tREALIO-3,<
        \tLDA\tCHANNL
        \tBEQ\tCRTSKP
        """, """
        IFN\tEXTIO,<
        \tLDA\tCHANNL
        \tBEQ\tCRTSKP
        """);

    // fix missing GOMOVF label if EXTIO=0
    result = replaceTextBlock(result, """
        \tJMP\tFLOAT
        GOMOVF:>
        """, """
        \tJMP\tFLOAT>
        GOMOVF:
        """);

    // insert new symbol for Commodore RND function
    result = replaceTextBlock(result, """
        IFE\tREALIO-3,<
        \tDISKO==1
        """, """
        CBMRND .SET 0\t\t;FLAG TO ENABLE COMMODORE VIA TIMER FOR RND FUNCTION
        IFE\tREALIO-3,<
        CBMRND .SET 1\t\t;USE COMMODORE VIA TIMER FOR RND FUNCTION
        \tDISKO==1
        """);

    result = replaceTextBlock(result, """
        IFN\tREALIO-3,<
        \tTAX>\t\t\t;GET INTO ACCX, SINCE "MOVFM" USES ACCX.
        """, """
        IFE\tCBMRND,<
        \tTAX>\t\t\t;GET INTO ACCX, SINCE "MOVFM" USES ACCX.
        """);

    result = replaceTextBlock(result, """
        IFE\tREALIO-3,<
        \tBNE\tQSETNR
        """, """
        IFN\tCBMRND,<
        \tBNE\tQSETNR
        """);

    result = replaceTextBlock(result, """
        IFN\tREALIO-3,<
        \tTXA\t\t\t;FAC WAS ZERO?
        """, """
        IFE\tCBMRND,<
        \tTXA\t\t\t;FAC WAS ZERO?
        """);

    result = replaceTextBlock(result, """
        IFE\tREALIO-3,<
        \tLDX\tFACMOH
        """, """
        IFN\tCBMRND,<
        \tLDX\tFACMOH
        """);

    // insert missing NOP for Commodore
    result = replaceTextBlock(result, """
        \tBEQ\tDIRCON
        """, """
        IFE REALIO-3,<\tNOP>
        \tBEQ\tDIRCON
        """);

    // enable peek into ROM for Commodore
    result = replaceTextBlock(result, """
        \tCMPI\tROMLOC/256\t;IF WITHIN BASIC,
        \tBCC\tGETCON
        \tCMPI\tLASTWR/256
        \tBCC\tDOSGFL>\t\t;GIVE HIM ZERO FOR AN ANSWER.
        """, """
        \tNOP
        \tNOP
        \tNOP
        \tNOP
        \tNOP
        \tNOP
        \tNOP
        \tNOP>
        """);

    // activate easter egg for Commodore
    result = replaceTextBlock(result, """
        IFN\tREALIO-3,<ZSTORDO=STORDO>
        """, """
        IFN\tREALIO-3,<ZSTORDO=STORDO>
        IFE\tREALIO-3,<ZSTORDO=ZSTORD>
        """);
    result = replaceTextBlock(result, "MRCHR:\tLDA\tSINCON+36,X>", "MRCHR:\tLDA\tSINCON+30,X>");

    // move label to separate line to simplify parsing
    result = replaceTextBlock(result, """
        DIVNRM: REPEAT\t6,<ASL\tA>\t;GET LAST TWO BITS INTO MSB AND B6.
        """, """
        DIVNRM:
        REPEAT\t6,<ASL\tA>\t;GET LAST TWO BITS INTO MSB AND B6.
        """);

    // revert the workaround for MACRO-11 doesn't like "(" in arguments
    result = replaceTextBlock(result, """
        \t"S"
        \t"P"
        \t"C"
        \t"("+128\t\t\t;MACRO DOESNT LIKE ('S IN ARGUMENTS.
        \tQ=Q+1
        """, """
        \tDCI"SPC("
        """);

    // revert the workaround for MACRO-11 doesn't like "(" in arguments
    result = replaceTextBlock(result, """
        \t"T"
        \t"A"
        \t"B"
        \t"("+128
        \tQ=Q+1
        """, """
        \tDCI"TAB("
        """);

    // enable C like comments used multi-line comment blocks
    result = replaceTextBlock(result, """
        ; SUBTTL\tINTRODUCTION AND COMPILATION PARAMETERS.
        """, """
        ; SUBTTL\tINTRODUCTION AND COMPILATION PARAMETERS.

        .FEATURE c_comments

        """);

    // make long errors the default
    result = replaceTextBlock(result, "LNGERR==0\t\t\t;LONG ERROR MESSAGES.", "LNGERR==1\t\t\t;LONG ERROR MESSAGES.");

    // fix label name
    result = replaceTextBlock(result, "\tADR(RESTORE-1)", "\tADR(RESTOR-1)");

    // allow injection of opcode $A9
    result = replaceTextBlock(result, "\tXWD\t^O1000,^O251\t;LDAI TYA TO MAKE IT NONZERO.", "\t.BYTE\t$A9");

    // fix capitalization errors
    result = replaceTextBlock(result, "ife\taddprc,<", "IFE\tADDPRC,<");
    result = replaceTextBlock(result, "expcon: 6\t; degree -1.", "EXPCON: 6\t; degree -1.");
    result = replaceTextBlock(result, "\tlinlen==40", "LINLEN==40");

    // fix whitespace and syntax errors
    result = replaceTextBlock(result, "\tERRDV0==Q\t\t;DIVISION BY ZERO.", "ERRDV0=Q\t;DIVISION BY ZERO.");
    result = replaceTextBlock(result, "\tERRDV0==Q", "ERRDV0=Q");
    result = replaceTextBlock(result, "ZSTORD:!\tLDA\tPOKER", "ZSTORD:\tLDA\tPOKER"); // remove '!'

    // remove unneeded stuff which is not supported by cc65
    result = replaceTextBlock(result, "SEARCH\tM6502", "");
    result = replaceTextBlock(result, "SALL", "");
    result = replaceTextBlock(result, "$Z::\t\t\t\t;STARTING POINT FOR M6502 SIMULATOR", "");
    result = replaceTextBlock(result, "PAGE", "");
    result = replaceTextBlock(result, "\tPAGE", "");
    result = replaceTextBlock(result, "\tHRRZ\t14,.JBDDT##", ";\tHRRZ\t14,.JBDDT##");
    result = replaceTextBlock(result, "\tJRST\t0(14)>", ";\tJRST\t0(14)>");
    result = replaceTextBlock(result, "\tXLIST", "");
    result = replaceTextBlock(result, "\tLIST", "");
    result = replaceTextBlock(result, ".XCREF", "");
    result = replaceTextBlock(result, ".CREF", "");
    result = replaceTextBlock(result, "IFNDEF\tSTART,<START==0>", "");
    result = replaceTextBlock(result, "\tEND\t$Z+START", "");

    return result;
  }

  private List<String> convertUntilUnchanged(
      List<String> input, Function<List<String>, List<String>> converter) {
    List<String> lines = input;
    while (true) {
      List<String> before = new ArrayList<>(lines);
      lines = converter.apply(lines);
      if (lines.equals(before)) {
        break;
      }
    }
    return lines;
  }

  // convert MACRO-10 IF conditions (IFE, IFN, IF1, IF2)
  private List<String> convertIf(List<String> lines) {
    List<String> result = new ArrayList<>();

    while (!lines.isEmpty()) {
      String line = lines.removeFirst();

      Matcher matchIfEqual = Pattern.compile("^IFE\\s*(\\S+),<(.*)").matcher(line);
      Matcher matchIfNotEqual = Pattern.compile("^IFN\\s*(\\S+),<(.*)").matcher(line);
      Matcher matchIfPass1 = Pattern.compile("^IF1,<(.*)").matcher(line);
      Matcher matchIfPass2 = Pattern.compile("^IF2,<(.*)").matcher(line);

      if (matchIfEqual.find()) {
        String expr = matchIfEqual.group(1);
        String code = matchIfEqual.group(2);
        processIf(code, expr, true, lines, result);
      } else if (matchIfNotEqual.find()) {
        String expr = matchIfNotEqual.group(1);
        String code = matchIfNotEqual.group(2);
        processIf(code, expr, false, lines, result);
      } else if (matchIfPass1.find()) {
        String code = matchIfPass1.group(1);
        Block block = getAngledBlock(code, lines);
        if (block.lines().getLast().contains("PRINTX")) {
          result.addAll(getConfigLines());
        } // else other IF1 blocks are ignored
      } else if (matchIfPass2.find()) {
        String code = matchIfPass2.group(1);
        Block block = getAngledBlock(code, lines);
        if (block.lines().stream().noneMatch(s -> s.contains("PURGE"))) {
          result.addAll(block.lines); // add block if they are not using MACRO-10 PURGE instruction
        }
      } else {
        result.add(line);
      }
    }

    return result;
  }

  // process MACRO-10 IF condition
  private void processIf(String code, String expr, boolean testEqual, List<String> lines,
      List<String> result) {
    Block block = getAngledBlock(code, lines);

    if (expr.equals("REALIO")) {
      expr = "REALIO-0";
    }

    String condition = "";
    Matcher matcherSymbol = Pattern.compile("^[A-Z]+$").matcher(expr);
    Matcher matcherTarget = Pattern.compile("^REALIO-(\\d)$").matcher(expr);
    Matcher matcherOr = Pattern.compile("^([A-Z]+)!([A-Z]+)$").matcher(expr);
    if (matcherSymbol.matches()) {
      if (testEqual) {
        condition = expr + "=0";
      } else {
        condition = expr + "<>0";
      }
    } else if (matcherTarget.matches()) {
      String target = matcherTarget.group(1);
      if (testEqual) {
        condition = "REALIO=" + target;
      } else {
        condition = "REALIO<>" + target;
      }
    } else if (matcherOr.matches()) {
      String sym0 = matcherOr.group(1);
      String sym1 = matcherOr.group(2);
      if (testEqual) {
        condition = "(" + sym0 + "|" + sym1 + ")=0";
      } else {
        condition = "(" + sym0 + "|" + sym1 + ")<>0";
      }
    } else if (!testEqual && expr.equals("STKEND-511")) {
      condition = "STKEND<>511";
    } else if (!testEqual && expr.equals("<<BUF+BUFLEN>/256>-<<BUF-1>/256>")) {
      condition = "BUFPAG<>0";
    } else {
      throw new IllegalArgumentException("unsupported expression " + expr);
    }

    result.add(".IF " + condition);
    List<String> blockLines = new ArrayList<>(block.lines());
    if (blockLines.getLast().isEmpty()) {
      blockLines.removeLast();
    }
    if (!block.trailing().isEmpty()) {
      String lastLine = blockLines.removeLast();
      blockLines.add(lastLine + block.trailing());
    }
    result.addAll(blockLines);
    result.add(".ENDIF");
  }

  // get statements to print out configuration during assemble
  private List<String> getConfigLines() {
    String config = """
        .OUT .SPRINTF("CONFIG: REALIO=%d", REALIO)
        .IF REALIO=1
          .OUT "CONFIG: TARGET=KIM"
        .ENDIF
        .IF REALIO=2
          .OUT "CONFIG: TARGET=OSI"
        .ENDIF
        .IF REALIO=3
          .OUT "CONFIG: TARGET=COMMODORE"
        .ENDIF
        .IF REALIO=4
          .OUT "CONFIG: TARGET=APPLE"
        .ENDIF
        .IF REALIO=5
          .OUT "CONFIG: TARGET=STM"
        .ENDIF
        .IF ADDPRC<>0
          .OUT "CONFIG: ADDITIONAL PRECISION"
        .ENDIF
        .IF LNGERR<>0
          .OUT "CONFIG: LONG ERRORS"
        .ENDIF
        .IF DISKO<>0
          .OUT "CONFIG: SAVE AND LOAD"
        .ENDIF
        .IF ROMSW=0
          .OUT "CONFIG: RAM"
        .ENDIF
        .IF ROMSW<>0
          .OUT "CONFIG: ROM"
        .ENDIF
        .IF RORSW=0
          .OUT "CONFIG: EMULATE ROR INSTRUCTION"
        .ENDIF
        .IF RORSW<>0
          .OUT "CONFIG: USE ROR INSTRUCTION"
        .ENDIF
        """;
    return config.lines().toList();
  }

  // convert all MACRO-10 macros to cc65 macros
  private List<String> convertMacros(List<String> lines) {
    List<String> result = new ArrayList<>();
    while (!lines.isEmpty()) {
      String line = lines.removeFirst();

      Matcher matcher = Pattern.compile("^DEFINE(.*),\\s*<(.*)").matcher(line);
      if (matcher.matches()) {
        String def = matcher.group(1).trim().replace("\t", " ");
        String code = matcher.group(2);
        getAngledBlock(code, lines); // consume/delete
        result.addAll(getMacroLines(def));
      } else {
        result.add(line);
      }
    }

    return result;
  }

  // convert single MACRO-10 macro to cc65 macro
  private List<String> getMacroLines(String def) {
    String macro = switch (def) {
      case "DC" -> """
          .MACRO DC STR
            .REPEAT .STRLEN(STR)-1,I
              .BYTE .STRAT(STR,I)
            .ENDREP
            .BYTE .STRAT(STR,.STRLEN(STR)-1) | $80
          .ENDMACRO
          """;

      case "ROR (WD)" -> """
          .MACRO RORA ADDRESS
            LDA #0
            BCC PC *+4
            LDA #$80
            LSR ADDRESS
            ORA ADDRESS
            STA ADDRESS
          .ENDMACRO
          """;

      case "ACRLF" -> """
          .MACRO ACRLF
            .BYTE $0D, $0A
          .ENDMACRO
          """;

      case "SYNCHK (Q)" -> """
          .MACRO SYNCHK VALUE
            LDA #VALUE
            JSR SYNCHR
          .ENDMACRO
          """;

      case "DT(Q)" -> """
          .MACRO DT STR
            .BYTE STR
          .ENDMACRO
          """;

      case "LDWD (WD)" -> """
          .MACRO LDWD ADDRESS
            LDA ADDRESS+0
            LDY ADDRESS+1
          .ENDMACRO
          """;

      case "LDWDI (WD)" -> """
          .MACRO LDWDI ADDRESS
            LDA #<(ADDRESS)
            LDY #>(ADDRESS)
          .ENDMACRO
          """;

      case "LDWX (WD)" -> """
          .MACRO LDWX ADDRESS
            LDA ADDRESS+0
            LDX ADDRESS+1
          .ENDMACRO
          """;

      case "LDWXI (WD)" -> """
          .MACRO LDWXI ADDRESS
            LDA #<(ADDRESS)
            LDX #>(ADDRESS)
          .ENDMACRO
          """;

      case "LDXY (WD)" -> """
          .MACRO LDXY ADDRESS
            LDX ADDRESS+0
            LDY ADDRESS+1
          .ENDMACRO
          """;

      case "LDXYI (WD)" -> """
          .MACRO LDXYI ADDRESS
            LDX #<(ADDRESS)
            LDY #>(ADDRESS)
          .ENDMACRO
          """;

      case "STWD (WD)" -> """
          .MACRO STWD ADDRESS
            STA ADDRESS+0
            STY ADDRESS+1
          .ENDMACRO
          """;

      case "STWX (WD)" -> """
          .MACRO STWX ADDRESS
            STA ADDRESS+0
            STx ADDRESS+1
          .ENDMACRO
          """;

      case "STXY (WD)" -> """
          .MACRO STXY ADDRESS
            STX ADDRESS+0
            STY ADDRESS+1
          .ENDMACRO
          """;

      case "CLR (WD)" -> """
          .MACRO CLR ADDRESS
            LDA #0
            STA ADDRESS
          .ENDMACRO
          """;

      case "COM (WD)" -> """
          .MACRO COM ADDRESS
            LDA ADDRESS
            EOR #$FF
            STA ADDRESS
          .ENDMACRO
          """;

      case "PULWD (WD)" -> """
          .MACRO PULWD ADDRESS
            PLA
            STA ADDRESS+0
            PLA
            STA ADDRESS+1
          .ENDMACRO
          """;

      case "PSHWD (WD)" -> """
          .MACRO PSHWD ADDRESS
            LDA ADDRESS+1
            PHA
            LDA ADDRESS+0
            PHA
          .ENDMACRO
          """;

      case "JEQ (WD)" -> """
          .MACRO JEQ ADDRESS
            BNE *+5
            JMP ADDRESS
          .ENDMACRO
          """;

      case "JNE (WD)" -> """
          .MACRO JNE ADDRESS
            BEQ *+5
            JMP ADDRESS
          .ENDMACRO
          """;

      case "BCCA(Q)" -> """
          .MACRO BCCA ADDRESS
            BCC ADDRESS
          .ENDMACRO
          """;

      case "BCSA(Q)" -> """
          .MACRO BCSA ADDRESS
            BCS ADDRESS
          .ENDMACRO
          """;

      case "BEQA(Q)" -> """
          .MACRO BEQA ADDRESS
            BEQ ADDRESS
          .ENDMACRO
          """;

      case "BNEA(Q)" -> """
          .MACRO BNEA ADDRESS
            BNE ADDRESS
          .ENDMACRO
          """;

      case "BMIA(Q)" -> """
          .MACRO BMIA ADDRESS
            BMI ADDRESS
          .ENDMACRO
          """;

      case "BPLA(Q)" -> """
          .MACRO BPLA ADDRESS
            BPL ADDRESS
          .ENDMACRO
          """;

      case "BVCA(Q)" -> """
          .MACRO BVCA ADDRESS
            BVC ADDRESS
          .ENDMACRO
          """;

      case "BVSA(Q)" -> """
          .MACRO BVSA ADDRESS
            BVS ADDRESS
          .ENDMACRO
          """;

      case "INCW(R)" -> """
          .MACRO INCW ADDRESS
            .LOCAL @SKIP
            INC ADDRESS+0
            BNE @SKIP
            INC ADDRESS+1
            @SKIP:
          .ENDMACRO
          """;

      case "SKIP1" -> """
          .MACRO SKIP1 ADDRESS
            .BYTE $24 ; BIT ZERO PAGE
          .ENDMACRO
          """;

      case "SKIP2" -> """
          .MACRO SKIP2 ADDRESS
            .BYTE $2C ; BIT ABS
          .ENDMACRO
          """;

      case "DCI(A)" -> """
          .MACRO DCI STR
            .REPEAT .STRLEN(STR)-1,I
              .BYTE .STRAT(STR,I)
            .ENDREP
            .BYTE .STRAT(STR,.STRLEN(STR)-1) | $80
            Q .SET Q+1
          .ENDMACRO
          """;

      case "DCE(X)" -> """
          .MACRO DCE STR
            .REPEAT .STRLEN(STR)-1,I
              .BYTE .STRAT(STR,I)
            .ENDREP
            .BYTE .STRAT(STR,.STRLEN(STR)-1) | $80
            Q .SET Q+2
          .ENDMACRO
          """;

      default -> null;
    };

    return (macro + "\n").lines().toList();
  }

  // convert symbol definitions like e.g. ROMLOC= ^O20000
  // convert octal numbers to hexadecimal
  // make symbol values available for further processing
  private List<String> convertSymbols(List<String> lines) {
    List<String> result = new ArrayList<>();
    for (String line : lines) {
      Line parsed = getLine(line);
      Matcher matchSimpleSymbolDefinition =
          Pattern.compile("^(\\s*)([A-Z]+)\\s*={1,2}\\s*(.*)$").matcher(parsed.instruction());
      if (matchSimpleSymbolDefinition.matches()) {
        String space = matchSimpleSymbolDefinition.group(1);
        String name = matchSimpleSymbolDefinition.group(2);
        String value = matchSimpleSymbolDefinition.group(3);
        if (value.startsWith("^O")) {
          int number = Integer.parseInt(value.substring(2), 8);
          value = String.format("$%04X", number);
        }
        symbols.put(name, value);

        if (varNames.contains(name)) {
          String replace = space + name + " .SET " + value + parsed.comment();
          result.add(replace);
        } else {
          String replace = space + name + "=" + value + parsed.comment();
          result.add(replace);
        }
        continue;
      }
      result.add(line);
    }

    return result;
  }

  // convert MACRO-10 REPEAT statement
  private List<String> convertRepeat(List<String> lines) {
    List<String> result = new ArrayList<>();
    while (!lines.isEmpty()) {
      String line = lines.removeFirst();
      Matcher matcher = Pattern.compile("^(\\s*)REPEAT\\s+(\\S+),\\s*<(.*)").matcher(line);
      if (matcher.matches()) {
        String space = matcher.group(1);
        String expr = matcher.group(2);
        int count = 0;
        if (expr.matches("\\d")) {
          count = Integer.parseInt(expr);
        } else if (expr.equals("3+ADDPRC")) {
          count = 3 + Integer.parseInt(symbols.get("ADDPRC"));
        } else {
          throw new IllegalArgumentException("unsupported expression" + expr);
        }
        String code = matcher.group(3);
        Block block = getAngledBlock(code, lines); // consume/delete block
        String expand = block.lines().get(0);
        if (block.lines.size() > 1) {
          expand = block.lines().get(1);
        }
        for (int i = 0; i < count; i++) {
          result.add(space + expand);
        }
      } else {
        result.add(line);
      }
    }
    return result;
  }

  // convert operation instructions and data definitions
  private List<String> convertInstructions(List<String> lines) {
    int radix = 8;
    List<String> result = new ArrayList<>();
    for (String line : lines) {
      Line parsed = getLine(line);

      // look for radix statement and store radix value
      Matcher matchRadix = Pattern.compile("^RADIX\\s(\\d+)").matcher(parsed.instruction());
      if (matchRadix.find()) {
        radix = Integer.parseInt(matchRadix.group(1)); // 8=octal or 10=decimal
        continue;
      }

      // remove all ORG statements
      Matcher matchOrg = Pattern.compile("^\\s*ORG\\s(\\S+)").matcher(parsed.instruction());
      if (matchOrg.find()) {
        continue;
      }

      // convert octal numbers to hexadecimal
      Matcher matchOctal = Pattern.compile(".*\\^O(\\d+).*").matcher(parsed.instruction());
      if (matchOctal.matches()) {
        int decimal = Integer.parseInt(matchOctal.group(1), 8);
        String digits = String.valueOf((decimal > 0xff) ? 4 : 2);
        String hex = String.format("$%0" + digits + "X", decimal);
        line = line.replace("^O" + matchOctal.group(1), hex);
        parsed = getLine(line);
      }

      // ADR -> .WORD
      Matcher matchAdr = Pattern.compile("^ADR\\t*\\((\\S+)\\)$").matcher(parsed.instruction());
      if (matchAdr.matches()) {
        String replace = parsed.label() + ".WORD " + matchAdr.group(1) + parsed.comment();
        result.add(replace);
        continue;
      }

      // BLOCK -> .RES
      Matcher matchBlock = Pattern.compile("^BLOCK\\s+(.*)$").matcher(parsed.instruction());
      if (matchBlock.matches() && !line.contains("BLOCK TRANSFER")) {
        String replace = parsed.label() + ".RES " + matchBlock.group(1) + parsed.comment();
        result.add(replace);
        continue;
      }

      // <decimal number> -> .BYTE
      Matcher matchByteDec = Pattern.compile("^(\\d+)$").matcher(parsed.instruction());
      if (matchByteDec.matches()) {
        int number = Integer.parseInt(matchByteDec.group(1), radix);
        String replace = parsed.label() + ".BYTE " + number + parsed.comment();
        result.add(replace);
        continue;
      }

      // <hexadecimal number> -> .BYTE
      Matcher matchByteHex = Pattern.compile("^\\$([0-9A-F]+)$").matcher(parsed.instruction());
      if (matchByteHex.matches()) {
        int number = Integer.parseInt(matchByteHex.group(1), 16);
        String replace = parsed.label() + ".BYTE " + number + parsed.comment();
        result.add(replace);
        continue;
      }

      // EXP -> .BYTE
      if (parsed.instruction().startsWith("EXP")) {
        Matcher matchExp = Pattern.compile("^EXP\\s+(.*)$").matcher(parsed.instruction());
        if (matchExp.matches()) {
          String replace = parsed.label() + ".BYTE " + matchExp.group(1) + parsed.comment();
          result.add(replace);
          continue;
        }
      }

      // expression "333-ADDPRC" -> .BYTE
      if (radix == 8 && parsed.instruction().equals("333-ADDPRC")) {
        String replace = parsed.label() + ".BYTE 219-ADDPRC" + parsed.comment();
        result.add(replace);
        continue;
      }

      // expression <symbol> -> .BYTE
      if (symbols.containsKey(parsed.instruction())) {
        String replace = parsed.label() + ".BYTE " + parsed.instruction() + parsed.comment();
        result.add(replace);
        continue;
      }

      // convert mnemonic macros
      String converted = convertMnemonic(parsed.instruction(), radix);
      if (converted != null) {
        String replace = parsed.label() + converted + parsed.comment();
        result.add(replace);
        continue;
      }

      result.add(line);
    }

    return result;
  }

  // convert mnemonics to cc65 syntax
  private String convertMnemonic(String instruction, int radix) {
    Matcher matchMnemonic = Pattern.compile("^([A-Z]+)\\s+(.*)$").matcher(instruction);
    if (!matchMnemonic.matches()) {
      return null;
    }
    String mnemonic = matchMnemonic.group(1);
    String arg = matchMnemonic.group(2);
    switch (mnemonic) {
      case "ADCI", "ANDI", "CMPI", "CPXI", "CPYI", "EORI", "LDAI", "LDXI", "LDYI", "ORAI", "SBCI":
        Matcher matchNumber = Pattern.compile("\\d+").matcher(arg);
        if (radix == 8 && matchNumber.matches()) {
          int number = Integer.parseInt(arg, radix);
          arg = String.format("$%02X", number); // convert octal number to hexadecimal
        }
        arg = expressions.getOrDefault(arg, arg);
        return mnemonic.substring(0, mnemonic.length() - 1) + "\t#" + arg.replace("\"", "\'");

      case "ADCDY", "CMPDY", "LDADY", "SBCDY", "STADY":
        return mnemonic.substring(0, 3) + "\t(" + arg + "),Y";

      case "JMPD":
        return "JMP\t(" + arg + ")";

      default:
        return null;
    }
  }

  private String expandTabs(String line) {
    StringBuilder result = new StringBuilder();
    int col = 0;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '\t') {
        int nextStop = ((col / 8) + 1) * 8;
        result.append(" ".repeat(nextStop - col));
        col = nextStop;
      } else {
        result.append(ch);
        col++;
      }
    }
    return result.toString();
  }

  private List<String> expandTabs(List<String> lines) {
    List<String> result = new ArrayList<>();

    while (!lines.isEmpty()) {
      String line = lines.removeFirst();
      line = expandTabs(line);
      result.add(line);
    }
    return result;
  }

  // search and replace text block supporting blocks with multiple lines
  // if replace is an empty string the complete search text block is removed
  private List<String> replaceTextBlock(List<String> inputLines, String search, String replace) {
    List<String> searchLines = search.lines().toList();
    List<String> replaceLines = replace.lines().toList();
    List<String> result = new ArrayList<>();

    int i = 0;
    int inputLinesSize = inputLines.size();
    int searchLinesSize = searchLines.size();

    while (i < inputLinesSize) {
      if (i + searchLinesSize <= inputLinesSize) {
        boolean match = true;
        for (int j = 0; j < searchLinesSize; j++) {
          if (!inputLines.get(i + j).equals(searchLines.get(j))) {
            match = false;
            break;
          }
        }
        if (match) {
          result.addAll(replaceLines);
          i += searchLinesSize;
          continue;
        }
      }
      result.add(inputLines.get(i));
      i++;
    }

    return result;
  }

  // parse line and split to label - instruction - comment
  private Line getLine(String line) {
    // split out comment from line
    String comment = "";
    String instruction = line;
    int index = line.indexOf(';');
    if (index != -1) {
      while (index > 0 && Character.isWhitespace(line.charAt(index - 1))) {
        index--; // include preceding white space
      }
      comment = line.substring(index);
      instruction = line.substring(0, index);
    }

    // split out label from line
    String label = "";
    Matcher matchLabel = Pattern.compile("^([A-Z\\d]+):(\\s*)(.*)$").matcher(instruction);
    if (matchLabel.matches()) {
      label = matchLabel.group(1) + ":" + matchLabel.group(2);
      instruction = matchLabel.group(3);
    }

    // move white spaces of instruction to to label
    Matcher matchSpace = Pattern.compile("^(\\s+)(.*)$").matcher(instruction);
    if (matchSpace.matches()) {
      label += matchSpace.group(1);
      instruction = matchSpace.group(2);
    }

    return new Line(label, instruction, comment, line);
  }

  // get all text starting after the first opening angle bracket up to the closing angle bracket.
  // text may contain nested blocks which are treated as transparent text
  // the remaining text after the closing angle bracket is returned in trailing
  private Block getAngledBlock(String code, List<String> lines) {
    List<String> result = new ArrayList<>();
    String line = code;
    int level = 1;
    while (line != null) {
      StringBuilder buffer = new StringBuilder();
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (c == '<') {
          level++;
        } else if (c == '>') {
          level--;
        }
        if (level != 0) {
          buffer.append(c);
        }

        if (level == 0) {
          result.add(buffer.toString());
          if (result.getFirst().isEmpty()) {
            result.removeFirst();
          }
          return new Block(result, line.substring(i + 1));
        }
      }
      result.add(line);
      line = lines.isEmpty() ? null : lines.removeFirst(); // fetch next line
    }

    throw new IllegalArgumentException("missing '>'");
  }
}
