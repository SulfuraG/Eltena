package com.eltena.core.application.world;

import com.eltena.core.domain.world.WorldUniqueRecord;

import java.util.List;

public record WorldUniqueGrantResult(
    boolean granted,
    WorldUniqueRecord record,
    List<String> messages
) {
}
