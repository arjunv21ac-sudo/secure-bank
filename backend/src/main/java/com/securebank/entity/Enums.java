package com.securebank.entity;

public final class Enums {

    private Enums() {
    }

    public enum Role {
        CUSTOMER,
        ADMIN
    }

    public enum AccountType {
        SAVINGS,
        CURRENT
    }

    public enum AccountStatus {
        PENDING,   // waiting for admin approval
        ACTIVE,
        FROZEN,    // blocked by admin: no money can move in or out
        REJECTED
    }

    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER
    }
}
