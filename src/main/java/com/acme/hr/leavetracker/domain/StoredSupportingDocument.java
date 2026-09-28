package com.acme.hr.leavetracker.domain;

public record StoredSupportingDocument(String storageKey, String originalFilename, String contentType, long fileSize) { }
