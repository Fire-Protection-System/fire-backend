package pl.edu.agh.kis.firebackend.controller;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.agh.kis.firebackend.model.configuration.Configuration;
import pl.edu.agh.kis.firebackend.model.simulation.SimulationStateDto;
import pl.edu.agh.kis.firebackend.service.HttpRequestService;
import pl.edu.agh.kis.firebackend.service.SimulationStateService;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SimulationStateController {
    private final SimulationStateService simulationStateService;
    private final HttpRequestService httpRequestService;

    private static final Logger log = LoggerFactory.getLogger(SimulationStateService.class);

    @Value("${SIMULATOR_PORT}") 
    private int simulatorPort;

    @PostMapping(value = "/run-simulation", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SimulationStateDto> runSimulation(
            @RequestParam(required = false, defaultValue = "5") long interval,
            @RequestBody Configuration configuration) {
        log.info("Received run-simulation request with interval={}", interval);
        log.info("Configuration: {}", configuration);
        return simulationStateService.runSimulation(configuration, Duration.ofSeconds(interval));
    }

    @PostMapping("/send-simulation-request")
    public ResponseEntity<String> sendSimulationRequest(@RequestBody Configuration configuration) {
        log.info("Sending HTTP request to start simulation...");
        httpRequestService.sendPostRequest("http://fire-simulation:" + simulatorPort + "/run_simulation", configuration);
        return ResponseEntity.ok("Configuration send to simulation!");  
    }
}
