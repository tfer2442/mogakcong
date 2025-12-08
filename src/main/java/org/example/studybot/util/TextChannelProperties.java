package org.example.studybot.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "text-channel")
public class TextChannelProperties {

    private String targetChannelName;

    private String summaryChannelName;

    public String getTargetChannelName() {
        return targetChannelName;
    }

    public void setTargetChannelName(String targetChannelName) {
        this.targetChannelName = targetChannelName;
    }

    public String getSummaryChannelName() {
        return summaryChannelName;
    }

    public void setSummaryChannelName(String summaryChannelName) {
        this.summaryChannelName = summaryChannelName;
    }
}