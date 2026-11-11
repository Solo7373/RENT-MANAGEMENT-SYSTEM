package com.rentals.service;

import com.rentals.users.User;

/**
 * ============================================================
 * SESSION SERVICE
 * ============================================================
 *
 * Keeps track of who is currently logged in.
 * Uses static state so every part of the app can read the session.
 */
public class SessionService {

    private static User currentUser;

    /** Creates a session for the logged-in admin. */
    public void createAdminSession(User user) {
        currentUser = user;
        System.out.println("Session created for: " + user.getFullName());
    }

    /** Destroys the current session (logout). */
    public void destroySession() {
        currentUser = null;
    }

    /** Returns the currently logged-in user (may be null). */
    public static User getCurrentUser() {
        return currentUser;
    }

    /** Checks if anyone is logged in. */
    public boolean isLoggedIn() {
        return currentUser != null;
    }
}
