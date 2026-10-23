package io.github.heorhipuhachou.bmuz.catalogue;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String homepage;

    protected Store() {
    }

    public Store(String name, String homepage) {
        this.name = name;
        this.homepage = homepage;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHomepage() {
        return homepage;
    }
}
