package com.securebank.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// A saved payee, so customers don't have to type account numbers every time
@Entity
@Table(name = "beneficiaries",
        uniqueConstraints = @UniqueConstraint(name = "uk_beneficiary_owner_account", columnNames = {"owner_id", "account_number"}))
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    // Nickname chosen by the customer, e.g. "Kavya (sister)"
    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String accountNumber;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
