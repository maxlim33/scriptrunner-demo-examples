/*
 * This script ignore issues and comments from myself, then does 3 things:
 * 1. Save email message id in as issue or comment property
 * 2. Set customer display names if it is different from inside the email message
 * 3. Set replyTo, replyCc in custom fields so that I can change when I reply the email as comment in the issue
 */

import com.adaptavist.hapi.cloud.jira.issues.Issue
import com.maxlim33.JiraUtils
import com.maxlim33.JsmUtils
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

println "webhookEvent: $webhookEvent"
if (webhookEvent == 'comment_created' && comment['author']['accountType'] != 'customer') return
if (webhookEvent == 'jira:issue_created' && issue['fields']['reporter']['accountType'] != 'customer') return

// Assumption: Reporter and request participants are all portal customers
def issue = Issues.getByKey(issue.key as String) as Issue

MimeMessage message
if (webhookEvent == 'comment_created') {
    // There is a possibility that there are two emails come in very quick and retrieving the latest one would be inaccurate
    // Keep it for now as proper verification can be expensive
    message = JsmUtils.instance.getLatestMailMessage(issue.key, ['NEW COMMENT'])
    JiraUtils.instance.setEmailMessageIdCommentProperty(comment.id as String, message.messageID)
} else {
    message = JsmUtils.instance.getLatestMailMessage(issue.key, ['NEW REQUEST'])
    JiraUtils.instance.setEmailMessageIdIssueProperty(issue.id as String, message.messageID)
}

def messageFrom = message.getFrom() as List<InternetAddress>
def messageTo = message.getRecipients(Message.RecipientType.TO) as List<InternetAddress> ?: []
def messageCc = message.getRecipients(Message.RecipientType.CC) as List<InternetAddress> ?: []

def replyTo = message.getReplyTo() as List<InternetAddress> ?: messageFrom
def replyCc = (messageTo + messageCc)
    .findAll { it && !it.address.equalsIgnoreCase(MLIM_EMAIL) }
    .unique()

def reporterAccountId = issue.reporterId as String
def requestParticipantAccountIds =
    (issue.getCustomFieldValue('Request participants') ?: [])
        .collect { it['accountId'] as String }
def recipientAccountIds = requestParticipantAccountIds + reporterAccountId
def recipientCustomerProfiles = JsmUtils.instance.getCustomerProfiles(recipientAccountIds)

def updatedUpdateRequiredCustomerProfiles = recipientCustomerProfiles.collect { customerProfile ->
    // Some emails have different 'from' and 'reply-to'
    def displayNameFromMessage=
        (messageFrom + replyTo + replyCc)
            .unique()
            .find { it.address.equalsIgnoreCase(customerProfile['email'] as String) }
            ?.personal
    if (!displayNameFromMessage || customerProfile['displayName'] == displayNameFromMessage) return null
    customerProfile['displayName'] = displayNameFromMessage
    customerProfile
}.findAll()

if (updatedUpdateRequiredCustomerProfiles) JsmUtils.instance.setCustomerAccounts(updatedUpdateRequiredCustomerProfiles)
issue.update {
    setCustomFieldValue(
        'To',
        recipientCustomerProfiles
            .findAll { it['email'].toString() in replyTo*.address }
            .collect { it['customerId'] as String }
    )
    if (replyCc) setCustomFieldValue(
        'Cc',
        recipientCustomerProfiles
            .findAll { it['email'].toString() in replyCc*.address }
            .collect { it['customerId'] as String }
    )
}