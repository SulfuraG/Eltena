package com.eltena.core.bootstrap;

public interface BootstrapModule {

    String name();

    default void load(ServiceRegistry services) {
    }

    default void enable(ServiceRegistry services) {
    }

    default void disable(ServiceRegistry services) {
    }
}
