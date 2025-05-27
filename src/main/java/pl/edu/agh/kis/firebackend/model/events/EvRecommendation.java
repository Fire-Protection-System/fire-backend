package pl.edu.agh.kis.firebackend.model.events;

import java.util.Date;
import java.util.List; 

import pl.edu.agh.kis.firebackend.model.events.RecommendedAction;

public record EvRecommendation(
    float timestamp,
    List<RecommendedAction> recommendedActions,
    String priority
) { }