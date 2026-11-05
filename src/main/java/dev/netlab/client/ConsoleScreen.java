package dev.netlab.client;

import dev.netlab.Packets;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ConsoleScreen extends Screen {
  public final BlockPos pos;
  private final List<String> log = new ArrayList<>(), history = new ArrayList<>();
  private EditBox input;
  private int left, top, panelWidth, panelHeight, scroll, historyPos;
  private boolean waiting;
  private String token="";
  private boolean terminal,connected=true;
  public String token(){return token;}
  public boolean isTerminal(){return terminal;}
  public void terminal(dev.netlab.TerminalPackets.Data data){
    terminal=true;connected=data.connected();token=data.token();if(data.replace())log.clear();if(!data.text().isEmpty())append(data.text());
    history.clear();history.addAll(Arrays.asList(new com.google.gson.Gson().fromJson(data.history(),String[].class)));historyPos=history.size();waiting=data.busy();
    if(input!=null){if(!connected)input.setValue("");input.setEditable(connected);}
  }
  private long sentAt;
  private List<String> completions = List.of();
  private String pendingText = "", completionSuffix = "", completionValue = "";
  private int pendingCursor, completionCursor, completionIndex = -1;
  private long completionSent;
  private boolean applyingCompletion, completionPending;
  private void clearCompletions() {
    completions = List.of();
    completionIndex = -1;
    completionPending = false;
  }
  private void requestCompletion(boolean backwards) {
    if (waiting)
      return;
    if (!completions.isEmpty() && input.getValue().equals(completionValue)
        && input.getCursorPosition() == completionCursor) {
      completionIndex = completionIndex < 0
          ? (backwards ? completions.size() - 1 : 0)
          : Math.floorMod(completionIndex + (backwards ? -1 : 1), completions.size());
      applyCompletion(completions.get(completionIndex));
      return;
    }
    if (completionPending && System.currentTimeMillis() - completionSent < 3000)
      return;
    clearCompletions();
    pendingText = input.getValue();
    pendingCursor = input.getCursorPosition();
    completionSent = System.currentTimeMillis();
    completionPending = true;
    PacketDistributor.sendToServer(new Packets.Complete(pos, pendingText.substring(0, pendingCursor)));
  }
  public void complete(Packets.Completed message) {
    if (!completionPending || !input.getValue().equals(pendingText)
        || input.getCursorPosition() != pendingCursor
        || !message.query().equals(pendingText.substring(0, pendingCursor)))
      return;
    completionPending = false;
    completionSuffix = pendingText.substring(pendingCursor);
    completions = message.choices().isEmpty() ? List.of() : message.choices().lines().toList();
    completionIndex = -1;
    applyCompletion(message.value());
    if (completions.size() < 2)
      completions = List.of();
  }
  private void applyCompletion(String prefix) {
    if (prefix.length() + completionSuffix.length() > 256)
      return;
    // Avoid doubling the space when completing a word before an existing suffix.
    if (prefix.endsWith(" ") && completionSuffix.startsWith(" "))
      prefix = prefix.stripTrailing();
    applyingCompletion = true;
    input.setValue(prefix + completionSuffix);
    input.setCursorPosition(prefix.length());
    applyingCompletion = false;
    completionValue = input.getValue();
    completionCursor = input.getCursorPosition();
  }
  public ConsoleScreen(BlockPos pos, String text) {
    super(Component.literal("NetLab Console"));
    this.pos = pos;
    append(text);
  }
  public void append(String text) {
    log.addAll(Arrays.asList(text.split("\n", -1)));
    while (log.size() > 1000) log.removeFirst();
    scroll = 0;
    waiting = false;
  }
  @Override
  protected void init() {
    panelWidth = Math.min(900, width - 24);
    panelHeight = height - 24;
    left = (width - panelWidth) / 2;
    top = 12;
    input = new EditBox(
        font, left + 12, top + panelHeight - 28, panelWidth - 90, 20, Component.literal("Command"));
    input.setMaxLength(256);
    input.setFormatter(
        (text, cursor)
            -> FormattedCharSequence.forward(!log.isEmpty() && log.getLast().strip().equals("Password:")
                    ? "*".repeat(text.length())
                    : text,
                net.minecraft.network.chat.Style.EMPTY));
    input.setHint(Component.literal("Tab — complete / ? — help / Enter — execute"));
    input.setResponder(text -> {
      if (!applyingCompletion)
        clearCompletions();
    });
    addRenderableWidget(input);
    addRenderableWidget(Button.builder(Component.literal("Send"), b -> send())
            .bounds(left + panelWidth - 70, top + panelHeight - 28, 58, 20)
            .build());
    setInitialFocus(input);
  }
  private void send() {
    if(terminal&&!connected)return;
    String command = input.getValue().trim();
    if (command.isEmpty() || waiting)
      return;
    if (!token.startsWith("p:")&&!dev.netlab.core.Console.sensitive(command)
        && (log.isEmpty() || !log.getLast().strip().equals("Password:")))
      history.add(command);
    historyPos = history.size();
    input.setValue("");
    waiting = true;
    sentAt = System.currentTimeMillis();
    PacketDistributor.sendToServer(new Packets.Command(pos, command,token));
  }
  @Override
  public boolean keyPressed(int key, int scan, int mods) {
    if(terminal&&!connected)return super.keyPressed(key,scan,mods);
    if (key == GLFW.GLFW_KEY_Z && hasControlDown()) {
      clearCompletions();
      PacketDistributor.sendToServer(new Packets.Command(pos, "\u001a",token));
      return true;
    }
    if (key == GLFW.GLFW_KEY_TAB) {
      requestCompletion(hasShiftDown());
      return true;
    }
    if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
      send();
      return true;
    }
    if (key == GLFW.GLFW_KEY_UP && !history.isEmpty()) {
      historyPos = Math.max(0, historyPos - 1);
      input.setValue(history.get(historyPos));
      return true;
    }
    if (key == GLFW.GLFW_KEY_DOWN) {
      historyPos = Math.min(history.size(), historyPos + 1);
      input.setValue(historyPos == history.size() ? "" : history.get(historyPos));
      return true;
    }
    return super.keyPressed(key, scan, mods);
  }
  @Override
  public boolean mouseScrolled(double x, double y, double dx, double dy) {
    scroll = Math.max(0, scroll + (int) (dy * 3));
    return true;
  }
  @Override
  public boolean isPauseScreen() {
    return false;
  }
  @Override public void removed(){if(token.startsWith("p:")){if(minecraft!=null&&minecraft.getConnection()!=null)PacketDistributor.sendToServer(new Packets.Command(pos,"__portable_close",token));history.clear();log.clear();}super.removed();}
  // Screen.render calls this before widgets. The console paints its own opaque
  // background; vanilla blur here would blur the already drawn terminal text.
  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
  @Override
  public void render(GuiGraphics g, int mx, int my, float dt) {
    g.fill(0, 0, width, height, 0xb0101720);
    g.fill(left, top, left + panelWidth, top + panelHeight, 0xff09121a);
    g.fill(left, top, left + panelWidth, top + 30, 0xff183241);
    g.drawString(font, "NETLAB  /  DEVICE CONSOLE", left + 12, top + 10, 0xff74d8ef, false);
    var lines = new ArrayList<FormattedCharSequence>();
    for (String line : log) lines.addAll(font.split(Component.literal(line), panelWidth - 30));
    int count = Math.max(1, (panelHeight - 87) / font.lineHeight);
    scroll = Math.min(scroll, Math.max(0, lines.size() - count));
    int end = lines.size() - scroll, start = Math.max(0, end - count), y = top + 40;
    for (int i = start; i < end; i++) {
      g.drawString(font, lines.get(i), left + 12, y, 0xffa2f4bc, false);
      y += font.lineHeight;
    }
    if (waiting)
      g.drawString(font, "Waiting for device...", left + 12, top + panelHeight - 44, 0xffe5c56a, false);
    if (!waiting && completions.size() > 1) {
      String options = "Tab / Shift+Tab: " + String.join(" | ", completions);
      g.drawString(font, font.plainSubstrByWidth(options, panelWidth - 24), left + 12, top + panelHeight - 44,
          0xff74d8ef, false);
    }
    super.render(g, mx, my, dt);
  }
}
