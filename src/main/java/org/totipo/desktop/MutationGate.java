package org.totipo.desktop;

import org.totipo.desktop.ui.Edt;
import java.util.function.Consumer;

/** One EDT-owned mutating workflow per vault window, including capability decisions. */
final class MutationGate {
    private final Consumer<Boolean> availability;
    private Object owner;
    private boolean closing;

    MutationGate(Consumer<Boolean> availability) { this.availability = availability; }

    boolean acquire(Object candidate) {
        Edt.require();
        if (closing || owner != null) { return false; }
        owner = candidate;
        availability.accept(false);
        return true;
    }

    void release(Object candidate) {
        Edt.require();
        if (owner == candidate) {
            owner = null;
            availability.accept(!closing);
        }
    }

    void closing() {
        Edt.require();
        closing = true;
        availability.accept(false);
    }
}
