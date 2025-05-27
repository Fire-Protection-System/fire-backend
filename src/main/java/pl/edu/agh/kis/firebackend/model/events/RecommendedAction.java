package pl.edu.agh.kis.firebackend.model.events;

import pl.edu.agh.kis.firebackend.model.primitives.Location;

public record RecommendedAction(
    int unitId,
    int sectorId
) { }
