package com.stock.dashboard.backend.model;

import com.stock.dashboard.backend.model.User;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.NaturalId;

import java.util.HashSet;
import java.util.Set;

@Entity(name = "ROLE")
@NoArgsConstructor
@ToString(exclude = "userList")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ROLE_ID")
    private Long id;

    @NaturalId
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE_NAME")
    private RoleName role;

    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private Set<User> userList = new HashSet<>();

    public Role(RoleName role) {
        this.role = role;
    }

    public boolean isAdminRole() {
        return RoleName.ROLE_ADMIN.equals(this.role);
    }

    public boolean isUserRole() {
        return RoleName.ROLE_USER.equals(this.role);
    }

    public boolean isSystemRole() {
        return RoleName.ROLE_SYSTEM.equals(this.role);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RoleName getRole() { return role; }
    public void setRole(RoleName role) { this.role = role; }

    public Set<User> getUserList() { return userList; }
    public void setUserList(Set<User> userList) { this.userList = userList; }
}
