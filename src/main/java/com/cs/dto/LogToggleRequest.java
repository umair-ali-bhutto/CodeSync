package com.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class LogToggleRequest {

    @Schema(
        description = "Enable or disable logs (Y/N)",
        example = "Y",
        allowableValues = {"Y", "N"}
    )
    private String enableLogs;

    public String getEnableLogs() {
        return enableLogs;
    }

    public void setEnableLogs(String enableLogs) {
        this.enableLogs = enableLogs;
    }
}