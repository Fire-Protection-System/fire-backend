package pl.edu.agh.kis.firebackend.service;

import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Qualifier;
import pl.edu.agh.kis.firebackend.configuration.QueueNames;
import pl.edu.agh.kis.firebackend.model.configuration.Configuration;
import pl.edu.agh.kis.firebackend.model.events.EvCO2Sensor;
import pl.edu.agh.kis.firebackend.model.events.EvCamera;
import pl.edu.agh.kis.firebackend.model.events.EvFireBrigade;
import pl.edu.agh.kis.firebackend.model.events.EvForestPatrol;
import pl.edu.agh.kis.firebackend.model.events.EvLitterMoistureSensor;
import pl.edu.agh.kis.firebackend.model.events.EvPM25ConcentrationSensor;
import pl.edu.agh.kis.firebackend.model.events.EvRecommendation;
import pl.edu.agh.kis.firebackend.model.events.EvSectorState;
import pl.edu.agh.kis.firebackend.model.events.RecommendedAction;
import pl.edu.agh.kis.firebackend.model.events.EvTempAndAirHumiditySensor;
import pl.edu.agh.kis.firebackend.model.events.EvWindDirectionSensor;
import pl.edu.agh.kis.firebackend.model.events.EvWindSpeedSensor;
import pl.edu.agh.kis.firebackend.model.simulation.FireBrigade;
import pl.edu.agh.kis.firebackend.model.simulation.ForesterPatrol;
import pl.edu.agh.kis.firebackend.model.simulation.Sector;
import pl.edu.agh.kis.firebackend.model.simulation.SimulationState;
import pl.edu.agh.kis.firebackend.model.simulation.SimulationStateDto;
import pl.edu.agh.kis.firebackend.model.SectorState;
import pl.edu.agh.kis.firebackend.util.SectorIdResolver;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
public class SimulationStateService 
{
    private StateUpdatesService stateUpdatesService;
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
    private Flux<EvSectorState> sectorStateFlux;
    
    public SimulationStateService(
        StateUpdatesService stateUpdatesService,
        Flux<EvFireBrigade> fireBrigadeFlux,
        Flux<EvForestPatrol> foresterPatrolFlux,
        Flux<EvWindSpeedSensor> windSpeedSensorFlux,
        Flux<EvTempAndAirHumiditySensor> tempAndAirHumiditySensorFlux,
        Flux<EvWindDirectionSensor> windDirectionSensorFlux,
        Flux<EvLitterMoistureSensor> litterMoistureSensorFlux,
        Flux<EvCO2Sensor> co2SensorFlux,
        Flux<EvPM25ConcentrationSensor> pm25ConcentrationSensorFlux,
        Flux<EvCamera> cameraFlux,
        Flux<EvRecommendation> recommendationFlux,
        @Qualifier("simulationState") Flux<EvSectorState> sectorStateFlux
    ) {
        this.stateUpdatesService = stateUpdatesService;
        this.fireBrigadeFlux = fireBrigadeFlux;
        this.foresterPatrolFlux = foresterPatrolFlux;
        this.windSpeedSensorFlux = windSpeedSensorFlux;
        this.tempAndAirHumiditySensorFlux = tempAndAirHumiditySensorFlux;
        this.windDirectionSensorFlux = windDirectionSensorFlux;
        this.litterMoistureSensorFlux = litterMoistureSensorFlux;
        this.co2SensorFlux = co2SensorFlux;
        this.pm25ConcentrationSensorFlux = pm25ConcentrationSensorFlux;
        this.cameraFlux = cameraFlux;
        this.recommendationFlux = recommendationFlux;
        this.sectorStateFlux = sectorStateFlux;
    }

    private static final Logger log = LoggerFactory.getLogger(SimulationStateService.class);
    
    private volatile SimulationState currentState = null;
    
    // Track last sent state for delta calculation
    private volatile SimulationState lastSentState = null;
    
    private boolean sectorsHaveContours() {
        return !currentState.sectors.isEmpty() && 
               currentState.sectors.values().stream().anyMatch(s -> !s.contours.isEmpty());
    }
    
    @PostConstruct
    public void initializeMessageConsumers() 
    {
        try {
            String logFile = System.getenv().getOrDefault("LOG_FILE", "logs/application.logs");
            java.nio.file.Path logPath = java.nio.file.Paths.get(logFile).getParent();
            if (logPath != null) {
                java.nio.file.Files.createDirectories(logPath);
            }
        } catch (Exception e) {
            log.warn("Failed to ensure log directory exists: {}", e.toString());
        }

        currentState = new SimulationState();
        subscribeToQueues();
        log.info("RabbitMQ consumers ready");
    }
    
    private void subscribeToQueues() 
    {
        fireBrigadeFlux.subscribeOn(Schedulers.parallel())            
            .subscribe(fireBrigade -> 
            {
                Integer key = fireBrigade.fireBrigadeId();
                
                synchronized (currentState) 
                {
                    Integer sectorId = fireBrigade.sectorId();
                    if (sectorId == null || sectorId == 0) {
                        if (sectorsHaveContours()) {
                            Optional<Integer> resolvedSectorId = SectorIdResolver.resolveSectorId(
                                currentState.sectors.values().stream().toList(), 
                                fireBrigade.location()
                            );
                            if (resolvedSectorId.isPresent()) {
                                sectorId = resolvedSectorId.get();
                            } else {
                                log.warn("Could not resolve sectorId for fire brigade {} at location {}", key, fireBrigade.location());
                                sectorId = 0;
                            }
                        } else {
                            sectorId = 0;
                        }
                    }
                    
                    var brigade = new FireBrigade(
                        fireBrigade.fireBrigadeId(),
                        sectorId,
                        fireBrigade.location(),
                        fireBrigade.state(),
                        pl.edu.agh.kis.firebackend.model.FireBrigadeAction.EXTINGUISH
                    );
                    currentState.fireBrigades.put(key, brigade);
                }
            });

        foresterPatrolFlux.subscribeOn(Schedulers.parallel())
            .subscribe(foresterPatrol -> 
            {
                Integer key = foresterPatrol.foresterPatrolId();
                
                synchronized (currentState)
                {
                    currentState.foresterPatrols.put(key, ForesterPatrol.from(foresterPatrol));
                }
            });

        windSpeedSensorFlux
            .subscribeOn(Schedulers.parallel())
            .subscribe(windSpeedSensor -> 
            {
                if (!sectorsHaveContours()) {
                    return;
                }

                Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(currentState.sectors.values().stream().toList(), windSpeedSensor.location());
                if (sectorIdOptional.isEmpty()) 
                {
                    log.warn("Sector at location {} not found for wind speed sensor!", windSpeedSensor.location());
                    return;
                }

                Integer sectorId = sectorIdOptional.get();
                var windSpeed = windSpeedSensor.data().windSpeed();

                synchronized (currentState) 
                {
                    var sector = currentState.sectors.get(sectorId);
                    if (sector == null) {
                        log.error("Resource not found: Sector {} does not exist when updating wind speed sensor at location {}", 
                            sectorId, windSpeedSensor.location());
                        return;
                    }
                    sector.state.windSpeed = windSpeed;
                }
            });

        tempAndAirHumiditySensorFlux.subscribeOn(Schedulers.parallel())
            .subscribe(tempAndAirHumiditySensor -> {
                if (!sectorsHaveContours()) {
                    return;
                }
                
                Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(currentState.sectors.values().stream().toList(), tempAndAirHumiditySensor.location());
                if (sectorIdOptional.isEmpty()) {
                    log.warn("Sector at location {} not found for temperature and humidity sensor!", tempAndAirHumiditySensor.location());
                    return;
                }
                Integer sectorId = sectorIdOptional.get();
                synchronized (currentState) {
                    var sector = currentState.sectors.get(sectorId);
                    if (sector == null) {
                        log.error("Resource not found: Sector {} does not exist when updating temperature/humidity sensor at location {}", 
                            sectorId, tempAndAirHumiditySensor.location());
                        return;
                    }
                    sector.state.temperature = tempAndAirHumiditySensor.data().temperature();
                    sector.state.airHumidity = tempAndAirHumiditySensor.data().airHumidity();
                }
            });

        windDirectionSensorFlux.subscribeOn(Schedulers.parallel())
            .subscribe(windDirectionSensor -> {
                if (!sectorsHaveContours()) {
                    return;
                }
                
                Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(currentState.sectors.values().stream().toList(), windDirectionSensor.location());
                if (sectorIdOptional.isEmpty()) {
                    log.warn("Sector at location {} not found for wind direction sensor!", windDirectionSensor.location());
                    return;
                }
                Integer sectorId = sectorIdOptional.get();
                synchronized (currentState) {
                    var sector = currentState.sectors.get(sectorId);
                    if (sector == null) {
                        log.error("Resource not found: Sector {} does not exist when updating wind direction sensor at location {}", 
                            sectorId, windDirectionSensor.location());
                        return;
                    }
                    sector.state.windDirection = windDirectionSensor.data().windDirection();
                }
            });

        litterMoistureSensorFlux.subscribeOn(Schedulers.parallel())
            .subscribe(litterMoistureSensor -> {
                if (!sectorsHaveContours()) {
                    return;
                }
                
                Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(currentState.sectors.values().stream().toList(), litterMoistureSensor.location());
                if (sectorIdOptional.isEmpty()) {
                    log.warn("Sector at location {} not found for litter moisture sensor!", litterMoistureSensor.location());
                    return;
                }
                Integer sectorId = sectorIdOptional.get();
                synchronized (currentState) {
                    var sector = currentState.sectors.get(sectorId);
                    if (sector == null) {
                        log.error("Resource not found: Sector {} does not exist when updating litter moisture sensor at location {}", 
                            sectorId, litterMoistureSensor.location());
                        return;
                    }
                    sector.state.plantLitterMoisture = litterMoistureSensor.data().litterMoisture();
                }
            });

        co2SensorFlux.subscribeOn(Schedulers.parallel())
                .subscribe(co2Sensor -> {
                    if (!sectorsHaveContours()) {
                        return;
                    }
                    
                    Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(currentState.sectors.values().stream().toList(), co2Sensor.location());
                    if (sectorIdOptional.isEmpty()) {
                        log.warn("Sector at location {} not found for CO2 sensor!", co2Sensor.location());
                        return;
                    }
                    Integer sectorId = sectorIdOptional.get();
                    synchronized (currentState) {
                        var sector = currentState.sectors.get(sectorId);
                        if (sector == null) {
                            log.error("Resource not found: Sector {} does not exist when updating CO2 sensor at location {}", 
                                sectorId, co2Sensor.location());
                            return;
                        }
                        sector.state.co2Concentration = co2Sensor.data().co2Concentration();
                    }
                });

        pm25ConcentrationSensorFlux.subscribeOn(Schedulers.parallel())
                .subscribe(pm25ConcentrationSensor -> {
                    if (!sectorsHaveContours()) {
                        return;
                    }
                    
                    Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(currentState.sectors.values().stream().toList(), pm25ConcentrationSensor.location());
                    if (sectorIdOptional.isEmpty()) {
                        log.warn("Sector at location {} not found for PM2.5 concentration sensor!", pm25ConcentrationSensor.location());
                        return;
                    }
                    Integer sectorId = sectorIdOptional.get();
                    synchronized (currentState) {
                        var sector = currentState.sectors.get(sectorId);
                        if (sector == null) {
                            log.error("Resource not found: Sector {} does not exist when updating PM2.5 sensor at location {}", 
                                sectorId, pm25ConcentrationSensor.location());
                            return;
                        }
                        sector.state.pm2_5Concentration = pm25ConcentrationSensor.data().pm2_5Concentration();
                    }
                });

        cameraFlux.subscribeOn(Schedulers.parallel())
                .subscribe(camera -> {
                    if (!sectorsHaveContours()) {
                        return;
                    }
                    
                    Optional<Integer> sectorIdOptional = SectorIdResolver.resolveSectorId(currentState.sectors.values().stream().toList(), camera.location());
                    if (sectorIdOptional.isEmpty()) {
                        log.warn("Sector at location {} not found for camera!", camera.location());
                        return;
                    }
                    Integer sectorId = sectorIdOptional.get();
                    synchronized (currentState) {
                        var sector = currentState.sectors.get(sectorId);
                        if (sector == null) {
                            log.error("Resource not found: Sector {} does not exist when processing camera data at location {}", 
                                sectorId, camera.location());
                            return;
                        }
                        // TODO: Handle camera data in some way
                    }
                });

        recommendationFlux.subscribeOn(Schedulers.parallel())
                .subscribe(recommendation -> {
                    List<RecommendedAction> actions = recommendation.recommendedActions();
                    if (actions == null) {
                        log.warn("Received recommendation with null recommendedActions list at timestamp: {}", recommendation.timestamp());
                        return;
                    }
        
                    for (RecommendedAction action : actions) {
                        // Use composite key (unitType + unitId) to avoid conflicts between fire brigades and forester patrols
                        // This is critical because both can have the same numeric IDs (e.g., both have ID 0, 1, 2...)
                        String unitType = action.unitType();
                        if (unitType == null || unitType.isEmpty()) {
                            log.error("Received recommendation without unitType for unitId={}, sectorId={}. Skipping to avoid misrouting.", 
                                action.unitId(), action.sectorId());
                            continue;
                        }
                        
                        // Validate that the referenced unit exists in current state to avoid sending commands for missing agents
                        boolean exists;
                        synchronized (currentState) {
                            if ("fireBrigade".equals(unitType)) {
                                exists = currentState.fireBrigades.containsKey(action.unitId());
                            } else if ("foresterPatrol".equals(unitType)) {
                                exists = currentState.foresterPatrols.containsKey(action.unitId());
                            } else {
                                exists = false;
                            }
                        }
                        if (!exists) {
                            log.warn("Dropping recommendation for unknown unitType={} unitId={} (sectorId={})", 
                                unitType, action.unitId(), action.sectorId());
                            continue;
                        }
                        
                        // Create composite key to avoid ID conflicts
                        String compositeKey = unitType + ":" + action.unitId();
                        
                        synchronized (currentState) {
                            currentState.recommendedActions.put(compositeKey, action);
                        }
                    }            
                });

        sectorStateFlux.subscribeOn(Schedulers.parallel())
                .subscribe(sectorState -> {
                    Integer sectorId = sectorState.sectorId();
                    synchronized (currentState) {
                        var sector = currentState.sectors.get(sectorId);
                        if (sector == null) {
                            SectorState state = new SectorState(
                                new java.util.Date(),
                                0.0, 0.0, null, 0.0, 0.0, 0.0, 0.0,
                                null, null,
                                sectorState.fireLevel(),
                                sectorState.burnLevel(),
                                sectorState.extinguishLevel()
                            );
                            // Create sector with empty contours and no assigned brigades
                            sector = new Sector(sectorId, state, new ArrayList<List<Double>>(), new ArrayList<Integer>());
                            currentState.sectors.put(sectorId, sector);
                        } else {
                            // Update existing sector
                            sector.state.fireLevel = sectorState.fireLevel();
                            sector.state.burnLevel = sectorState.burnLevel();
                            sector.state.extinguishLevel = sectorState.extinguishLevel();
                        }
                    }
                });
    }
    
    public Flux<SimulationStateDto> runSimulation(Configuration configuration, Duration interval) 
    {
        log.info("Starting simulation - interval: {}s, sectors: {}, brigades: {}, patrols: {}", 
            interval.getSeconds(), configuration.sectors().size(), 
            configuration.fireBrigades().size(), configuration.foresterPatrols().size());
        
        synchronized(currentState) {
            var configState = SimulationState.from(configuration);
            currentState.forestName = configState.forestName;
            
            for (var entry : configState.sectors.entrySet()) {
                var existingSector = currentState.sectors.get(entry.getKey());
                if (existingSector == null) {
                    currentState.sectors.put(entry.getKey(), entry.getValue());
                } else {
                    if (entry.getValue().contours != null && !entry.getValue().contours.isEmpty()) {
                        existingSector.contours = entry.getValue().contours;
                    }
                }
            }
            
            if (currentState.fireBrigades.isEmpty()) {
                currentState.fireBrigades = configState.fireBrigades;
            }
            
            if (currentState.foresterPatrols.isEmpty()) {
                currentState.foresterPatrols = configState.foresterPatrols;
            }
            
            log.info("State ready - sectors: {}, brigades: {}, patrols: {}", 
                currentState.sectors.size(), currentState.fireBrigades.size(), currentState.foresterPatrols.size());
            
            stateUpdatesService.sendMessageToQueue(QueueNames.SUPPORT_AGGREGATED_DATA, configuration)
                .doOnSuccess(v -> log.info("Published configuration to support service"))
                .doOnError(e -> log.error("Failed to publish configuration to support service: {}", e.toString()))
                .subscribe();
        }
        
        return Flux.interval(interval)
            .map(tick -> {
                synchronized(currentState) {
                    currentState.timestamp = new Date();
                    currentState.tick = tick;
                    SimulationStateDto dto = SimulationStateDto.from(currentState);
                    
                    // Send full state via SSE (frontend needs complete state)
                    // But optimize queue updates to support service - send only changed entities
                    if (tick % 10 == 0) {
                        log.debug("SSE tick {} - B:{}, P:{}, R:{}", tick, dto.fireBrigades().size(), 
                            dto.foresterPatrols().size(), dto.recommendedActions().size());
                    }
                    
                    // Send optimized update to support service (only changed entities)
                    sendOptimizedUpdateToQueue(tick);
                    
                    return dto;
                }
            });
    }

    public void assignBrigadesToSector(final int sectorId, final List<Integer> brigades) {
        synchronized(currentState) {
            var sector = currentState.sectors.get(sectorId);
            if (sector == null) {
                log.error("Resource not found: Sector {} does not exist when attempting to assign brigades {}", 
                    sectorId, brigades);
                return;
            }
            sector.assignedBrigades = brigades == null ? new ArrayList<>() : new ArrayList<>(brigades);
        }
    }
    
    /**
     * Send optimized update to support service queue - only changed entities.
     * This reduces network traffic significantly compared to sending full state every time.
     */
    private void sendOptimizedUpdateToQueue(long tick) {
        if (lastSentState == null) {
            // First update - send full state
            SimulationStateDto fullDto = SimulationStateDto.from(currentState);
            stateUpdatesService.sendMessageToQueue(QueueNames.SUPPORT_AGGREGATED_DATA, fullDto)
                .doOnError(e -> log.error("Failed to publish initial state to support service: {}", e.toString()))
                .subscribe();
            lastSentState = cloneState(currentState);
            return;
        }
        
        // Calculate and send only changed entities
        List<Sector> changedSectors = new ArrayList<>();
        List<FireBrigade> changedBrigades = new ArrayList<>();
        List<ForesterPatrol> changedPatrols = new ArrayList<>();
        List<pl.edu.agh.kis.firebackend.model.events.RecommendedAction> newRecommendations = new ArrayList<>();
        
        // Find changed sectors (only fire/burn/extinguish levels or assigned brigades changed)
        for (var entry : currentState.sectors.entrySet()) {
            var currentSector = entry.getValue();
            var lastSector = lastSentState.sectors.get(entry.getKey());
            
            if (lastSector == null || 
                currentSector.state.fireLevel != lastSector.state.fireLevel ||
                currentSector.state.burnLevel != lastSector.state.burnLevel ||
                currentSector.state.extinguishLevel != lastSector.state.extinguishLevel ||
                !currentSector.assignedBrigades.equals(lastSector.assignedBrigades)) {
                changedSectors.add(currentSector);
            }
        }
        
        // Find changed brigades
        for (var entry : currentState.fireBrigades.entrySet()) {
            var currentBrigade = entry.getValue();
            var lastBrigade = lastSentState.fireBrigades.get(entry.getKey());
            
            if (lastBrigade == null || 
                !currentBrigade.location().equals(lastBrigade.location()) ||
                currentBrigade.state() != lastBrigade.state() ||
                currentBrigade.sectorId() != lastBrigade.sectorId()) {
                changedBrigades.add(currentBrigade);
            }
        }
        
        // Find changed patrols
        for (var entry : currentState.foresterPatrols.entrySet()) {
            var currentPatrol = entry.getValue();
            var lastPatrol = lastSentState.foresterPatrols.get(entry.getKey());
            
            if (lastPatrol == null ||
                !currentPatrol.location().equals(lastPatrol.location()) ||
                currentPatrol.state() != lastPatrol.state() ||
                currentPatrol.sectorId() != lastPatrol.sectorId()) {
                changedPatrols.add(currentPatrol);
            }
        }
        
        // Find new recommendations
        for (var entry : currentState.recommendedActions.entrySet()) {
            if (!lastSentState.recommendedActions.containsKey(entry.getKey())) {
                newRecommendations.add(entry.getValue());
            }
        }
        
        // Only send if there are changes
        if (!changedSectors.isEmpty() || !changedBrigades.isEmpty() || 
            !changedPatrols.isEmpty() || !newRecommendations.isEmpty()) {
            
            // Create optimized DTO with only changed entities
            SimulationStateDto optimizedDto = new SimulationStateDto(
                currentState.forestName,
                currentState.timestamp,
                currentState.tick,
                changedSectors,
                changedBrigades,
                changedPatrols,
                newRecommendations
            );
            
            stateUpdatesService.sendMessageToQueue(QueueNames.SUPPORT_AGGREGATED_DATA, optimizedDto)
                .doOnError(e -> log.error("Failed to publish optimized state update to support service: {}", e.toString()))
                .subscribe();
            
            // Update last sent state
            lastSentState = cloneState(currentState);
        }
    }
    
    /**
     * Create a deep copy of SimulationState for tracking changes.
     */
    private SimulationState cloneState(SimulationState state) {
        SimulationState cloned = new SimulationState();
        cloned.forestName = state.forestName;
        cloned.timestamp = state.timestamp != null ? new Date(state.timestamp.getTime()) : null;
        cloned.tick = state.tick;
        
        // Deep copy sectors
        cloned.sectors = new java.util.HashMap<>();
        for (var entry : state.sectors.entrySet()) {
            var sector = entry.getValue();
            var clonedSector = new Sector(
                sector.sectorId,
                cloneSectorState(sector.state),
                new ArrayList<>(sector.contours),
                new ArrayList<>(sector.assignedBrigades)
            );
            cloned.sectors.put(entry.getKey(), clonedSector);
        }
        
        // Deep copy brigades
        cloned.fireBrigades = new java.util.HashMap<>();
        for (var entry : state.fireBrigades.entrySet()) {
            cloned.fireBrigades.put(entry.getKey(), entry.getValue());
        }
        
        // Deep copy patrols
        cloned.foresterPatrols = new java.util.HashMap<>();
        for (var entry : state.foresterPatrols.entrySet()) {
            cloned.foresterPatrols.put(entry.getKey(), entry.getValue());
        }
        
        // Deep copy recommendations
        cloned.recommendedActions = new java.util.HashMap<>();
        for (var entry : state.recommendedActions.entrySet()) {
            cloned.recommendedActions.put(entry.getKey(), entry.getValue());
        }
        
        return cloned;
    }
    
    private SectorState cloneSectorState(SectorState state) {
        // Create new SectorState with all visible fields
        SectorState cloned = new SectorState(
            state.timestamp != null ? new Date(state.timestamp.getTime()) : null,
            state.temperature,
            state.windSpeed,
            state.windDirection,
            state.airHumidity,
            state.plantLitterMoisture,
            state.co2Concentration,
            state.pm2_5Concentration,
            null, // threatLevel - not accessible
            null, // fireState - not accessible
            state.fireLevel,
            state.burnLevel,
            state.extinguishLevel
        );
        return cloned;
    }
}
