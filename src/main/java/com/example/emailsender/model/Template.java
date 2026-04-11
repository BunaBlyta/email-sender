package com.example.emailsender.model;

import jakarta.persistence.*;

@Entity
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String subject;

    @Column(length = 5000)
    private String body;

    public Template() {
    }

    public Template(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    // Optional but handy
    public void setId(Long id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
