package uk.co.iceconchy.aerowarptics.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Remembers the last thing said about each subject, so a trace can speak only when it changes.
 *
 * <p>Some of what is worth tracing is asked over and over: a chart refreshes its quotes every few
 * seconds, a console re-checks who may use it on every packet. Logged every time, the one line that
 * matters - the moment a player's access or an anchor's price changed - would be buried under a
 * hundred identical ones. Logged only on change, it stands out.
 *
 * <p>Bounded, least recently used first out, because the subjects are players and machines and a
 * long-running server meets an unbounded number of both. Forgetting one costs nothing worse than
 * saying its current state once more.
 *
 * <p>Free of Minecraft, so it is tested directly.
 */
public final class TraceMemo {

    private final Map<String, String> said;

    /** @param capacity how many subjects to remember before the least recently touched is dropped */
    public TraceMemo(int capacity) {
        int cap = Math.max(1, capacity);
        this.said = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > cap;
            }
        };
    }

    /**
     * Records what is now true of {@code subject}.
     *
     * @return whether it differs from what was last recorded for it - including the first time
     */
    public synchronized boolean changed(String subject, String now) {
        return !Objects.equals(said.put(subject, now), now);
    }

    /** Forgets everything, so the next report on any subject is heard in full. */
    public synchronized void clear() {
        said.clear();
    }

    public synchronized int size() {
        return said.size();
    }
}
