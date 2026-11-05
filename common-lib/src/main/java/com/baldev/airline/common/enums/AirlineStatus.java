package com.baldev.airline.common.enums;

/** Lifecycle controlled by system admins only; owners can never set it. */
public enum AirlineStatus {
    PENDING_APPROVAL,
    ACTIVE,
    INACTIVE,
    BANNED
}
