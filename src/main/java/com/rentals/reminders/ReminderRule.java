package com.rentals.reminders;

/**
 * Reminder rule - defines when and how a tenant is reminded about rent.
 * Example: stage 1, 3 days after due date, via SMS.
 */
public class ReminderRule {

    private Long ruleId;
    private Integer stage;
    private Integer daysAfterDue;
    private String channel = "SMS";
    private String messageTemplate;
    private boolean enabled = true;

    public ReminderRule() {}

    public ReminderRule(Integer stage, Integer daysAfterDue, String channel, String messageTemplate) {
        this.stage = stage;
        this.daysAfterDue = daysAfterDue;
        this.channel = channel;
        this.messageTemplate = messageTemplate;
    }

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Integer getStage() { return stage; }
    public void setStage(Integer stage) { this.stage = stage; }
    public Integer getDaysAfterDue() { return daysAfterDue; }
    public void setDaysAfterDue(Integer daysAfterDue) { this.daysAfterDue = daysAfterDue; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getMessageTemplate() { return messageTemplate; }
    public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
