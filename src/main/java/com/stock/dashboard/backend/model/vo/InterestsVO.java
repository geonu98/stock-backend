package com.stock.dashboard.backend.model.vo;

import com.stock.dashboard.backend.model.User;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*; // 🛑 Jakarta EE로 변경

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ToString
@Entity
@Table(name = "INTERESTS")
public class InterestsVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NAME", nullable = false)
    private String name;


    @ManyToMany(mappedBy = "interests", fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();
}
