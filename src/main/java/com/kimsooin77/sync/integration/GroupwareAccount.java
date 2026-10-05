package com.kimsooin77.sync.integration;

public record GroupwareAccount(
        String employeeNo,
        String name,
        String email,
        String departmentCode,
        String employmentStatus,
        boolean enabled
) {

    public static GroupwareAccount from(GroupwareAccountRequest request, boolean enabled) {
        return new GroupwareAccount(request.employeeNo(), request.name(), request.email(),
                request.departmentCode(), request.employmentStatus(), enabled);
    }
}
