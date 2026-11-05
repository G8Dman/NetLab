package dev.netlab.core;

import java.util.*;

/** One context-sensitive keyword tree for execution and completion. */
public final class CommandGrammar {
  public record Spec(String words, int minArgs, int maxArgs, String usage) {}
  /** Read-only command catalogue shared by the handbook and the CLI grammar. */
  public static List<Spec> reference(Console cli,Device device){return List.copyOf(commands(cli,device));}
  private static final class Node {
    final SortedMap<String, Node> children = new TreeMap<>();
    Spec command;
  }
  public record Resolved(String value, String error) {}
  public record Completion(String value, List<String> choices) {}
  private static List<Spec> commands(Console cli, Device device) {
    var specs = new ArrayList<Spec>();
    add(specs, "exit", 0, 0, "");
    add(specs, "help", 0, 0, "");
    if(device.kind.equals("pc")){specs.addAll(PCCommands.specs(device));return specs;}
    specs.addAll(ConfigurationCommands.specs(cli,device));
    if (cli.mode < 2) {
      add(specs, "enable", 0, 0, "");
      add(specs, "show ip interface brief", 0, 0, "");
      add(specs, "show ip route", 0, 0, "");
      add(specs, "show running-config", 0, 0, "");
      add(specs, "ping", 1, 1, "IP");
      if (cli.mode == 1) {
        add(specs, "configure terminal", 0, 0, "");
        add(specs, "disable", 0, 0, "");
      }
    } else {
      add(specs, "end", 0, 0, "");
      add(specs, "hostname", 1, 1, "NAME");
      add(specs, "interface", 1, 2, "INTERFACE");
      add(specs, "ip default-gateway", 1, 1, "IP");
      if (DeviceKinds.router(device.kind))
        add(specs, "ip route", 3, 4, "NETWORK MASK NEXT_HOP");
      add(specs, "do show ip interface brief", 0, 0, "");
      add(specs, "do show ip route", 0, 0, "");
      add(specs, "do show running-config", 0, 0, "");
      add(specs, "do ping", 1, 1, "IP");
      if (cli.mode == 3 && cli.selected >= 0) {
        add(specs, "shutdown", 0, 0, "");
        add(specs, "no shutdown", 0, 0, "");
        if (!device.kind.equals("switch"))
          add(specs, "ip address", device.kind.equals("router") ? 1 : 2, 2, "IP MASK");
      }
    }
    if (device.kind.equals("pc"))
      specs.addAll(PCCommands.specs(device));
    if (device.kind.equals("switch") && cli.mode == 3) {
      add(specs, "switchport mode trunk", 0, 0, "");
      add(specs, "switchport mode access", 0, 0, "");
      add(specs, "switchport access vlan", 1, 1, "VLAN");
      add(specs, "switchport trunk native vlan", 1, 1, "VLAN");
    }
    if (device.kind.equals("router")) {
      specs.addAll(RouterCommands.specs(cli, device));
      if(device.ports.stream().anyMatch(p->p.l2))specs.addAll(SwitchCommands.specs(cli,device));
      if (cli.mode >= 2) {
        var executive = new Console();
        executive.mode = 1;
        for (var spec : RouterCommands.specs(executive, device))
          if (spec.words().startsWith("show "))
            add(specs, "do " + spec.words(), spec.minArgs(), spec.maxArgs(), spec.usage());
      }
      // `show ip interface` is a parameter leaf and also has literal `brief`.
      // Resolve literals first, then fall back to the interface argument.
    }
    if (device.kind.equals("switch")) {
      specs.addAll(SwitchCommands.specs(cli, device));
      if (cli.mode >= 2) {
        var executive = new Console(); executive.mode = 1;
        for (var spec : SwitchCommands.specs(executive, device))
          if (spec.words().startsWith("show ")) add(specs, "do " + spec.words(), spec.minArgs(), spec.maxArgs(), spec.usage());
      }
    }
    return specs;
  }
  private static void add(List<Spec> specs, String words, int min, int max, String usage) {
    specs.add(new Spec(words, min, max, usage));
  }
  private static Node tree(Console cli, Device device) {
    Node root = new Node();
    for (Spec spec : commands(cli, device)) {
      Node at = root;
      for (String word : spec.words.split(" ")) at = at.children.computeIfAbsent(word, key -> new Node());
      at.command = spec;
    }
    return root;
  }
  public static String keyword(String prefix, List<String> words) {
    String lower = prefix.toLowerCase(Locale.ROOT);
    if (words.contains(lower))
      return lower;
    var choices = words.stream().filter(w -> w.startsWith(lower)).toList();
    if (choices.size() != 1)
      throw new IllegalArgumentException(
          choices.isEmpty() ? "Invalid keyword: " + prefix : "Ambiguous keyword: " + prefix);
    return choices.getFirst();
  }
  private static List<String> matching(Node node, String prefix) {
    String lower = prefix.toLowerCase(Locale.ROOT);
    if (node.children.containsKey(lower))
      return List.of(lower);
    return node.children.keySet().stream().filter(word -> word.startsWith(lower)).toList();
  }
  public static Resolved resolve(Console cli, Device device, String line) {
    String trimmed = line.strip();
    if (trimmed.isEmpty())
      return new Resolved("", "");
    String[] input = trimmed.split("\\s+");
    Node node = tree(cli, device);
    var output = new ArrayList<String>();
    for (int i = 0; i < input.length; i++) {
      if (node.command != null && matching(node, input[i]).isEmpty()) {
        int count = input.length - i;
        if (count < node.command.minArgs)
          return new Resolved("", "% Incomplete command: " + node.command.words + " " + node.command.usage);
        if (count > node.command.maxArgs)
          return new Resolved("", "% Invalid arguments: " + node.command.words + " " + node.command.usage);
        output.addAll(Arrays.asList(input).subList(i, input.length));
        return new Resolved(String.join(" ", output), "");
      }
      var candidates = matching(node, input[i]);
      if (candidates.size() > 1 && i > 0 && (input[i - 1].equalsIgnoreCase("no")
          || (!output.isEmpty() && output.getLast().equals("no")))
          && input[i].equalsIgnoreCase("s") && candidates.contains("shutdown"))
        candidates = List.of("shutdown");
      if (candidates.size() > 1)
        return new Resolved(
            "", "% Ambiguous command: " + input[i] + " (" + String.join(", ", candidates) + ")");
      if (candidates.isEmpty()) {
        if (cli.mode < 2
            && (input[0].equalsIgnoreCase("ip") || "interface".startsWith(input[0].toLowerCase(Locale.ROOT))))
          return new Resolved("", "% Configuration requires: enable, configure terminal");
        return new Resolved("", "% Invalid command at '" + input[i] + "'. Type ?");
      }
      String word = candidates.getFirst();
      output.add(word);
      node = node.children.get(word);
    }
    if (node.command == null || node.command.minArgs > 0)
      return new Resolved("",
          "% Incomplete command"
              + (node.command == null ? "" : ": " + node.command.words + " " + node.command.usage));
    return new Resolved(String.join(" ", output), "");
  }
  public static int interfaceIndex(Device device, String text) {
    return device.findInterface(text, false);
  }
  private static String commonPrefix(List<String> values) {
    if (values.isEmpty())
      return "";
    String prefix = values.getFirst();
    for (String value : values)
      while (!value.startsWith(prefix)) prefix = prefix.substring(0, prefix.length() - 1);
    return prefix;
  }
  /** Completes only the text before the caret; the GUI retains text after it. */
  public static Completion complete(Console cli, Device device, String prefix) {
    String clean = prefix.stripLeading();
    boolean space = !clean.isEmpty() && Character.isWhitespace(clean.charAt(clean.length() - 1));
    List<String> input =
        new ArrayList<>(clean.isBlank() ? List.of() : Arrays.asList(clean.stripTrailing().split("\\s+")));
    String fragment = space || input.isEmpty() ? "" : input.removeLast();
    Node node = tree(cli, device);
    List<String> normalized = new ArrayList<>();
    for (String token : input) {
      if (node.command != null && matching(node, token).isEmpty())
        return completeArgument(cli, device, node.command, normalized, input, fragment, prefix, space);
      var candidates = matching(node, token);
      if (candidates.size() != 1)
        return new Completion(prefix, List.of());
      String word = candidates.getFirst();
      normalized.add(word);
      node = node.children.get(word);
    }
    if (node.command != null
        && (node.children.isEmpty() || !fragment.isEmpty() && matching(node, fragment).isEmpty()))
      return completeArgument(cli, device, node.command, normalized, input, fragment, prefix, space);
    var candidates = matching(node, fragment);
    String base = normalized.isEmpty() ? "" : String.join(" ", normalized) + " ";
    List<String> choices = candidates.stream().map(word -> base + word + " ").toList();
    if (choices.isEmpty())
      return new Completion(prefix, List.of());
    return new Completion(
        choices.size() == 1 ? choices.getFirst() : base + commonPrefix(candidates), choices);
  }
  private static Completion completeArgument(Console cli, Device device, Spec spec, List<String> normalized,
      List<String> input, String fragment, String original, boolean space) {
    if (!spec.words.equals("interface"))
      return device.kind.equals("switch") ? SwitchCompletions.complete(cli,device,spec,input,fragment,original) : device.kind.equals("router")
          ? RouterCompletions.complete(cli, device, spec, input, fragment, original)
          : new Completion(original, List.of());
    // The keyword occupies one token; interface family and number may be separated.
    String typed = String.join("", input.subList(Math.min(1, input.size()), input.size())) + fragment;
    var choices = new ArrayList<String>();
    for (int i = 0; i < device.ports.size(); i++) {
      if (device.ports.get(i).hidden)
        continue;
      String name = device.portName(i);
      String lower = name.toLowerCase(Locale.ROOT), candidate = typed.toLowerCase(Locale.ROOT);
      boolean matches = lower.startsWith(candidate) || interfaceIndex(device, typed) == i;
      if (!matches && candidate.matches("[a-z]+[0-9/]*")) {
        String family = candidate.replaceAll("[0-9/].*", "");
        String number = candidate.substring(family.length());
        String full = lower.replaceAll("[0-9].*", "");
        matches = full.startsWith(family) && lower.substring(full.length()).startsWith(number);
      }
      if (matches)
        choices.add("interface " + name + " ");
    }
    if (choices.isEmpty())
      return new Completion(original, List.of());
    return new Completion(
        choices.size() == 1 ? choices.getFirst() : commonPrefix(choices), List.copyOf(choices));
  }
  public static String help(Console cli, Device device) {
    return contextHelp(cli, device, "");
  }
  /** `?` describes the next token; Tab remains an editor operation with concrete completions. */
  public static String contextHelp(Console cli, Device device, String prefix) {
    String clean = prefix.stripLeading();
    boolean space = !clean.isEmpty() && Character.isWhitespace(clean.charAt(clean.length() - 1));
    var input = new ArrayList<>(clean.isBlank() ? List.<String>of() : Arrays.asList(clean.stripTrailing().split("\\s+")));
    String fragment = space || input.isEmpty() ? "" : input.removeLast();
    Node node = tree(cli, device);
    var normalized = new ArrayList<String>();
    for (int i = 0; i < input.size(); i++) {
      var candidates = matching(node, input.get(i));
      if (node.command != null && candidates.isEmpty())
        return argumentHelp(cli, device, node.command, normalized,
            input.subList(i, input.size()), fragment);
      if (candidates.size() != 1) return "% Ambiguous or invalid help context";
      String word = candidates.getFirst();
      normalized.add(word);
      node = node.children.get(word);
    }
    var result = new ArrayList<String>();
    String base = normalized.isEmpty() ? "" : String.join(" ", normalized) + " ";
    for (String word : matching(node, fragment)) result.add(base + word);
    if (node.command != null) {
      String arguments = argumentHelp(cli, device, node.command, normalized, List.of(), fragment);
      if (!arguments.startsWith("%")) result.addAll(Arrays.asList(arguments.split("\n")));
    }
    return result.isEmpty() ? "% No matching keywords or arguments" : String.join("\n", new LinkedHashSet<>(result));
  }
  private static String argumentHelp(Console cli, Device d, Spec spec, List<String> normalized,
      List<String> args, String fragment) {
    int index = args.size();
    var result = new LinkedHashSet<String>();
    if (fragment.isEmpty() && index >= spec.minArgs() && index <= spec.maxArgs()) result.add("<cr>");
    if (index >= spec.maxArgs()) return result.isEmpty() ? "% Too many arguments" : String.join("\n", result);
    String[] usage = spec.usage().replace("[", "").replace("]", "").split("\\s+");
    String parameter = usage[Math.min(index, usage.length - 1)];
    if (parameter.equals("VLAN") || parameter.startsWith("VLAN,")) {
      result.add("<1-4094>  VLAN IDs 1-4094 (normal range 1-1005)");
    } else {
      var input = new ArrayList<>(normalized); input.addAll(args);
      String base = String.join(" ", input) + " ";
      var completion = completeArgument(cli, d, spec, normalized, input, fragment, base + fragment, fragment.isEmpty());
      for (String choice : completion.choices()) result.add(choice.stripTrailing());
      if (parameter.contains("|")) {
        for (String choice : parameter.split("\\|"))
          if (choice.toLowerCase(Locale.ROOT).startsWith(fragment.toLowerCase(Locale.ROOT))) result.add(base + choice);
      } else if (parameter.equals("IP") || parameter.equals("MASK") || parameter.equals("NETWORK") || parameter.equals("NEXT_HOP")) {
        result.add("A.B.C.D  " + parameter);
      } else if (!parameter.isEmpty() && (result.isEmpty() || result.equals(Set.of("<cr>")))) {
        result.add(parameter + "  " + (parameter.equals("INTERFACE") ? "Interface name" : "Argument"));
      }
    }
    return result.isEmpty() ? "% No matching arguments" : String.join("\n", result);
  }
}
