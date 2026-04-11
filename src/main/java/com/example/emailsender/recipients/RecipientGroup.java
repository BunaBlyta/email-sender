package com.example.emailsender.recipients;

import com.example.emailsender.user.User;
import jakarta.persistence.*;
import java.util.List;

@Entity
public class RecipientGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    private String name;

    @ElementCollection
    private List<String> members;

    public RecipientGroup() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
}
