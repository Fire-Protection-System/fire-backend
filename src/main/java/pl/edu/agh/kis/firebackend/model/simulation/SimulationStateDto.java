package pl.edu.agh.kis.firebackend.model.simulation;

import pl.edu.agh.kis.firebackend.model.events.EvRecommendation;
import pl.edu.agh.kis.firebackend.model.events.RecommendedAction;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public record SimulationStateDto (
    String forestName,
    Date timestamp,
    List<Sector> sectors,
    List<FireBrigade> fireBrigades,
    List<ForesterPatrol> foresterPatrols, 
    List<RecommendedAction> recommendedActions
){
    public static SimulationStateDto from(SimulationState state) {
        return new SimulationStateDto(
                state.forestName,
                state.timestamp,
                new ArrayList<>(state.sectors.values()),
                new ArrayList<>(state.fireBrigades.values()),
                new ArrayList<>(state.foresterPatrols.values()), 
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
