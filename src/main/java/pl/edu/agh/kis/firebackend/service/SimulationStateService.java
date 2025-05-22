package pl.edu.agh.kis.firebackend.service;

import java.time.Duration;
import java.util.Optional;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import pl.edu.agh.kis.firebackend.model.configuration.Configuration;
import pl.edu.agh.kis.firebackend.model.events.EvCO2Sensor;
import pl.edu.agh.kis.firebackend.model.events.EvCamera;
import pl.edu.agh.kis.firebackend.model.events.EvFireBrigade;
import pl.edu.agh.kis.firebackend.model.events.EvForestPatrol;
import pl.edu.agh.kis.firebackend.model.events.EvLitterMoistureSensor;
import pl.edu.agh.kis.firebackend.model.events.EvPM25ConcentrationSensor;
import pl.edu.agh.kis.firebackend.model.events.EvRecommendation;
import pl.edu.agh.kis.firebackend.model.events.RecommendedAction;
import pl.edu.agh.kis.firebackend.model.events.EvTempAndAirHumiditySensor;
import pl.edu.agh.kis.firebackend.model.events.EvWindDirectionSensor;
import pl.edu.agh.kis.firebackend.model.events.EvWindSpeedSensor;
import pl.edu.agh.kis.firebackend.model.simulation.FireBrigade;
import pl.edu.agh.kis.firebackend.model.simulation.ForesterPatrol;
import pl.edu.agh.kis.firebackend.model.simulation.SimulationState;
import pl.edu.agh.kis.firebackend.model.simulation.SimulationStateDto;
import pl.edu.agh.kis.firebackend.util.SectorIdResolver;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
@AllArgsConstructor
public class SimulationStateService {
    private Flux<EvFireBrigade> fireBrigadeFlux;
    private Flux<EvForestPatrol> foresterPatrolFlux;
    private Flux<EvWindSpeedSensor> windSpeedSensorFlux;
    private Flux<EvTempAndAirHumiditySensor> tempAndAirHumiditySensorFlux;
    private Flux<EvWindDirectionSensor> windDirectionSensorFlux;
    private Flux<EvLitterMoistureSensor> litterMoistureSensorFlux;
    private Flux<EvCO2Sensor> co2SensorFlux;
    private Flux<EvPM25ConcentrationSensor> pm25ConcentrationSensorFlux;
    private Flux<EvCamera> cameraFlux;
    private Flux<EvRecommendation> recommendationFlux;

    private static final Logger log = LoggerFactory.getLogger(SimulationStateService.class);

    public Flux<SimulationStateDto> runSimulation(Configuration configuration, Duration interval) {
        log.info("Starting simulation with interval: {} seconds", interval.getSeconds());
        log.info("Configuration: {}", configuration);
        
        SimulationState state = SimulationState.from(configuration);
        log.info("Initial state created with {} sectors", state.sectors.size());

        fireBrigadeFlux.subscribeOn(Schedulers.parallel())            
            .subscribe(fireBrigade -> {
                Integer key = fireBrigade.fireBrigadeId();                
                log.info("Fire brigade event received - ID: {}, Location: {}", key, fireBrigade.location());
                
                synchronized (state) {
                    state.fireBrigades.put(key, FireBrigade.from(fireBrigade));
                    log.debug("Fire brigade state updated - ID: {}", key);
                }
        });

        foresterPatrolFlux.subscribeOn(Schedulers.parallel())
            .subscribe(foresterPatrol -> {
                Integer key = foresterPatrol.foresterPatrolId();
                log.info("Forester patrol event received - ID: {}, Location: {}", key, foresterPatrol.location());

                synchronized (state) {
                    state.foresterPatrols.put(key, ForesterPatrol.from(foresterPatrol));
                    log.debug("Forester patrol state updated - ID: {}", key);
                }
        });

        windSpeedSensorFlux.subscribeOn(Schedulers.parallel())
            .subscribe(windSpeedSensor -> {
                log.debug("Wind speed sensor event received at location: {}", windSpeedSensor.location());
                
                Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(state.sectors.values().stream().toList(), windSpeedSensor.location());
                if (sectorIdOptional.isEmpty()) {
                    log.warn("Sector at location {} not found for wind speed sensor!", windSpeedSensor.location());
                    return;
                }
                Integer sectorId = sectorIdOptional.get();
                synchronized (state) {
                    state.sectors.get(sectorId).state.windSpeed = windSpeedSensor.data().windSpeed();
                    log.debug("Wind speed updated for sector {}: {} m/s", sectorId, windSpeedSensor.data().windSpeed());
                }
        });

        tempAndAirHumiditySensorFlux.subscribeOn(Schedulers.parallel())
            .subscribe(tempAndAirHumiditySensor -> {
                log.debug("Temperature and humidity sensor event received at location: {}", tempAndAirHumiditySensor.location());
                
                Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(state.sectors.values().stream().toList(), tempAndAirHumiditySensor.location());
                if (sectorIdOptional.isEmpty()) {
                    log.warn("Sector at location {} not found for temperature and humidity sensor!", tempAndAirHumiditySensor.location());
                    return;
                }
                Integer sectorId = sectorIdOptional.get();
                synchronized (state) {
                    state.sectors.get(sectorId).state.temperature = tempAndAirHumiditySensor.data().temperature();
                    state.sectors.get(sectorId).state.airHumidity = tempAndAirHumiditySensor.data().airHumidity();
                    log.debug("Temperature updated for sector {}: {}°C", sectorId, tempAndAirHumiditySensor.data().temperature());
                    log.debug("Air humidity updated for sector {}: {}%", sectorId, tempAndAirHumiditySensor.data().airHumidity());
                }
        });

        windDirectionSensorFlux.subscribeOn(Schedulers.parallel())
            .subscribe(windDirectionSensor -> {
                log.debug("Wind direction sensor event received at location: {}", windDirectionSensor.location());
                
                Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(state.sectors.values().stream().toList(), windDirectionSensor.location());
                if (sectorIdOptional.isEmpty()) {
                    log.warn("Sector at location {} not found for wind direction sensor!", windDirectionSensor.location());
                    return;
                }
                Integer sectorId = sectorIdOptional.get();
                synchronized (state) {
                    state.sectors.get(sectorId).state.windDirection = windDirectionSensor.data().windDirection();
                    log.debug("Wind direction updated for sector {}: {} degrees", sectorId, windDirectionSensor.data().windDirection());
                }
        });

        litterMoistureSensorFlux.subscribeOn(Schedulers.parallel())
            .subscribe(litterMoistureSensor -> {
                log.debug("Litter moisture sensor event received at location: {}", litterMoistureSensor.location());
                
                Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(state.sectors.values().stream().toList(), litterMoistureSensor.location());
                if (sectorIdOptional.isEmpty()) {
                    log.warn("Sector at location {} not found for litter moisture sensor!", litterMoistureSensor.location());
                    return;
                }
                Integer sectorId = sectorIdOptional.get();
                synchronized (state) {
                    state.sectors.get(sectorId).state.plantLitterMoisture = litterMoistureSensor.data().litterMoisture();
                    log.debug("Plant litter moisture updated for sector {}: {}%", sectorId, litterMoistureSensor.data().litterMoisture());
                }
        });

        co2SensorFlux.subscribeOn(Schedulers.parallel())
                .subscribe(co2Sensor -> {
                    log.debug("CO2 sensor event received at location: {}", co2Sensor.location());
                    
                    Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(state.sectors.values().stream().toList(), co2Sensor.location());
                    if (sectorIdOptional.isEmpty()) {
                        log.warn("Sector at location {} not found for CO2 sensor!", co2Sensor.location());
                        return;
                    }
                    Integer sectorId = sectorIdOptional.get();
                    synchronized (state) {
                        state.sectors.get(sectorId).state.co2Concentration = co2Sensor.data().co2Concentration();
                        log.debug("CO2 concentration updated for sector {}: {} ppm", sectorId, co2Sensor.data().co2Concentration());
                    }
        });

        pm25ConcentrationSensorFlux.subscribeOn(Schedulers.parallel())
                .subscribe(pm25ConcentrationSensor -> {
                    log.debug("PM2.5 concentration sensor event received at location: {}", pm25ConcentrationSensor.location());
                    
                    Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(state.sectors.values().stream().toList(), pm25ConcentrationSensor.location());
                    if (sectorIdOptional.isEmpty()) {
                        log.warn("Sector at location {} not found for PM2.5 concentration sensor!", pm25ConcentrationSensor.location());
                        return;
                    }
                    Integer sectorId = sectorIdOptional.get();
                    synchronized (state) {
                        state.sectors.get(sectorId).state.pm2_5Concentration = pm25ConcentrationSensor.data().pm2_5Concentration();
                        log.debug("PM2.5 concentration updated for sector {}: {} μg/m³", sectorId, pm25ConcentrationSensor.data().pm2_5Concentration());
                    }
        });

        cameraFlux.subscribeOn(Schedulers.parallel())
                .subscribe(camera -> {
                    log.debug("Camera event received at location: {}", camera.location());
                    
                    Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(state.sectors.values().stream().toList(), camera.location());
                    if (sectorIdOptional.isEmpty()) {
                        log.warn("Sector at location {} not found for camera!", camera.location());
                        return;
                    }
                    Integer sectorId = sectorIdOptional.get();
                    synchronized (state) {
                        // TODO: Handle camera data in some way
                        log.debug("Camera data received for sector {}", sectorId);
                    }
        });

        recommendationFlux.subscribeOn(Schedulers.parallel())
                .subscribe(recommendation -> {
                    log.debug("New recommendation received at timestamp: {}", recommendation.timestamp());
                    
                    List<RecommendedAction> actions = recommendation.recommendedActions();
                    if (actions == null) {
                        log.warn("Received recommendation with null recommendedActions list at timestamp: {}", recommendation.timestamp());
                        return; // or continue, depending on context
                    }
        
                    // Integer sectorId = sectorIdOptional.get();
                    for (RecommendedAction action : actions) {
                        synchronized (state) {
                            state.recommendedActions.put(action.unitId(), action);
                        }
                    }            
                });

        log.info("All sensor subscriptions established, starting interval-based state emission");
        
        return Flux.interval(interval)
            .map(tick -> {
                synchronized(state) {
                    SimulationStateDto dto = SimulationStateDto.from(state);
                    return dto;
                }
            });
    }
}
