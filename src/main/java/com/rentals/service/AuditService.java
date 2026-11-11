package com.rentals.service;

import com.rentals.dao.AuditDAO;
import com.rentals.users.User;

/**
 * Audit service - one-line helper to record user actions.
 * Uses the currently logged-in user from SessionService.
 */
public class AuditService {

    private static final AuditDAO auditDAO = new AuditDAO();

    /** Logs an action for the current user. */
    public static void log(String action, String description) {
        Long userId = null;
        User user = SessionService.getCurrentUser();
        if (user != null) userId = user.getUserId();
        auditDAO.log(userId, action, description);
    }
}
