package com.maxlim33

import javax.mail.Session
import javax.mail.internet.MimeMessage

// ASSUMPTION: the accounts are always just a portal customer
@Singleton
class JsmUtils {
    private static final MLIM_MAIL_CHANNEL = 'CHANNEL0674f17ec435'
    private static final JSM_DEFAULT_MAIL_CHANNEL = 'CHANNEL5637d83388d8'

    // "id": 9417,
    // "mailChannelId": x,
    // "mailItemId": xxxx,
    // "handlerName": "Jira Service Management Mail Handler",
    // "resultStatus": "NEW REQUEST",
    // "fromAddress": "xxx@example.com",
    // "mailChannelName": "xxx@example.com",
    // "subject": "xxxxx",
    // "issueKey": "KEY-XXXX",
    // "noOfRetry": 0,
    // "updatedDateTime": "19/Feb/26 11:32 AM",
    // "createdDateTime": "19/Feb/26 11:32 AM"
    List<Map> getNewRequestMailsFromDefaultChannelSinceTime(Long epoch) {
        def searchMailResponse = get("/rest/jira-email-processor-plugin/1.0/mail/audit/process/$JSM_DEFAULT_MAIL_CHANNEL")
            .queryString('from', epoch)
            .queryString('statuses', 'NEW REQUEST')
            .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
            .asObject(Map)
        searchMailResponse.body['data'] as List<Map>
    }

    String getLatestMailId(String issueKey, Collection<String> types = ['NEW REQUEST', 'NEW COMMENT']) {
        [MLIM_MAIL_CHANNEL, JSM_DEFAULT_MAIL_CHANNEL].findResult { channelId ->
            println "Search mails on $channelId"
            def searchMailRequest = get("/rest/jira-email-processor-plugin/1.0/mail/audit/process/$channelId")
                .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
                .queryString('searchText', issueKey)
            types.each { searchMailRequest.queryString('statuses', it) }
            def searchMailResponse = searchMailRequest.asObject(Map)

            searchMailResponse.body['data'].find { it['issueKey'] == issueKey }?['id']
        }
    }

    MimeMessage getMailMessage(String mailId) {
        InputStream rawMessage = get("/rest/jira-email-processor-plugin/1.0/mail/download/$mailId")
            .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
            .asBinary()
            .body

        new MimeMessage(Session.getDefaultInstance(new Properties()), rawMessage)
    }

    MimeMessage getLatestMailMessage(String issueKey, Collection<String> types = ['NEW REQUEST', 'NEW COMMENT']) {
        def latestMailId = getLatestMailId(issueKey, types)
        latestMailId ? getMailMessage(latestMailId) : null
    }

    List getCustomerProfiles(List<String> accountIds) {
        def customerProfiles = post("https://api.atlassian.com/jsm/csm/cloudid/${MLIM_CSM_CLOUD_ID}/api/v1/customer/profile/fetch")
            .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
            .header('X-ExperimentalApi', 'opt-in')
            .header('Content-Type', 'application/json')
            .body([
                customerIds: accountIds
            ])
            .asObject(Map).body['profiles'] as List
        return customerProfiles
    }

    String setCustomerAccounts(List customerProfiles) {
        def idempotencyKey = UUID.randomUUID().toString()
        def statusUrl = post("https://api.atlassian.com/jsm/csm/cloudid/${MLIM_CSM_CLOUD_ID}/api/v1/customer/bulk")
            .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
            .header('X-ExperimentalApi', 'opt-in')
            .header('Idempotency-Key', idempotencyKey)
            .header('Content-Type', 'application/json')
            .body([
                customerAccounts: customerProfiles.collect {[
                    operationType: 'UPDATE',
                    payload: [
                        displayName: it['displayName'],
                        email: it['email'],
                        customerId: it['customerId'],
                    ]
                ]}
            ])
            .asObject(Map).body['statusUrl']
        return statusUrl
    }

    String getCustomerEmail(String accountId) {
        def customers = []
        def start = 0
        def isLastPage = false
        while (!isLastPage) {
            def authResponse = get("/rest/servicedeskapi/servicedesk/2/customer")
                .header('X-ExperimentalApi', 'opt-in')
                .queryString('query', accountId)
                .queryString('start', start)
                .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
                .asObject(Map)
            assert authResponse.status >= 200 && authResponse.status <= 300
            customers.addAll(authResponse.body["values"])
            isLastPage = authResponse.body['isLastPage']
            start = start + (authResponse.body['limit'] as Integer)
        }

        return customers.find { it['accountId'] == accountId }['emailAddress']
    }

//    void addEmailIntoCustomerDetails(String accountId) {
//        def getEmailAddressResponse = get("https://api.atlassian.com/jsm/csm/cloudid/${MLIM_CSM_CLOUD_ID}/api/v1/customer/${accountId}")
//            .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
//            .asObject(Map)
//        def emailAddress = (getEmailAddressResponse.body['details'].find { it['name'] == 'Email' }['values'] as List)?.first()
//
//        if (!emailAddress) {
//            def customers = []
//            def start = 0
//            def isLastPage = false
//            while (!isLastPage) {
//                def authResponse = get("/rest/servicedeskapi/servicedesk/2/customer")
//                    .header('X-ExperimentalApi', 'opt-in')
//                    .queryString('query', accountId)
//                    .queryString('start', start)
//                    .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
//                    .asObject(Map)
//                assert authResponse.status >= 200 && authResponse.status <= 300
//                customers.addAll(authResponse.body["values"])
//                isLastPage = authResponse.body['isLastPage']
//                start = start + (authResponse.body['limit'] as Integer)
//            }
//
//            // query accountId doesn't give unique result unfortunately
//            emailAddress = customers.find { it['accountId'] == accountId }['emailAddress']
//
//            put("https://api.atlassian.com/jsm/csm/cloudid/${MLIM_CSM_CLOUD_ID}/api/v1/customer/${accountId}/details")
//                .header('Content-Type', 'application/json')
//                .queryString('fieldName', 'Email')
//                .basicAuth(MLIM_EMAIL, MLIM_API_TOKEN)
//                .body([
//                    values: [emailAddress]
//                ])
//                .asObject(Map)
//        }
//    }
}