import com.adaptavist.hapi.cloud.jira.issues.Issues

def now = Calendar.getInstance()
def month = now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())

def projectKey = 'DEMO'
Issues.create(projectKey, "Time Logging"){
    setSummary("Time logging for ${month}")
    setDescription("Log development time here for ${month}")
}
