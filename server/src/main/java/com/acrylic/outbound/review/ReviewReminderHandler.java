package com.acrylic.outbound.review;

import com.acrylic.outbound.jobs.*;
import org.springframework.stereotype.Component;

@Component
public class ReviewReminderHandler implements BackgroundJobHandler {
    private final ReviewReminderStore reminders;
    public ReviewReminderHandler(ReviewReminderStore reminders) { this.reminders = reminders; }
    @Override public String jobType() { return "REVIEW_REMINDER_SWEEP"; }
    @Override public Runnable execute(BackgroundJobExecution execution) { return reminders::createDueReminders; }
}
