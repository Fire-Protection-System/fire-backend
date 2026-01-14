package pl.edu.agh.kis.firebackend.controller;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import pl.edu.agh.kis.firebackend.model.configuration.Configuration;
import pl.edu.agh.kis.firebackend.model.simulation.SimulationStateDto;
import pl.edu.agh.kis.firebackend.service.HttpRequestService;
import pl.edu.agh.kis.firebackend.service.SimulationStateService;
import pl.edu.agh.kis.firebackend.service.StateUpdatesService;
import pl.edu.agh.kis.firebackend.configuration.QueueNames;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/simulation")
public class SimulationStateController 
{
    private final SimulationStateService simulationStateService;
    private final HttpRequestService httpRequestService;
    private final StateUpdatesService stateUpdatesService;
    private static final Logger log = LoggerFactory.getLogger(SimulationStateController.class);

    @Value("${FIRE_SIMULATION_SERVICE:127.0.0.1}")
    private String simulationHost;

    @Value("${SIMULATOR_PORT:5000}")
    private int simulatorPort;

    @PostMapping(value = "/run-simulation", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SimulationStateDto> runSimulation(
        @RequestParam(required = false) Long interval,
        @RequestBody Configuration configuration)
    {
        long effectiveInterval = (interval != null && interval > 0) ? interval : 5L;
        
        return simulationStateService.runSimulation(configuration, Duration.ofSeconds(effectiveInterval));
    }

    @PostMapping("/send-simulation-request")
    public ResponseEntity<String> sendSimulationRequest(@RequestBody Configuration configuration) 
    {
        String url = String.format("http://%s:%d/run_simulation", simulationHost, simulatorPort);
        try {
            httpRequestService.sendPostRequest(url, configuration);
            return ResponseEntity.ok("Configuration sent to simulation!");
        } catch (Exception e) {
            log.error("Failed to send run_simulation request to simulation at {}:{}", simulationHost, simulatorPort, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Simulation service unreachable");
        }
    }

    @PostMapping("/orderFireBrigade")
    public ResponseEntity<String> orderFireBrigade(@RequestBody Object payload) 
    {
        String url = String.format("http://%s:%d/orderFireBrigade", simulationHost, simulatorPort);
        try {
            httpRequestService.sendPostRequest(url, payload);
            return ResponseEntity.ok("Order forwarded to simulation");
        } catch (Exception e) {
            log.warn("Failed to forward fire brigade order to simulation at {}:{} - attempting queue fallback", simulationHost, simulatorPort);
            try {
                stateUpdatesService.sendMessageToQueue(QueueNames.SIMULATION_CONTROL_FIRE_BRIGADE_ACTIONS, payload)
                    .subscribe(
                        unused -> {},
                        err -> log.error("Fallback enqueue failed: {}", err.toString())
                    );
                return ResponseEntity.status(HttpStatus.ACCEPTED).body("Simulation unreachable; order queued");
            } catch (Exception ex) {
                log.error("Fallback enqueue failed entirely: {}", ex.toString());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Simulation unreachable and enqueue failed");
            }
        }
    }

    @PostMapping("/orderForestPatrol")
    public ResponseEntity<String> orderForestPatrol(@RequestBody Object payload) 
    {
        String url = String.format("http://%s:%d/orderForestPatrol", simulationHost, simulatorPort);
        try {
            httpRequestService.sendPostRequest(url, payload);
            return ResponseEntity.ok("Order forwarded to simulation");
        } catch (Exception e) {
            log.warn("Failed to forward forester patrol order to simulation at {}:{} - attempting queue fallback", simulationHost, simulatorPort);
            try {
                stateUpdatesService.sendMessageToQueue(QueueNames.SIMULATION_CONTROL_FORESTER_ACTIONS, payload)
                    .subscribe(
                        unused -> {},
                        err -> log.error("Fallback enqueue failed: {}", err.toString())
                    );
                return ResponseEntity.status(HttpStatus.ACCEPTED).body("Simulation unreachable; order queued");
            } catch (Exception ex) {
                log.error("Fallback enqueue failed entirely: {}", ex.toString());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Simulation unreachable and enqueue failed");
            }
        }
    }

    @PostMapping("/assignBrigades")
    public ResponseEntity<String> assignBrigades(@RequestBody java.util.Map<String, Object> payload) 
    {
        Object sectorIdObj = payload.get("sectorId");
        Object assigned = payload.get("assignedBrigades");

        if (sectorIdObj instanceof Number) {
            int sectorId = ((Number) sectorIdObj).intValue();
            java.util.List<Integer> brigades = new java.util.ArrayList<>();

            if (assigned instanceof java.util.List<?>) 
                {
                for (Object o : (java.util.List<?>) assigned) 
                    {
                    if (o instanceof Number)
                    { 
                        brigades.add(((Number) o).intValue());
                    }
                    else if (o instanceof String) 
                    {
                        try { brigades.add(Integer.parseInt((String) o)); } catch (NumberFormatException e) { }
                    }
                }
            }
            simulationStateService.assignBrigadesToSector(sectorId, brigades);
        } 
        else
        {
            log.warn("Invalid sectorId in assignBrigades payload: {}", sectorIdObj);
        }

        String url = String.format("http://%s:%d/assignBrigades", simulationHost, simulatorPort);
        try {
            httpRequestService.sendPostRequest(url, payload);
            return ResponseEntity.ok("AssignBrigades forwarded to simulation");
        } catch (Exception e) {
            log.error("Failed to forward assignBrigades to simulation at {}:{}", simulationHost, simulatorPort, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Simulation service unreachable");
        }
    }

    @PostMapping("/stop-simulation")
    public ResponseEntity<String> sendStopRequest() 
    {
        String url = String.format("http://%s:%d/stop_simulation", simulationHost, simulatorPort);
        try {
            httpRequestService.sendPostRequest(url, "");
            return ResponseEntity.ok("Stop request sent!");
        } catch (Exception e) {
            log.error("Failed to send stop_simulation request to simulation at {}:{}", simulationHost, simulatorPort, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Simulation service unreachable");
        }
    }

    @PostMapping("/set-speed")
    public ResponseEntity<String> setSimulationSpeed(@RequestParam("tickInterval") Double tickIntervalSeconds)
    {
        if (tickIntervalSeconds == null || tickIntervalSeconds <= 0) {
            return ResponseEntity.badRequest().body("tickInterval must be > 0");
        }

        String url = String.format("http://%s:%d/set_speed", simulationHost, simulatorPort);
        try {
            java.util.Map<String, Object> payload = java.util.Map.of("tickInterval", tickIntervalSeconds);
            httpRequestService.sendPostRequest(url, payload);
            return ResponseEntity.ok("Simulation speed updated");
        } catch (Exception e) {
            log.error("Failed to send set_speed request to simulation at {}:{}", simulationHost, simulatorPort, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Simulation service unreachable");
        }
    }
}