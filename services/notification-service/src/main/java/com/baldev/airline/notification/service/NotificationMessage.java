package com.baldev.airline.notification.service;

import java.util.Map;

/**
 * What to tell a customer about one event, before it is rendered for a channel.
 *
 * @param eventKey  idempotency key: {@code <topic>:<aggregate id>[:<discriminator>]}
 * @param type      template name under {@code templates/email/}
 * @param userId    inbox owner; {@code null} for no in-app entry
 * @param email     e-mail recipient; {@code null} or blank for no e-mail
 * @param subject   e-mail subject and inbox title
 * @param summary   one or two plain sentences for the inbox
 * @param variables template variables
 */
public record NotificationMessage(String eventKey, String type, Long userId, String email, String subject,
                                  String summary, Map<String, Object> variables) {
}
