import com.adaptavist.hapi.cloud.jira.issues.Issue
import com.maxlim33.JiraUtils
import com.maxlim33.JsmUtils
import com.maxlim33.MailUtils
import javax.mail.internet.InternetAddress
import java.time.Instant

println "webhookEvent: $webhookEvent"
if (issue['fields']['reporter']['accountId'] != '5f89cb49c07c880075bba803') return
if (JsmUtils.instance.getLatestMailId(issue.key as String)) return

def issue = Issues.getByKey(issue.key as String) as Issue

def toAccountIds = issue.getCustomFieldValue('To').collect { it['accountId'] } as List<String>
def ccAccountIds = issue.getCustomFieldValue('Cc').collect { it['accountId'] } as List<String>
def bccAccountIds = issue.getCustomFieldValue('Bcc').collect { it['accountId'] } as List<String>

def customerProfiles = JsmUtils.instance.getCustomerProfiles(toAccountIds + ccAccountIds + bccAccountIds)

def to = customerProfiles.findAll { it['customerId'].toString() in toAccountIds }.collect { new InternetAddress(it['email'].toString(), it['displayName'].toString())}
def cc = customerProfiles.findAll { it['customerId'].toString() in ccAccountIds }.collect { new InternetAddress(it['email'].toString(), it['displayName'].toString())}
def bcc = customerProfiles.findAll { it['customerId'].toString() in bccAccountIds }.collect { new InternetAddress(it['email'].toString(), it['displayName'].toString())}
bcc.add(new InternetAddress('support@mlim-csm.atlassian.net'))

def now = Instant.now().toEpochMilli()
def message = MailUtils.instance.sendNewIssueAsMessage(issue, to, cc, bcc)
JiraUtils.instance.setEmailMessageIdIssueProperty(issue.id as String, message.messageID)
// Do not need to clear Bcc because this is going to be deleted, and only to and cc will be copied over
// issue.update { clearCustomField('Bcc') }

// Poll every 3 seconds until cloned issue through email channel is detected or timeout reached
def newIssueKey = ''
def attempt = 0
def maxAttempt = 20  // 20  * 3s = 60 seconds timeout

while (!newIssueKey && attempt < maxAttempt) {
    sleep(3000) // wait 3 seconds between checks
    attempt++

    def newNow = Instant.now().toEpochMilli()
    def mails = JsmUtils.instance.getNewRequestMailsFromDefaultChannelSinceTime(now)
    newIssueKey = mails.find {
        JsmUtils.instance.getMailMessage(it['id'] as String).messageID == message.messageID
    }?['issueKey'] as String
    now = newNow
}

if (!newIssueKey) {
    issue.addComment("Cloned issue is not detected in the JSM email channel within 60s, preserving this issue.")
    return
}

def newIssueUrl = "$baseUrl/browse/$newIssueKey"
issue.addComment("""To let JSM capture proper message references, a cloned issue is created through JSM email channel:

[$newIssueUrl|$newIssueUrl|smart-link]

This issue will be deleted in 30 seconds.""")
def newIssue = Issues.getByKey(newIssueKey) as Issue
newIssue.update {
    if (toAccountIds) setCustomFieldValue('To', toAccountIds)
    if (ccAccountIds) setCustomFieldValue('Cc', ccAccountIds)
}
JiraUtils.instance.setEmailMessageIdIssueProperty(newIssue.id, message.messageID)
sleep(30000)
issue.delete()