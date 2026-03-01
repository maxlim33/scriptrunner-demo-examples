import com.maxlim33.JsmUtils

logger.warn "issue_event_type_name: $issue_event_type_name"
logger.warn "changelog: $changelog"
if (issue_event_type_name == 'issue_updated') {
    if (!(changelog['items'].collect { it['fieldId'] }.contains('customfield_10039'))) return
}

final EMAILS = [
    'maxlim33@icloud.com'
]

def reporterAccountId = issue.fields['reporter']['accountId'] as String
def requestParticipantAccountIds = issue.fields['customfield_10039'].collect { it['accountId'] } as List<String>

JsmUtils.instance.addEmailIntoCustomerDetails(reporterAccountId)
requestParticipantAccountIds.each { JsmUtils.instance.addEmailIntoCustomerDetails(it) }

// if (!(reporterEmailAddress in EMAILS)) return

// def requestParticipants = issue.fields['customfield_10039'] as List
// def requestBody = [
//     // raiseOnBehalfOf: issue.fields['reporter'], //issue.fields['reporter']['accountId'],
//     // isAdfRequest: true,
//     requestFieldValues: [
//         summary: issue.fields['summary'],
//     ],
//     serviceDeskId: 2,
//     requestTypeId: 10,
//     requestParticipants: requestParticipants.collect { it['accountId'] }, // doesn't work, from issue history, natively Jira also make second request to add
//     // channel: 'email', // doesn't work, use below to set if needed
// ]
// logger.warn "requestBody: $requestBody"
// logger.warn "descriptoin: ${issue.fields['description']}"
// def createRequestResponse = post('/rest/servicedeskapi/request')
//     .header('Content-Type', 'application/json')
//     // .header('X-ExperimentalApi', 'opt-in')
//     .body([
//         *:requestBody,
//         requestFieldValues: [
//             *:requestBody.requestFieldValues,
//             description: issue.fields['description']
//         ]
//     ])
//     .asObject(Map)
// def requestIssueId = createRequestResponse.body['issueId']
// // To set request channel type
// put("/rest/api/3/issue/$requestIssueId/properties/request.channel.type")
//     .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
//     .header('Content-Type', 'application/json')
//     .body([value: 'email'])
//     .asString()

// post("/rest/servicedeskapi/request/${requestIssueId}/comment")
//     .header('Content-Type', 'application/json')
//     .body([
//         body: issue.fields['description'],
//         public: true
//     ])
//     .asObject(Map)

// delete("/rest/api/3/issue/${issue.key}").basicAuth(MLIM_EMAIL, MLIM_API_TOKEN).asString()