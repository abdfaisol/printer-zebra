package com.example.printprasi.printprasi.ui;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class LogBridge {
    private static final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public static void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    public static void log(String message) {
        System.out.println("[Print Wareify] " + message);
        com.example.printprasi.printprasi.helpers.DailyLogFileManager.appendLog(message);
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(message);
            } catch (Exception ignored) {}
        }
    }
}
