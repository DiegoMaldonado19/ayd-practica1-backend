package com.fitness.app.iam.model;

/**
 * Where a temporary code is sent. Lives in iam and not in common/enums because
 * it is the only module using it: notification.channel is a different set
 * (EMAIL, SMS, IN_APP).
 */
public enum VerificationChannel
{
    EMAIL,
    SMS
}
