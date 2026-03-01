import com.adaptavist.hapi.cloud.jira.issues.Issue
import com.maxlim33.JiraUtils
import com.maxlim33.JsmUtils
import com.maxlim33.MailUtils
import javax.mail.internet.InternetAddress

println "webhookEvent: $webhookEvent"
if (comment['author']['accountId'] != '5f89cb49c07c880075bba803' || !comment['jsdPublic']) return

def issue = Issues.getByKey(issue.key as String) as Issue

def toAccountIds = issue.getCustomFieldValue('To').collect { it['accountId'] } as List<String>
def ccAccountIds = issue.getCustomFieldValue('Cc').collect { it['accountId'] } as List<String>
def bccAccountIds = issue.getCustomFieldValue('Bcc').collect { it['accountId'] } as List<String>
def requestParticipants = issue.getCustomFieldValue('Request participants').collect { it['accountId'] } as List<String>

def customerProfiles = JsmUtils.instance.getCustomerProfiles(toAccountIds + ccAccountIds + bccAccountIds)
def replyTo = customerProfiles.findAll { it['customerId'].toString() in toAccountIds }.collect { new InternetAddress(it['email'].toString(), it['displayName'].toString())}
def replyCc = customerProfiles.findAll { it['customerId'].toString() in ccAccountIds }.collect { new InternetAddress(it['email'].toString(), it['displayName'].toString())}
def replyBcc = customerProfiles.findAll { it['customerId'].toString() in bccAccountIds }.collect { new InternetAddress(it['email'].toString(), it['displayName'].toString())}

def replyMessage = MailUtils.instance.sendLatestCommentAsMessage(issue, replyTo, replyCc, replyBcc)
JiraUtils.instance.setEmailMessageIdCommentProperty(comment.id as String, replyMessage.messageID)
issue.update {
    clearCustomField('Bcc')
    if (requestParticipants != toAccountIds + ccAccountIds)
        setCustomFieldValue('Request participants', toAcountIds + ccAccountIds)
}