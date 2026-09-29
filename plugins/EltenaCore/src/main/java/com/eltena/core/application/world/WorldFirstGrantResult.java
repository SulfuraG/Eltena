package com.eltena.core.application.world;

import com.eltena.core.domain.world.WorldFirstRecord;

import java.util.List;

public record WorldFirstGrantResult(
    boolean granted,
    WorldFirstRecord record,
    List<String> messages
) {
}
