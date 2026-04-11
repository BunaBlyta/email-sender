package com.example.emailsender.mail.model;

import jakarta.persistence.*;

@Entity
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Message message;

    private String filename;
    private String mimeType;
    private Long sizeBytes;
    private String storageRef;

    public Attachment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Message getMessage() { return message; }
    public void setMessage(Message message) { this.message = message; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }
}
