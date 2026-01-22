package pl.edu.agh.kis.firebackend.integration;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;
import pl.edu.agh.kis.firebackend.configuration.QueueNames;
import pl.edu.agh.kis.firebackend.model.events.EvRecommendation;
import pl.edu.agh.kis.firebackend.service.StateUpdatesService;
import pl.edu.agh.kis.firebackend.service.model.UpdatesQueue;
import pl.edu.agh.kis.firebackend.service.model.configuration.Configuration;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for LLM prediction functionality across fire-backend, fire-simulation, and fire-support services.
 * 
 * This test verifies:
 * 1. Integration of LLM between all three services
 * 2. Generation of LLM predictions by support service
 * 3. Data flow: backend → simulation → support → backend (recommendations)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "RABBITMQ_HOST=127.0.0.1",
    "RABBITMQ_PORT=5672",
    "RABBITMQ_USER=guest",
    "RABBITMQ_PASS=guest",
    "MONGO_URI=mongodb://127.0.0.1:27017/configurations",
    "FIRE_SIMULATION_SERVICE=127.0.0.1",
    "FIRE_SUPPORT_SERVICE=127.0.0.1",
    "SIMULATOR_PORT=5000",
    "SUPPORT_PORT=5001"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("IntegrationTest")
public class LlmPredictionIntegrationTest {
    
    private static final Logger log = LoggerFactory.getLogger(LlmPredictionIntegrationTest.class);
    
    @LocalServerPort
    private int backendPort;
    
    @Autowired
    private StateUpdatesService stateUpdatesService;
    
    private RestTemplate httpClient;
    private String backendBaseUrl;
    private String simulationBaseUrl;
    private String supportBaseUrl;
    
    private Flux<EvRecommendation> recommendationsFlux;
    private List<EvRecommendation> receivedRecommendations;
    
    @BeforeEach
    void setUp() {
        httpClient = new RestTemplate();
        backendBaseUrl = "http://localhost:" + backendPort;
        simulationBaseUrl = "http://127.0.0.1:5000";
        supportBaseUrl = "http://127.0.0.1:5001";
        
        receivedRecommendations = new ArrayList<>();
        recommendationsFlux = stateUpdatesService.createUpdatesFlux(
            new UpdatesQueue<>(QueueNames.SUPPORT_RECOMMENDATIONS, EvRecommendation.class)
        );
        
        log.info("Test setup complete. Backend URL: {}", backendBaseUrl);
    }
    
    @AfterEach
    void tearDown() {
        try {
            httpClient.postForEntity(
                backendBaseUrl + "/simulation/stop-simulation",
                null,
                String.class
            );
            log.info("Stopped simulation");
        } catch (Exception e) {
            log.warn("Failed to stop simulation: {}", e.getMessage());
        }
        
        try {
            httpClient.postForEntity(
                supportBaseUrl + "/stop",
                null,
                String.class
            );
            log.info("Stopped support service");
        } catch (Exception e) {
            log.warn("Failed to stop support service: {}", e.getMessage());
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Test LLM prediction generation - full integration")
    void testLlmPredictionGeneration() throws IOException, InterruptedException {
        
        boolean simulationRunning = isServiceRunning(simulationBaseUrl);
        boolean supportRunning = isServiceRunning(supportBaseUrl);
        
        if (!simulationRunning) {
            log.error("Fire Simulation service is NOT running!");
        } else {
            log.info("Fire Simulation service działa poprawnie.");
        }
        if (!supportRunning) {
            log.error("Fire Support service is NOT running!");
        } else {
            log.info("Fire Support service działa poprawnie.");
        }

        assertTrue(simulationRunning, "Fire Simulation service should be running (sprawdź logi i endpoint /health)");
        assertTrue(supportRunning, "Fire Support service should be running (sprawdź logi i endpoint /health)");

        configureLlmMode(true);

        log.info("Ładowanie konfiguracji lasu...");
        Path configPath = Paths.get("fire-configurations/forest/forest_5x5.json");
        Configuration config = TestUtils.loadForestConfig(configPath.toString());

        List<Integer> sectorsWithFire = Arrays.asList(1, 2, 3);
        config = TestUtils.addActiveFires(config, sectorsWithFire, 0.5);

        CountDownLatch recommendationLatch = new CountDownLatch(1);
        AtomicReference<EvRecommendation> firstRecommendation = new AtomicReference<>();

        recommendationsFlux
            .doOnNext(recommendation -> {
                log.info("Odebrano rekomendację: {} akcji", 
                    recommendation.recommendedActions() != null ? recommendation.recommendedActions().size() : 0);
                receivedRecommendations.add(recommendation);
                if (firstRecommendation.get() == null) {
                    firstRecommendation.set(recommendation);
                    recommendationLatch.countDown();
                }
            })
            .doOnError(error -> {
                log.error("Błąd podczas odbierania rekomendacji: {}", error.getMessage());
                recommendationLatch.countDown();
            })
            .subscribe();


        log.info("Uruchamianie symulacji z trybem LLM...");
        ResponseEntity<String> runResponse = httpClient.postForEntity(
            backendBaseUrl + "/simulation/run-simulation?llmMode=true",
            config,
            String.class
        );

        if (runResponse.getStatusCode().is2xxSuccessful() || runResponse.getStatusCode() == HttpStatus.OK) {
            log.info("Symulacja: żądanie uruchomienia wysłane poprawnie (status: {}).", runResponse.getStatusCode());
        } else {
            log.error("Symulacja: żądanie uruchomienia NIE powiodło się (status: {}).", runResponse.getStatusCode());
        }
        assertTrue(
            runResponse.getStatusCode().is2xxSuccessful() || 
            runResponse.getStatusCode() == HttpStatus.OK,
            "Symulacja powinna uruchomić się poprawnie (HTTP)"
        );


        boolean simulationStarted = false;
        int timeoutSeconds = 20;
        for (int i = 0; i < timeoutSeconds; i++) {
            Thread.sleep(1000);
            if (isServiceRunning(simulationBaseUrl)) {
                simulationStarted = true;
                log.info("Symulacja faktycznie działa po uruchomieniu! (czekano {}s)", i+1);
                break;
            }
        }
        if (!simulationStarted) {
            log.error("Symulacja NIE wystartowała w ciągu {} sekund po żądaniu! Sprawdź backend i fire-simulation.", timeoutSeconds);
        }
        assertTrue(simulationStarted, "Symulacja powinna faktycznie działać po uruchomieniu (sprawdzenie /health, timeout " + timeoutSeconds + "s)");
        log.info("Symulacja uruchomiona, oczekiwanie na rekomendacje...");

        boolean recommendationReceived = recommendationLatch.await(30, TimeUnit.SECONDS);
        assertTrue(recommendationReceived, "Powinna zostać odebrana przynajmniej jedna rekomendacja w ciągu 30 sekund");
        assertFalse(receivedRecommendations.isEmpty(), "Powinna zostać odebrana przynajmniej jedna rekomendacja");

        EvRecommendation recommendation = firstRecommendation.get();
        assertNotNull(recommendation, "Pierwsza rekomendacja nie powinna być nullem");

        assertTrue(
            TestUtils.isValidRecommendationStructure(recommendation),
            "Struktura rekomendacji powinna być poprawna"
        );

        assertTrue(
            TestUtils.targetsActiveFires(recommendation, new HashSet<>(sectorsWithFire)),
            "Rekomendacja powinna dotyczyć sektorów z aktywnym pożarem"
        );

        assertTrue(
            TestUtils.isLlmRecommendation(recommendation),
            "Rekomendacja powinna pochodzić z LLM"
        );

        log.info("Test zakończony sukcesem. Odebrano {} rekomendacji", receivedRecommendations.size());
    }
    
    /**
     * Configure LLM mode in support service via backend.
     */
    private void configureLlmMode(boolean enabled) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("enabled", enabled);
        
        try {
            ResponseEntity<String> response = httpClient.postForEntity(
                backendBaseUrl + "/simulation/llm-mode",
                requestBody,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("LLM mode configured: enabled={}", enabled);
            } else {
                log.warn("Failed to configure LLM mode: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to configure LLM mode: {}", e.getMessage());
            // Don't fail the test - LLM mode might be configured via environment variables
        }
    }
    
    /**
     * Check if a service is running by calling its health endpoint.
     */
    private boolean isServiceRunning(String baseUrl) {
        try {
            ResponseEntity<String> response = httpClient.getForEntity(
                baseUrl + "/health",
                String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Service at {} is not reachable: {}", baseUrl, e.getMessage());
            return false;
        }
    }
}
