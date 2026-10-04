package com.kimsooin77.sync.simulation.hr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mock/hr")
@ConditionalOnProperty(prefix = "app.mock.hr", name = "enabled", havingValue = "true")
public class MockHrController {

    private final MockHrDataset dataset;

    public MockHrController(MockHrDataset dataset) {
        this.dataset = dataset;
    }

    @GetMapping("/employees")
    public List<MockHrEmployeeResponse> employees() {
        return dataset.employees();
    }
}
