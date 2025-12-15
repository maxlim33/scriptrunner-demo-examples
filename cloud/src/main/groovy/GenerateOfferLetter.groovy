/**
 * Generate Confluence page from a template by filling in variables with Jira data linked.
 *
 * Prerequisites:
 * - An admin user email and API token are required for calling APIs, not standard Jira and JSM.
 *   Visit https://id.atlassian.com/manage-profile/security/api-tokens to create one.
 * - The admin user must have 'Export individual content' access to the Confluence space.
 *   Navigate 'Space settings > Users > People in this space > ... > Manage access' to verify.
 * - The email and API token should be stored securely as Script Variables and referenced in scripts
 *   (e.g., {@code MAXLIM33_EMAIL}, {@code MAXLIM33_API_TOKEN}).
 *   See https://docs.adaptavist.com/sr4jc/latest/features/script-variables for more information.
 */

/********** User Input Start **********/

final CONFLUENCE_SPACE_KEY = 'HR'
final OFFER_LETTER_TEMPLATE_NAME = 'ACME offer letter'
final NAME_CF_NAME = 'Summary'
final START_DATE_CF_NAME = 'Employee Start date'
final ROLE_CF_NAME = 'Job title'
final AUTH_USER_EMAIL = MAXLIM33_EMAIL
final AUTH_USER_API_TOKEN = MAXLIM33_API_TOKEN

/********** User Input End **********/

import java.sql.Timestamp
import java.time.LocalDateTime
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.apache.http.entity.ContentType

def eventIssue = Issues.getByKey(issue.key as String)

final CUSTOM_TEMPLATES_QUERY = '''query CustomTemplatesQuery($spaceId: Long!) {
  confluence {
    templates {
      spaceTemplates(spaceId: $spaceId) {
        id
        name
      }
    }
  }
}
'''

def confluenceSpace = (get("$baseUrl/wiki/api/v2/spaces?keys=${CONFLUENCE_SPACE_KEY}").basicAuth(AUTH_USER_EMAIL, AUTH_USER_API_TOKEN).asObject(Map).body['results'] as List).first()
def confluenceSpaceId = confluenceSpace['id']
def confluenceSpaceHomepageId = confluenceSpace['homepageId']

def getTemplatesResponse = post("$baseUrl/cgraphql")
    .header('Content-Type', 'application/json')
    .basicAuth(AUTH_USER_EMAIL, AUTH_USER_API_TOKEN)
    .body([
        operationName: "CustomTemplatesQuery",
        query: CUSTOM_TEMPLATES_QUERY,
        variables: [
            spaceId: confluenceSpaceId
        ]
    ])
    .asObject(Map)
assert !getTemplatesResponse.body['errors'] : getTemplatesResponse.body['errors']
def templates = getTemplatesResponse.body['data']['confluence']['templates']['spaceTemplates'] as List
def offerLetterTemplateId = templates.find { it['name'] == OFFER_LETTER_TEMPLATE_NAME }['id']

// This uses instance system time, not user preference time
def now = LocalDateTime.now()

def roleTitle = eventIssue.getCustomFieldValue(ROLE_CF_NAME) as String
def name = eventIssue.getCustomFieldValue(NAME_CF_NAME) as String
def startDate = (eventIssue.getCustomFieldValue(START_DATE_CF_NAME) as Timestamp).toLocalDate()

def getTemplateAdfResponse = get("/wiki/rest/api/template/${offerLetterTemplateId}")
    .basicAuth(AUTH_USER_EMAIL, AUTH_USER_API_TOKEN)
    .queryString('expand', 'body.atlas_doc_format')
    .asObject(Map)
assert getTemplateAdfResponse.status >= 200 && getTemplateAdfResponse.status < 300 : "${getTemplateAdfResponse.status}: ${getTemplateAdfResponse.body}"
def templateAdfInString = getTemplateAdfResponse.body['body']['atlas_doc_format']['value'] as String
def templateAdf = new JsonSlurper().parseText(templateAdfInString) as Map

replaceVariable(templateAdf, 'position_name_bold' , createBoldTextNode(roleTitle.toUpperCase()))
replaceVariable(templateAdf, 'position_name' , createTextNode(roleTitle))
replaceVariable(templateAdf, 'recipient_name', createTextNode(name))
replaceVariable(templateAdf, 'sent_date', createTextNode(now.format('dd MMMM yyyy')))
replaceVariable(templateAdf, 'effective_date', createTextNode(startDate.format('dd MMMM yyyy')))

def createLetterResponse = post("/wiki/api/v2/pages")
    .header('Content-Type', 'application/json')
    .basicAuth(AUTH_USER_EMAIL, AUTH_USER_API_TOKEN)
    .body([
        spaceId: confluenceSpaceId,
        title: "${eventIssue.key} - $name - Offer Letter - generated at ${now.format('dd MMMM yyyy HH:mm:ss')}",
        parentId: confluenceSpaceHomepageId,
        body: [
            representation: 'atlas_doc_format',
            value: JsonOutput.toJson(templateAdf)
        ]
    ])
    .asObject(Map)
assert createLetterResponse.status >= 200 && createLetterResponse.status < 300 : "${createLetterResponse.status}: ${createLetterResponse.body}"

def report = createLetterResponse.body
def reportId = report['id']
def reportLink = "${report['_links']['base']}/spaces/${CONFLUENCE_SPACE_KEY}/pages/$reportId"

// For DC: https://support.atlassian.com/confluence/kb/rest-api-to-export-and-download-a-page-in-pdf-format/
def queuePdfExportResponse = get("$baseUrl/wiki/spaces/flyingpdf/pdfpageexport.action?pageId=$reportId")
    .basicAuth(AUTH_USER_EMAIL, AUTH_USER_API_TOKEN)
    .header('X-Atlassian-Token', 'no-check')
    .asString()
assert queuePdfExportResponse.status >= 200 && queuePdfExportResponse.status < 300 : "${queuePdfExportResponse.status}: ${queuePdfExportResponse.body}"

def m = queuePdfExportResponse.body =~ /<meta name="ajs-taskId" content="(.+)">/
def taskId = (m[0] as List)[1]
logger.warn "taskId: $taskId"

// Poll every 3 seconds until result is available or timeout reached
def generatorLink = null
def attempt = 0
def maxAttempt = 20  // 20 * 3s = 60 seconds timeout

while (!generatorLink && attempt < maxAttempt) {
    sleep(3000) // wait 3 seconds between checks
    attempt++

    def progressResp = get("/wiki/services/api/v1/task/$taskId/progress")
        .basicAuth(AUTH_USER_EMAIL, AUTH_USER_API_TOKEN)
        .asObject(Map)
        .body

    generatorLink = progressResp['result'] as String
}

if (!generatorLink) throw new RuntimeException("PDF generation timed out after ${attempt * 3} seconds.")

def downloadLink = get(generatorLink).basicAuth(AUTH_USER_EMAIL, AUTH_USER_API_TOKEN).asString().body as String

new URL(downloadLink).withInputStream { inputStream ->
    def filename = "$name - Offer Letter - ${now.format('dd MMMM yyyy HH:mm:ss')}.pdf"
    def attachments = post("/rest/api/3/issue/${eventIssue.id}/attachments")
        .header('X-Atlassian-Token', 'no-check')
        .field("file", inputStream, ContentType.create("application/pdf"), filename)
        .asObject(List)
        .body
    def attachmentFilename = attachments.first()['filename']
    def commentBody = """\
This is approved and the offer letter is generated in Confluence: [$reportLink|$reportLink]
A PDF version is also attached here:\\n\\n[^$attachmentFilename]\\n\\n
"""

    def postCommentResponse = post("/rest/servicedeskapi/request/${eventIssue.id}/comment")
        .header('Content-Type', 'application/json')
        .body([
            body: commentBody,
            public: false
        ])
        .asObject(Map)
    assert postCommentResponse.status >= 200 && postCommentResponse.status < 300 : "${postCommentResponse.status}: ${postCommentResponse.body}"
}

// Following functions are generated with AI
void replaceVariable(Object node, String varName, Map newNode) {
    Closure<Boolean> isTargetVar = { o ->
        o instanceof Map &&
            o.type == 'inlineExtension' &&
            o.attrs instanceof Map &&
            o.attrs.extensionType == 'com.atlassian.confluence.template' &&
            o.attrs.extensionKey  == 'variable' &&
            o.attrs.parameters['name'] == varName
    }

    if (node instanceof Map) {
        node.each { k, v ->
            if (isTargetVar(v)) {
                node[k] = newNode
            } else {
                replaceVariable(v, varName, newNode)
            }
        }
    } else if (node instanceof List) {
        for (int i = 0; i < node.size(); i++) {
            def v = node[i]
            if (isTargetVar(v)) {
                node[i] = newNode
            } else {
                replaceVariable(v, varName, newNode)
            }
        }
    }
}

Map createTextNode(String text) {
    [type: 'text', text: text]
}

Map createBoldTextNode(String text) {
    createTextNode(text) + ['marks': [['type': 'strong']]]
}