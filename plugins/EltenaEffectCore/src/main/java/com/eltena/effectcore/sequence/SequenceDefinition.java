package com.eltena.effectcore.sequence;

import java.util.List;

public record SequenceDefinition(
    String id,
    String displayName,
    List<SequenceStep> steps
) {
}
