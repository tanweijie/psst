package app.psst.chat.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    protected UserEntity() {}

    public UserEntity(String username) {
        this.username = username;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
}
