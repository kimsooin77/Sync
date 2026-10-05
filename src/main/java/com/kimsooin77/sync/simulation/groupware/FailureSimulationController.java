package com.kimsooin77.sync.simulation.groupware;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/mock/groupware/failure-simulation")
@ConditionalOnProperty(prefix = "app.mock.groupware", name = "enabled", havingValue = "true")
public class FailureSimulationController {
    private final FailureSimulation simulation;
    private final MockGroupwareProperties properties;

    public FailureSimulationController(FailureSimulation simulation, MockGroupwareProperties properties) {
        this.simulation = simulation;
        this.properties = properties;
    }

    @GetMapping
    public FailureSimulationResponse get() {
        return new FailureSimulationResponse(properties.defaultRule(), simulation.overrides());
    }

    @PutMapping("/{employeeNo}")
    public ResponseEntity<Void> put(@PathVariable String employeeNo, @RequestBody FailureSimulationRequest request) {
        if (request == null || request.mode() == null) throw badRequest("mode is required");
        long delay = 0;
        if (request.mode() == FailureMode.DELAY) {
            delay = request.delayMs() == null ? properties.getDefaultDelayMs() : request.delayMs();
        } else if (request.delayMs() != null) {
            throw badRequest("delayMs is only valid for DELAY mode");
        }
        if (delay < 0 || delay > properties.getMaxDelayMs()) throw badRequest("delayMs is out of range");
        try {
            simulation.setOverride(employeeNo, new FailureRule(request.mode(), delay));
        } catch (IllegalArgumentException invalid) {
            throw badRequest("employeeNo is required");
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{employeeNo}")
    public ResponseEntity<Void> delete(@PathVariable String employeeNo) {
        try { simulation.removeOverride(employeeNo); }
        catch (IllegalArgumentException invalid) { throw badRequest("employeeNo is required"); }
        return ResponseEntity.noContent().build();
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
