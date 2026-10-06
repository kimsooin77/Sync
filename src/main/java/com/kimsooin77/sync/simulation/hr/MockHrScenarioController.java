package com.kimsooin77.sync.simulation.hr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/hr/scenario")
@ConditionalOnProperty(prefix = "app.mock.hr", name = "enabled", havingValue = "true")
public class MockHrScenarioController {
    private final MockHrScenarioState scenarioState;

    public MockHrScenarioController(MockHrScenarioState scenarioState) {
        this.scenarioState = scenarioState;
    }

    @GetMapping
    public MockHrScenarioResponse get() {
        return MockHrScenarioResponse.from(scenarioState.current());
    }

    @PutMapping
    public ResponseEntity<MockHrScenarioResponse> update(@RequestBody MockHrScenarioRequest request) {
        if (request == null || request.scenario() == null) {
            throw new IllegalArgumentException("scenario is required");
        }
        return ResponseEntity.ok(MockHrScenarioResponse.from(scenarioState.changeTo(request.scenario())));
    }
}
