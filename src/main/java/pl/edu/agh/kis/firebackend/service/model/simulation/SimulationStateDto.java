package pl.edu.agh.kis.firebackend.service.model.simulation;

import pl.edu.agh.kis.firebackend.model.events.EvRecommendation;
import pl.edu.agh.kis.firebackend.model.events.RecommendedAction;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public record SimulationStateDto (
    String forestName,
    Date timestamp,
    long tick,
    List<Sector> sectors,
    /**
     * Lista brygad straży pożarnej (BEZ pola location, pozycje tylko przez agent_position!)
     * Deprecated: location nie jest już wysyłane w pełnym stanie.
     */
    List<FireBrigade> fireBrigades,
    /**
     * Lista patroli leśnych (BEZ pola location, pozycje tylko przez agent_position!)
     * Deprecated: location nie jest już wysyłane w pełnym stanie.
     */
    List<ForesterPatrol> foresterPatrols,
    List<RecommendedAction> recommendedActions
){
    public static SimulationStateDto from(SimulationState state) {
        // Usuwamy location z fireBrigades i foresterPatrols w pełnym stanie (tylko agent_position!)
        List<FireBrigade> fireBrigadesNoLocation = state.fireBrigades.values().stream()
            .map(fb -> fb.withoutLocation())
            .toList();
        List<ForesterPatrol> foresterPatrolsNoLocation = state.foresterPatrols.values().stream()
            .map(fp -> fp.withoutLocation())
            .toList();
        return new SimulationStateDto(
            state.forestName,
            state.timestamp,
            state.tick,
            new ArrayList<>(state.sectors.values()),
            fireBrigadesNoLocation,
            foresterPatrolsNoLocation,
            new ArrayList<>(state.recommendedActions.values())
        );
    }

//    public static SimulationState fromConfiguration(Configuration configuration) {
//        return new SimulationState(
//                configuration.forestName(),
//                new Date(),
//                configuration.sectors().stream().map(Sector::fromConfig).toList()
//        );
//    }
}
