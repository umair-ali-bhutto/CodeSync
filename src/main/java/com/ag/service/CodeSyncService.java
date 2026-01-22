package com.ag.service;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.ag.entity.CodeSync;
import com.ag.repository.CodeSyncRepository;

@Service
@Transactional
public class CodeSyncService {

	private final CodeSyncRepository repository;

	public CodeSyncService(CodeSyncRepository repository) {
		this.repository = repository;
	}

	public CodeSync getOrCreate(String shareKey) {
		return repository.findByShareKey(shareKey).orElseGet(() -> {
			CodeSync share = new CodeSync();
			share.setShareKey(shareKey);
			share.setContent("");
			return repository.save(share);
		});
	}

	public CodeSync get(String shareKey) {
		return repository.findByShareKey(shareKey).orElseThrow(() -> new RuntimeException("Share not found"));
	}

	public CodeSync update(String shareKey, String content) {
		CodeSync share = getOrCreate(shareKey);
		share.setContent(content);
		return repository.save(share);
	}

	public void delete(String shareKey) {
		repository.deleteByShareKey(shareKey);
	}
}
