import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Formatter {

  private Map<String, String> overwrites = new HashMap<>();
  private Map<String, String> defines = new HashMap<>();

  // symbol names which define target specific configuration
  private List<String> configs =
      List.of("ADDPRC", "BUFLEN", "BUFOFS", "BUFPAG", "CBMRND", "CLMWID", "DISKO", "EXTIO", "GETCMD",
          "INTPRC", "KIMROM", "LINLEN", "LNGERR", "LONGI", "NULCMD", "RAMLOC",
          "ROMLOC", "ROMSW", "RORSW", "STKEND", "TIME");

  // remove configuration symbol after conditional statements have be resolved
  private List<String> removeConfigs =
      List.of("CBMRND", "DISKO", "EXTIO", "GETCMD", "INTPRC", "KIMROM", "LNGERR", "LONGI", "NULCMD", "ROMSW", "RORSW",
          "TIME");

  // regex patterns to remove unwanted lines
  private List<String> removePatterns = List.of(
      "^.*;5=STM$",
      "^.*;4=APPLE.$",
      "^.*;3=COMMODORE.$",
      "^.*;2=OSI$",
      "^.*;1=MOS TECH,KIM$",
      "^.*;0=PDP-10 SIMULATING 6502$",
      "^.*\\.OUT.*");

  public Formatter(List<String> options) {
    for (String overwrite : options) {
      String name = overwrite.split("=")[0];
      String value = overwrite.split("=")[1];
      this.overwrites.put(name, value);
    }
  }

  private String getDefine(String name) {
    return overwrites.getOrDefault(name, defines.get(name));
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

  private Boolean evaluateCondition(String condition) {
    if (condition.equals("REALIO <> 1 .AND REALIO <> 2 .AND REALIO <> 3 .AND REALIO <> 4")) {
      return Boolean.FALSE; // remove unwanted REALIO check
    }

    Matcher simple = Pattern.compile("^([A-Z]+)(<>|=)([0-9]+)$").matcher(condition);
    if (simple.matches()) {
      String name = simple.group(1);
      String actual = getDefine(name);
      String expected = simple.group(3);
      if (actual == null) {
        return null; // symbol not yet defined
      }

      String operator = simple.group(2);
      if (operator.equals("=")) {
        return actual.equals(expected);
      }

      return !actual.equals(expected);
    }

    Matcher or = Pattern.compile("^\\((\\S+)\\)(<>|=)([0-9]+)$").matcher(condition);
    if (or.matches()) {
      String actual0 = getDefine(or.group(1).split("\\|")[0]);
      String actual1 = getDefine(or.group(1).split("\\|")[1]);
      if (actual0 == null || actual1 == null) {
        return null; // symbols not yet define
      }

      String operator = or.group(2);
      int expected = Integer.parseInt(or.group(3));
      int actual = Integer.parseInt(actual0) | Integer.parseInt(actual1);
      if (operator.equals("=")) {
        return actual == expected;
      }

      return actual != expected;
    }

    return null;
  }

  // update actual value of configuration symbol
  private void updateSymbols(String line) {
    for (String config : configs) {
      Matcher matcher = Pattern.compile("^" + config + "(=| .SET )([0-9]*).*$").matcher(line);
      if (matcher.matches()) {
        defines.put(config, matcher.group(2));
      }
    }
  }

  // resolve all .IF control commands (single pass)
  private List<String> resolveIfSingle(List<String> lines) {
    List<String> result = new ArrayList<>();
    while (!lines.isEmpty()) {
      String line = lines.removeFirst();
      updateSymbols(line);
      if (line.startsWith(".IF")) {
        String condition = line.substring(4);
        List<String> block = getBlock(lines);
        Boolean include = evaluateCondition(condition);
        if (include == Boolean.TRUE) {
          block.forEach(l -> updateSymbols(l));
          result.addAll(block);
          continue;
        } else if (include == Boolean.FALSE) {
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

  // resolve all .IF control commands (multiple pass)
  private List<String> resolveIf(List<String> lines) {
    List<String> previous = new ArrayList<>();
    while (true) {
      lines = resolveIfSingle(lines);
      if (lines.equals(previous)) {
        break; // repeat until no more changes
      }
      previous = new ArrayList<>(lines);
    }

    return lines;
  }

  // remove unwanted lines
  private List<String> remove(List<String> lines) {
    List<String> result = new ArrayList<>();
    for (String line : lines) {
      boolean remove = false;
      for (String removePattern : removePatterns) {
        if (line.matches(removePattern)) {
          remove = true;
          break;
        }
      }
      if (!remove) {
        result.add(line);
      }
    }
    return result;
  }

  // convert .SET to simple assignments
  private List<String> convertSet(List<String> lines) {
    List<String> result = new ArrayList<>(lines);
    for (String config : configs) {
      String lastMatch = null;
      // find the last matching .SET line for this config
      for (String line : result) {
        if (line.startsWith(config + " .SET ")) {
          lastMatch = line;
        }
      }

      List<String> newResult = new ArrayList<>();
      for (String line : result) {
        if (!line.startsWith(config + " .SET ")) {
          newResult.add(line);
        } else if (line.equals(lastMatch)) {
          newResult.add(line.replace(" .SET ", "="));
        }
      }

      result = newResult;
    }

    return result;
  }

  // remove configuration symbol after conditional statements have be resolved
  private List<String> removeConfig(List<String> lines) {
    List<String> result = new ArrayList<>();
    for (String line : lines) {
      boolean remove = false;
      for (String config : removeConfigs) {
        if (line.startsWith(config + "=")) {
          remove = true;
          break;
        }
      }
      if (!remove) {
        result.add(line);
      }
    }
    return result;
  }

  public static void main(String... args) throws IOException {
    if (args.length < 2) {
      System.err.println("ERROR: missing input and output filename");
      return;
    }

    Path inputFile = Path.of(args[0]);
    Path outputFile = Path.of(args[1]);
    List<String> options = Arrays.asList(Arrays.copyOfRange(args, 2, args.length));
    if (!options.stream().anyMatch(opt -> opt.startsWith("REALIO="))) {
      System.err.println("ERROR: missing required option REALIO");
      return;
    }

    Formatter formatter = new Formatter(options);
    System.out.printf("Create formatted source %s, %s\n", outputFile.getFileName(),
        String.join(", ", options));

    List<String> result = Files.readAllLines(inputFile);
    result = formatter.resolveIf(result);
    result = formatter.remove(result);
    result = formatter.convertSet(result);
    result = formatter.removeConfig(result);
    Files.write(outputFile, result);
  }
}
