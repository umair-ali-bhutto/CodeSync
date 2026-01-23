package com.ag.service;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.ag.config.CodeSyncLogger;
import com.ag.entity.CodeSync;
import com.ag.exception.ShareNotFoundException;
import com.ag.repository.CodeSyncRepository;

/**
 * Business layer for CodeSync operations.
 */
@Service
@Transactional
public class CodeSyncService {

    private final CodeSyncRepository repository;

    public CodeSyncService(CodeSyncRepository repository) {
        this.repository = repository;
    }

    /**
     * Fetches an existing share or creates a new one if it does not exist.
     *
     * @param shareKey unique share identifier
     * @return CodeSync entity
     */
    public CodeSync getOrCreate(String shareKey) {
        return repository.findByShareKey(shareKey).orElseGet(() -> {
            CodeSyncLogger.logInfo("Creating new share: " + shareKey);
            CodeSync share = new CodeSync();
            share.setShareKey(shareKey);
            share.setContent("");
            return repository.save(share);
        });
    }

    /**
     * Updates content for a given share key.
     *
     * @param shareKey unique share identifier
     * @param content text content
     * @return updated CodeSync entity
     */
    public CodeSync update(String shareKey, String content) {
        CodeSync share = getOrCreate(shareKey);
        share.setContent(content);
        CodeSyncLogger.logDebug("Updated content for key: " + shareKey);
        return repository.save(share);
    }

    /**
     * Deletes a share by key.
     *
     * @param shareKey unique share identifier
     */
    public void delete(String shareKey) {
        if (!repository.findByShareKey(shareKey).isPresent()) {
            throw new ShareNotFoundException(shareKey);
        }
        CodeSyncLogger.logInfo("Deleting share: " + shareKey);
        repository.deleteByShareKey(shareKey);
    }
}
