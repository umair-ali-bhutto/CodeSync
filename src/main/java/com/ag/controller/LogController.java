package com.ag.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ag.config.CodeSyncLogger;
import com.ag.config.StartUpInit;

import java.util.Map;

/**
 * REST Controller to enable or disable application logging at runtime.
 */
@RestController
@RequestMapping("/logsService")
public class LogController {

    /**
     * Enables or disables logs based on the request payload.
     *
     * Expected JSON:
     * {
     *   "enable.logs": "Y"
     * }
     *
     * Only "Y" or "N" (case-insensitive) are allowed.
     *
     * @param body JSON request body
     * @return ResponseEntity<Void>
     */
    @PostMapping
    public ResponseEntity<Void> saveOrUpdate(@RequestBody(required = true) Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("enable.logs")) {
            	StartUpInit.setEnableLogs("Y");
            	CodeSyncLogger.logInfo("Logs service called with missing 'enable.logs'. Defaulting to 'N'.");
                StartUpInit.setEnableLogs("N");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String value = body.get("enable.logs").toString().trim().toUpperCase();

            if (!value.equals("Y") && !value.equals("N")) {
            	StartUpInit.setEnableLogs("Y");
                CodeSyncLogger.logInfo("Invalid value for enable.logs: " + value + ". Defaulting to 'N'.");
                StartUpInit.setEnableLogs("N");
                value = "N";
            }

			StartUpInit.setEnableLogs("Y");
			CodeSyncLogger.logInfo("Logs service called. Setting enableLogs=" + value);
            StartUpInit.setEnableLogs(value);

        } catch (Exception e) {
            CodeSyncLogger.logError(LogController.class, "Failed to update log settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok().build();
    }
}
