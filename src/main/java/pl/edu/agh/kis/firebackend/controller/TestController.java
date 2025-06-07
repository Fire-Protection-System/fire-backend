package pl.edu.agh.kis.firebackend.controller;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import pl.edu.agh.kis.firebackend.model.FireBrigadeAction;
import pl.edu.agh.kis.firebackend.model.OrderFireBrigade;
import pl.edu.agh.kis.firebackend.service.HttpRequestService;
import pl.edu.agh.kis.firebackend.service.StateUpdatesService;

import java.util.Date;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
public class TestController {

    private final StateUpdatesService stateUpdatesService;
    private final HttpRequestService httpRequestService;

    @Value("${SIMULATOR_PORT}") 
    private int simulatorPort;

    @PostMapping("/sendData")
    public String sendData(@RequestBody Map<String, Object> data) {
        String url = "http://127.0.0.1:" + simulatorPort + "/run_simulation";
        return httpRequestService.sendPostRequest(url, data);
    }

    @GetMapping("/sendTestDataToMQ")
    public String sendToMQ() {
        // Utwórz obiekt OrderFireBrigade z bieżącym czasem
        OrderFireBrigade order = new OrderFireBrigade(
                456,
                FireBrigadeAction.GO_TO_BASE,
                new Date() // Bieżąca data
        );

        // Wyślij wiadomość do kolejki
        stateUpdatesService.sendMessageToQueue("Fire brigade", order)
                           .subscribe(); // Asynchroniczne wysłanie wiadomości

        return "Message sent!";
    }
}
