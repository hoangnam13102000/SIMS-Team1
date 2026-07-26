package com.event;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Event bus dung chung toan app (Observer Pattern), dinh danh su kien bang
 * CHINH CLASS cua payload thay vi enum - nen KHONG can sua file nay khi doi
 * sang domain khac (HR, ngan hang...). Moi domain chi can tu dinh nghia cac
 * class su kien rieng (vd: NewOrderEvent, StockChangedEvent) roi publish/
 * subscribe theo class do.
 *
 * An toan luong: publish() luon dua callback ve Swing EDT.
 */
public final class AppEventBus {

    private static final AppEventBus INSTANCE = new AppEventBus();

    public static AppEventBus getInstance() {
        return INSTANCE;
    }

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    private AppEventBus() {}

    /** Dang ky lang nghe 1 loai su kien (xac dinh boi class cua no). */
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /** Huy dang ky. Chi can goi khi component THAT SU bi huy/dong. */
    public <T> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<?>> list = listeners.get(eventType);
        if (list != null) list.remove(listener);
    }

    /** Phat 1 su kien toi tat ca listener dang dang ky cho class cua no. */
    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        if (event == null) return;
        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) return;
        for (Consumer<?> raw : list) {
            Consumer<T> typed = (Consumer<T>) raw;
            SwingUtilities.invokeLater(() -> typed.accept(event));
        }
    }
}