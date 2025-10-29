import com.adaptavist.hapi.cloud.jira.issues.Issues

import java.time.temporal.ChronoUnit

def chronoUnit = ChronoUnit.DAYS

String key = issue.key
def hapiIssue = Issues.getByKey(key)
def createdDate = hapiIssue.created.toZonedDateTime()
def updatedDate = hapiIssue.updated.toZonedDateTime()

def dateDifference = chronoUnit.between(createdDate, updatedDate)

// Return the value
"${dateDifference} ${chronoUnit.toString().toLowerCase()}"

