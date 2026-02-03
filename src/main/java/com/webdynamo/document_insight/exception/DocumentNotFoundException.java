package com.webdynamo.document_insight.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String message) {
        super(message);
    }

    public DocumentNotFoundException(Long id) {
        super("Document not found with id: " + id);
    }
}
