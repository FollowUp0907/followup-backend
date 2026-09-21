package com.followup.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Project not found"),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not a member of this project"),
    PROJECT_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "Only the project owner can perform this action"),
    PROJECT_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Project member not found"),
    PROJECT_MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "User is already a member of this project"),
    PROJECT_OWNER_CANNOT_BE_REMOVED(HttpStatus.CONFLICT, "Project owner cannot be removed"),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "Meeting not found"),
    INVALID_MEETING_PARTICIPANT(HttpStatus.BAD_REQUEST, "Participant must be a member of this project"),
    INVALID_CARRY_OVER_ACTION_ITEM(HttpStatus.BAD_REQUEST, "Carry-over action item must be an incomplete action item of this project"),
    ACTION_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Action item not found"),
    INVALID_ACTION_ITEM_ASSIGNEE(HttpStatus.BAD_REQUEST, "Assignee must be a member of this project"),
    INVALID_ACTION_ITEM_STATUS(HttpStatus.BAD_REQUEST, "Invalid action item status"),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "Analysis not found"),
    MEETING_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "Meeting content is empty"),
    AI_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI analysis failed"),
    ANALYSIS_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "Analysis has already been confirmed"),
    ANALYSIS_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "Analysis is not in a confirmable state"),
    INVALID_ANALYSIS_ASSIGNEE(HttpStatus.BAD_REQUEST, "Assignee must be a member of this project"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid Google ID token"),
    GOOGLE_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "Google email is not verified"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "Token has expired"),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "No handler found for the requested path"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this path"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found"),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have access to this notification"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
