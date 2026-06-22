package com.example.emailsender.recipients;

import com.example.emailsender.user.User;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class RecipientGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "recipient_group_members",
            joinColumns = @JoinColumn(name = "recipient_group_id")
    )
    @Column(name = "email", nullable = false)
    private List<String> members = new ArrayList<>();

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
