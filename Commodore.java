import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Commodore {
  private final Set<String> trueConditions = new HashSet<>();
  private final Set<String> falseConditions = new HashSet<>();
  private final Set<String> removeLines = new HashSet<>();
  private final Map<String, String> replaceLines = new HashMap<>();

  public Commodore(boolean extIO) {
    // REALIO=3 target
    trueConditions.add("REALIO<>1");
    trueConditions.add("REALIO=3");
    trueConditions.add("REALIO<>0");
    trueConditions.add("REALIO<>2");
    trueConditions.add("REALIO<>4");
    trueConditions.add("(REALIO|LONGI)<>0");
    falseConditions.add("REALIO <> 1 .AND REALIO <> 2 .AND REALIO <> 3 .AND REALIO <> 4");
    falseConditions.add("REALIO=0");
    falseConditions.add("REALIO<>3");
    falseConditions.add("REALIO=1");
    falseConditions.add("REALIO=2");
    falseConditions.add("REALIO=4");
    falseConditions.add("REALIO=5");
    falseConditions.add("(REALIO|LONGI)=0");
    falseConditions.add("(REALIO|DISKO)=0");
    removeLines.add(";5=STM");
    removeLines.add(";4=APPLE.");
    removeLines.add(";3=COMMODORE.");
    removeLines.add(";2=OSI");
    removeLines.add(";1=MOS TECH,KIM");
    removeLines.add(";0=PDP-10 SIMULATING 6502");
    removeLines.add(".OUT");

    // ROMSW=1 basic is provided in ROM
    trueConditions.add("ROMSW<>0");
    falseConditions.add("ROMSW=0");
    removeLines.add("ROMSW .SET 1");

    // KIMROM=0 no KIM-1 ROM
    trueConditions.add("KIMROM=0");
    falseConditions.add("KIMROM<>0");
    removeLines.add("KIMROM .SET");

    // RORSW=1 use ROR instruction instead of emulating it
    trueConditions.add("RORSW<>0");
    falseConditions.add("RORSW=0");
    removeLines.add("RORSW .SET 1");

    // ADDPRC=1 for additional precision for floating point values
    trueConditions.add("ADDPRC<>0");
    falseConditions.add("ADDPRC=0");
    replaceLines.put(
        "ADDPRC=1                        ;FOR ADDITIONAL PRECISION.",
        "ADDPRC=1                ;FOR ADDITIONAL PRECISION.");
    // INTPRC=1 integer arrays
    trueConditions.add("INTPRC<>0");
    falseConditions.add("INTPRC=0");
    removeLines.add(";INTEGER ARRAYS.");

    // LNGERR=1 long error messages
    falseConditions.add("LNGERR=0");
    trueConditions.add("LNGERR<>0");
    removeLines.add("LNGERR=1");
    replaceLines.put("BUFPAG .SET 2", "BUFPAG=2");

    // BUFPAG=2 page of input buffer
    trueConditions.add("BUFPAG<>0");
    falseConditions.add("BUFPAG=0");
    trueConditions.add("1 ; ((BUF+BUFLEN)/256)-((BUF-1)/256)");
    removeLines.add("BUFPAG .SET 0");

    // NULCMD=0 exclude "NULL" command
    trueConditions.add("NULCMD=0");
    falseConditions.add("NULCMD<>0");
    removeLines.add("NULCMD .SET");

    // GETCMD=1 include "GET" command
    trueConditions.add("GETCMD<>0");
    removeLines.add("GETCMD .SET 1");

    // TIME=1 capability to set and read a clk.
    trueConditions.add("TIME<>0");
    trueConditions.add("(TIME|EXTIO)<>0");
    trueConditions.add("(EXTIO|TIME)<>0");
    removeLines.add("TIME .SET");

    // STKEND=507 end of stack
    trueConditions.add("STKEND<>511");
    removeLines.add("STKEND .SET 511");
    replaceLines.put("STKEND .SET 507", "STKEND=507");

    // EXTIO external I/O
    if (extIO) {
      trueConditions.add("EXTIO<>0");
      falseConditions.add("EXTIO=0");
      removeLines.add("EXTIO .SET");
    } else {
      trueConditions.add("EXTIO=0");
      falseConditions.add("EXTIO<>0");
      removeLines.add("EXTIO .SET");
    }

    // DISKO=1 include disk commands
    trueConditions.add("DISKO<>0");
    removeLines.add("DISKO .SET");

    // LINLEN=40 terminal line length
    removeLines.add("LINLEN .SET 72");
    replaceLines.put("LINLEN .SET 40", "LINLEN=40");

    // BUFLEN=81 input buffer size
    removeLines.add("BUFLEN .SET 72");
    replaceLines.put("BUFLEN .SET 81", "BUFLEN=81");

    // ROMLOC=$C000 start of ROM
    removeLines.add("ROMLOC .SET $2000");
    replaceLines.put("ROMLOC .SET $C000", "ROMLOC=$C000");

    // RAMLOC=$0400 start or RAM
    removeLines.add("RAMLOC .SET $4000");
    replaceLines.put("RAMLOC .SET $0400", "RAMLOC=$0400");

    // CLMWID=10 column width for PRINT with commas
    removeLines.add("CLMWID .SET 14");
    replaceLines.put("CLMWID .SET 10", "CLMWID=10");

    // BUFOFS=2 BUFOFS=(BUF/256)*256; BUF=256*BUFPAG
    removeLines.add("BUFOFS .SET (BUF/256)*256");
    replaceLines.put(
        "BUFOFS .SET 0                   ;THE AMOUNT TO OFFSET THE LOW BYTE",
        "BUFOFS=(BUF/256)*256            ;THE AMOUNT TO OFFSET THE LOW BYTE");
  }

  private static List<String> getBlock(List<String> lines) {
    List<String> result = new ArrayList<>();
    int level = 1;
    while (!lines.isEmpty()) {
      String line = lines.removeFirst();
      if (line.startsWith(".IF")) {
        level++;
      } else if (line.startsWith(".ENDIF")) {
        level--;
        if (level == 0) {
          return result;
        }
      }
      result.add(line);
    }
    return result;
  }

  private List<String> resolveIf(List<String> lines) {
    List<String> result = new ArrayList<>();
    while (!lines.isEmpty()) {
      String line = lines.removeFirst();
      if (line.startsWith(".IF")) {
        String condition = line.substring(4);
        List<String> block = getBlock(lines);

        if (trueConditions.contains(condition)) {
          result.addAll(block);
          continue;
        }

        if (falseConditions.contains(condition)) {
          continue;
        }

        result.add(".IF " + condition);
        result.addAll(block);
        result.add(".ENDIF");
      } else {
        result.add(line);
      }
    }
    return result;
  }

  private List<String> processLines(List<String> lines) {
    List<String> result = new ArrayList<>();
    while (!lines.isEmpty()) {
      String line = lines.removeFirst();
      boolean remove = false;
      for (String search : removeLines) {
        if (line.contains(search)) {
          remove = true;
          break;
        }
      }
      if (remove) {
        continue;
      }

      for (var entry : replaceLines.entrySet()) {
        if (line.equals(entry.getKey())) {
          line = entry.getValue();
          break;
        }
      }
      result.add(line);

    }
    return result;
  }

  private List<String> replaceSet(List<String> lines) {
    List<String> result = new ArrayList<>();
    while (!lines.isEmpty()) {
      String line = lines.removeFirst();
      if (line.contains(".SET") && !line.contains("Q .SET")) {
        System.out.println(line);
      }

      result.add(line);
    }
    return result;
  }

  public static void convert(Path inputFile, Path outputFile, boolean extIO) throws IOException {
    Commodore commodore = new Commodore(extIO);
    List<String> result = Files.readAllLines(inputFile);
    for (int i = 0; i < 6; i++) {
      result = commodore.resolveIf(result);
    }
    result = commodore.processLines(result);
    result = commodore.replaceSet(result);
    Files.write(outputFile, result);
  }

  public static void main(String... args) throws IOException {
    if (args.length != 2) {
      throw new IllegalArgumentException("missing input and output filename");
    }

    convert(Path.of(args[0]), Path.of(args[1]), true/* extIO */);
  }
}
