package pl.edu.agh.kis.firebackend.configuration;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.edu.agh.kis.firebackend.model.UpdatesQueue;
import pl.edu.agh.kis.firebackend.model.events.*;
import pl.edu.agh.kis.firebackend.service.StateUpdatesService;
import reactor.core.publisher.Flux;

@Configuration
@AllArgsConstructor
public class DeclaredQueues
{
    private final StateUpdatesService stateUpdatesService;

    private <T> Flux<T> createFlux(String queueName, Class<T> clazz)
    {
        return stateUpdatesService.createUpdatesFlux(new UpdatesQueue<>(queueName, clazz));
    }

    @Bean
    public Flux<EvFireBrigade> fireBrigadeUpdates()
    {
        return createFlux("Fire brigades state queue", EvFireBrigade.class);
    }

    @Bean
    public Flux<EvForestPatrol> foresterPatrolUpdates()
    {
        return createFlux("Forester patrol state queue", EvForestPatrol.class);
    }

    @Bean
    public Flux<EvWindSpeedSensor> windSpeedSensorUpdates()
    {
        return createFlux("Wind speed queue", EvWindSpeedSensor.class);
    }

    @Bean
    public Flux<EvTempAndAirHumiditySensor> tempAndAirHumiditySensorUpdates()
    {
        return createFlux("Temp and air humidity queue", EvTempAndAirHumiditySensor.class);
    }

    @Bean
    public Flux<EvWindDirectionSensor> windDirectionSensorUpdates()
    {
        return createFlux("Wind direction queue", EvWindDirectionSensor.class);
    }

    @Bean
    public Flux<EvLitterMoistureSensor> litterMoistureSensorUpdates()
    {
        return createFlux("Litter moisture queue", EvLitterMoistureSensor.class);
    }

    @Bean
    public Flux<EvCO2Sensor> co2SensorUpdates()
    {
        return createFlux("CO2 queue", EvCO2Sensor.class);
    }

    @Bean
    public Flux<EvPM25ConcentrationSensor> pm25ConcentrationSensorUpdates()
    {
        return createFlux("PM2.5 queue", EvPM25ConcentrationSensor.class);
    }

    @Bean
    public Flux<EvCamera> cameraUpdates()
    {
        return createFlux("Camera queue", EvCamera.class);
    }

    @Bean
    public Flux<EvRecommendation> simulationRecommendations()
    {
        return createFlux("Recommended action queue", EvRecommendation.class);
    }

    @Bean
    public Flux<EvSectorState> simulationState()
    {
        return createFlux("Sector state queue", EvSectorState.class);
    }
}
