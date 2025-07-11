package pl.edu.agh.kis.firebackend.configuration;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.edu.agh.kis.firebackend.model.UpdatesQueue;
import pl.edu.agh.kis.firebackend.model.events.*;
import pl.edu.agh.kis.firebackend.service.StateUpdatesService;
import reactor.core.publisher.Flux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@AllArgsConstructor
public class DeclaredQueues {
    private final StateUpdatesService stateUpdatesService;

    @Bean
    Flux<EvFireBrigade> fireBrigadeUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Fire brigades state queue", EvFireBrigade.class));
    }

    @Bean
    Flux<EvForestPatrol> foresterPatrolUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Forester patrol state queue", EvForestPatrol.class));
    }

    @Bean
    Flux<EvWindSpeedSensor> windSpeedSensorUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Wind speed queue", EvWindSpeedSensor.class));
    }

    @Bean
    Flux<EvTempAndAirHumiditySensor> tempAndAirHumiditySensorUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Temp and air humidity queue", EvTempAndAirHumiditySensor.class));
    }

    @Bean
    Flux<EvWindDirectionSensor> windDirectionSensorUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Wind direction queue", EvWindDirectionSensor.class));
    }

    @Bean
    Flux<EvLitterMoistureSensor> litterMoistureSensorUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Litter moisture queue", EvLitterMoistureSensor.class));
    }

    @Bean
    Flux<EvCO2Sensor> co2SensorUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("CO2 queue", EvCO2Sensor.class));
    }

    @Bean
    Flux<EvPM25ConcentrationSensor> pm25ConcentrationSensorUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("PM2.5 queue", EvPM25ConcentrationSensor.class));
    }

    @Bean
    Flux<EvCamera> cameraUpdates() {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Camera queue", EvCamera.class));
    }

    @Bean
    Flux<EvRecommendation> simulationRecommendations(){
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Recommended action queue", EvRecommendation.class));
    }

    @Bean
    Flux<EvSectorState> simulationState(){
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>("Sector state queue", EvSectorState.class));
    }


}
