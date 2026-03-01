package com.maxlim33

import groovy.json.JsonOutput

// While I can declare `setEmailMessageId*` as static, I foresee that I might need to use these methods in Confluence
// And, I will need to pass in manual authentication anyway. So, go for Singleton.
@Singleton
class JiraUtils {
    private static final EMAIL_MESSAGE_ID_PROPERTY_KEY = 'com.maxlim33.email.message-id'

    String getEmailMessageIdCommentProperty(String commentId) {
        get("/rest/api/3/comment/$commentId/properties/$EMAIL_MESSAGE_ID_PROPERTY_KEY")
            .asObject(Map).body['value']
    }

    String getEmailMessageIdIssueProperty(String issueIdOrKey) {
        get("/rest/api/3/issue/$issueIdOrKey/properties/$EMAIL_MESSAGE_ID_PROPERTY_KEY")
            .asObject(Map).body['value']
    }

    void setEmailMessageIdCommentProperty(String commentId, String messageId) {
        put("/rest/api/3/comment/$commentId/properties/$EMAIL_MESSAGE_ID_PROPERTY_KEY")
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson(messageId))
            .asObject(Map)
    }

    void setEmailMessageIdIssueProperty(String issueIdOrKey, String messageId) {
        put("/rest/api/3/issue/$issueIdOrKey/properties/$EMAIL_MESSAGE_ID_PROPERTY_KEY")
            .header('Content-Type', 'application/json')
            .body(JsonOutput.toJson(messageId))
            .asObject(Map)
    }
}
