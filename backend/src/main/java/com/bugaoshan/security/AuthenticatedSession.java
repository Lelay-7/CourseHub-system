package com.bugaoshan.security;
/** Server-owned snapshot, captured after validating JWT against the database. */
public record AuthenticatedSession(int userType,int credentialVersion) {}
