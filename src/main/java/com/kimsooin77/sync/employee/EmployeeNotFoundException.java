package com.kimsooin77.sync.employee;

public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(Long id) { super("Employee was not found: " + id); }
}
