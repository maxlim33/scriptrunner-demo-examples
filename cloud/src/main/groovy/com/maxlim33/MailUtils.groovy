package com.maxlim33

import com.adaptavist.hapi.cloud.jira.issues.Issue
import javax.mail.*
import javax.mail.internet.*
import javax.activation.DataSource
import javax.activation.DataHandler
import javax.mail.util.ByteArrayDataSource
import javax.mail.search.HeaderTerm
import org.jsoup.Jsoup

@Singleton
class MailUtils {
    private final gmailSmtpProperties = [
        'mail.smtp.host'              : 'smtp.gmail.com',
        'mail.smtp.port'              : '587',
        'mail.smtp.auth'              : 'true',
        'mail.smtp.starttls.enable'   : 'true',
        // By default, javax.mail uses message id in form of <unique_id@[ip_address]>
        // JSM mail channel doesn't pick up the square brackets properly
        // Setting this, make the message id to <unique_id@mail_host>
        "mail.host"                   : "mail.maxlim33.com"
    ] as Properties

    private final gmailImapsProperties = [
        'mail.store.protocol'      : 'imaps',
        'mail.imaps.host'          : 'imap.gmail.com',
        'mail.imaps.port'          : '993',
        'mail.imaps.ssl.enable'    : 'true'
    ] as Properties

    private final gmailAuthenticator = new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(MLIM_EMAIL, MLIM_APP_PASSWORD)
        }
    }

    private final fromInternetAddress = new InternetAddress(MLIM_EMAIL, MLIM_DISPLAY_NAME)

    MimeMessage getMailMessageById(String messageId) {
        Store store = Session.getInstance(gmailImapsProperties, gmailAuthenticator).getStore("imaps")
        store.connect()
        MimeMessage message
        try {
            Folder inbox = store.getFolder("[Gmail]/All Mail")
            inbox.open(Folder.READ_ONLY)
            // Google remove square brackets to search id
            // In GMail UI, you can search by message id like so: rfc822msgid:<524471808.3.1771387104105@169.254.240.5>
            def hits = inbox.search(new HeaderTerm('Message-ID', messageId.replaceAll(/[\[\]]/, ''))) as List<MimeMessage>

            // IMPORTANT: detach message from IMAP folder/store
            message = (hits && hits.size() > 0) ? new MimeMessage(hits[0]) : null
        } finally {
            store?.close()
        }

        if (!message) println "No message found for Message-ID: ${messageId}"
        return message
    }

    private static Map processRenderedBody(String html) {
        def attachments = []
        def m = (html =~ /src="\/rest\/api\/3\/attachment\/content\/(\d+)"/)
        m.each { List match ->
            def attachmentId = match[1]
            def getAttachmentResponse = get("/rest/api/3/attachment/${attachmentId}").asObject(Map)
            def attachment = [
                id: attachmentId,
                name: getAttachmentResponse.body['filename'],
                contentType: getAttachmentResponse.body['mimeType'],
                bytes: (get("/rest/api/3/attachment/content/${attachmentId}").asBinary().body as ByteArrayInputStream).bytes,
                disposition: 'inline',
                contentId: "<file-$attachmentId>"
            ]
            attachments.addAll(attachment)
            html = html.replaceAll(
                "src=\"/rest/api/3/attachment/content/$attachmentId\"",
                "src=\"cid:file-${attachmentId}\"")
        }

        def n = (html =~ /(?s)<a\b.*?href="\/rest\/api\/3\/attachment\/content\/(\d+)"[^>]*?>(.*?)(?:\s*<sup\b.*?>.*?<\/sup>)?\s*?<\/a>/)
        n.each { List match ->
            def attachmentId = match[1]
            def anchorText = match[2]
            def getAttachmentResponse = get("/rest/api/3/attachment/${attachmentId}").asObject(Map)
            def attachment = [
                id: attachmentId,
                name: getAttachmentResponse.body['filename'],
                contentType: getAttachmentResponse.body['mimeType'],
                bytes: (get("/rest/api/3/attachment/content/${attachmentId}").asBinary().body as ByteArrayInputStream).bytes,
                disposition: 'attachment',
            ]
            attachments.addAll(attachment)
            html = html.replaceAll(
                /(?s)<a\b.*?href="\/rest\/api\/3\/attachment\/content\/${attachmentId}"[^>]*?>(.*?)(?:\s*<sup\b.*?>.*?<\/sup>)?\s*?<\/a>/,
                "${anchorText} (attached)"
            )
        }

        def doc = Jsoup.parse(html)
        def mentionedAccountIds = doc.select("a[accountid]")
            .collect { a -> a.attr("accountid") }
            .unique()

        // ASSUMPTION: I won't mention myself, because I am a customer in JSM
        def mentionedCustomerProfiles = JsmUtils.instance.getCustomerProfiles(mentionedAccountIds)


        doc.select("a[accountid]").each { a ->
            def accountId = a.attr("accountid")
            def name = a.text()

            a.clearAttributes()
            a.attr("href", "mailto:${mentionedCustomerProfiles.find { it['customerId'] == accountId }['email']}")
            a.text("@${name}")
        }

        html = doc.body().html()

        // Consistent with parseMessage for attachment:
        // Long term, declare this as a class
        // id         : attachmentId,
        // name       : fn ?: "unknown",
        // contentType: ct,
        // disposition: disposition,
        // bytes      : part.getInputStream().bytes,
        // contentId  : contentId // Works fine, only inline attachment needs contentId
        [html: html, attachments: attachments]
    }

    static Map parseMessage(MimePart part) {
        // acc = accumulator
        def acc = [
            textParts: [],
            htmlParts: [],
            attachments: []
        ]
        walk(part, acc)

        String text = acc.textParts ? acc.textParts.join("\n\n") : null

        // Option A (recommended): pick the largest HTML chunk (usually the real body)
        // String html = acc.htmlParts
        //         ? acc.htmlParts.max { (it ?: "").length() }
        //         : null

        // Option B (if you prefer): concatenate all HTML chunks in order
        String html = acc.htmlParts ? """<html><head><meta http-equiv="content-type" content="text/html"></head><body>${acc.htmlParts.join("\n")}</body></html>""" : null

        return [text: text, html: html, attachments: acc.attachments]
    }

    private static void walk(MimePart part, Map acc) {
        if (part == null) return

        // ---- multipart containers ----
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent()
            for (int i = 0; i < mp.count; i++) {
                walk(mp.getBodyPart(i) as MimePart, acc)
            }
            return
        }

        // ---- nested message ----
        if (part.isMimeType("message/rfc822")) {
            def nested = part.getContent()
            if (nested instanceof MimePart) walk((MimePart) nested, acc)
            return
        }

        // ---- leaf: text bodies ----
        if (part.isMimeType("text/plain")) {
            (acc.textParts as List) << safeStringContent(part)
            return
        }

        if (part.isMimeType("text/html")) {
            (acc.htmlParts as List) << stripOuterHtml(safeStringContent(part))
            return
        }

        // ---- leaf: attachments / inline parts ----
        // Treat as attachment when:
        // - disposition is ATTACHMENT or INLINE with filename
        // - OR filename exists
        // - OR content is not text/* and not multipart/*
        String disposition = part.getDisposition()
        String fn = part.getFileName()
        String ct = part.getContentType() ?: ""
        String id = part.getContentID()

        boolean looksLikeAttachment =
            fn != null ||
                disposition?.equalsIgnoreCase(MimePart.ATTACHMENT) ||
                (disposition?.equalsIgnoreCase(MimePart.INLINE) && fn != null) ||
                (!ct.toLowerCase().startsWith("text/") && !ct.toLowerCase().startsWith("multipart/"))

        if (looksLikeAttachment) {
            (acc.attachments as List) << [
                name       : fn ?: "unknown",
                contentType: ct,
                disposition: disposition,
                bytes      : part.getInputStream().bytes,
                contentId  : id // Works fine, but not all attachment has contentId
            ]
        }
    }

    private static String simpleEscapeHtml(String s) {
        return s.replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
    }

    private static String safeStringContent(MimePart part) {
        def c = part.getContent()
        if (c == null) return null
        if (c instanceof String) return (String) c
        // Sometimes JavaMail returns InputStream for weird text parts; fall back:
        try {
            return part.getInputStream().getText("UTF-8")
        } catch (ignored) {
            return c.toString()
        }
    }

    // strip outer html/head/body wrappers for quoting
    static String stripOuterHtml(String html) {
        def m = (html =~ /(?is).*<body[^>]*>(.*)<\/body>.*/)
        String strippedHtml = m.matches() ? (m[0] as List)[1] : html

        return strippedHtml.trim()
    }

    private MimeMessage sendMessage(
        String subject,
        String messageHtml,
        List attachments,
        MimeMessage original,
        Collection<InternetAddress> to,
        Collection<InternetAddress> cc,
        Collection<InternetAddress> bcc
    ) {
        def smtpSession = Session.getInstance(gmailSmtpProperties, gmailAuthenticator)
        MimeMessage message = new MimeMessage(smtpSession)

        message.setFrom(fromInternetAddress)
        message.setRecipients(Message.RecipientType.TO, to as Address[])
        if (cc) message.setRecipients(Message.RecipientType.CC, cc as Address[])
        if (bcc) message.setRecipients(Message.RecipientType.BCC, bcc as Address[])
        message.setSubject(subject ?: '', 'UTF-8')

        def messageText = Jsoup.parse(messageHtml).wholeText()
        def quotedHtmlBlock = ''
        def quotedTextBlock = ''

        if (original)  {
            message.setHeader('In-Reply-To', original.messageID)

            // Append original Message-ID to References (if present)
            List<Address> originalFrom = original.getFrom()
            List<String> refs = original?.getHeader('References')
            String refsValue = (refs != null && refs.size() > 0) ? refs.join(' ').trim() : ''
            refsValue = (refsValue ? (refsValue + ' ') : '') + original.messageID
            message.setHeader('References', refsValue)

            def originalMessage = parseMessage(original)
            String originalHtml = originalMessage['html']
            String originalText = originalMessage['text']
            def originalAttachments = originalMessage['attachments'] as List

            if (originalAttachments) attachments.addAll(originalAttachments)

            // Minimal quoting wrapper (works well in most clients)
            def sentDate = original.getSentDate()?.toLocalDateTime()?.format("EEE, d MMM, yyyy, 'at' hh:mm a")
            def receivedDate = original.getReceivedDate()?.toLocalDateTime()?.format("EEE, d MMM, yyyy, 'at' hh:mm a")
            def quoteDate = sentDate ?: receivedDate
            quotedHtmlBlock = """
                <br>
                <div class="gmail_quote gmail_quote_container">
                    <div dir="ltr" class="gmail_attr">
                    On ${quoteDate}, ${simpleEscapeHtml(originalFrom?.join(", "))} wrote:
                    <br>
                    </div>
                    <blockquote class="gmail_quote" style="margin:0px 0px 0px 0.5em; border-left:1px solid #ccc; padding-left:1em">
                    ${originalHtml ? "<div>${stripOuterHtml(originalHtml)}</div>" : simpleEscapeHtml(originalText).replaceAll(/\r?\n/, '<br>')}
                    </blockquote>
                </div>
            """
            def quotedLines = originalText.split("\\r?\\n").collect { ">" + it.trim() }.join("\n")
            quotedTextBlock = "On ${quoteDate}, ${simpleEscapeHtml(originalFrom?.join(", "))} wrote:\n${quotedLines}".trim()
        }
        // println 'attachments:'
        // attachments.each {
        //     println "name: ${it['name']}"
        //     println "disposition: ${it['disposition']}"
        //     println "contentType: ${it['contentType']}"
        // }

        messageHtml = """<div>${quotedHtmlBlock ? "${messageHtml}\n${quotedHtmlBlock}" : messageHtml}</div>""".toString()
        // println "messageHtml: $messageHtml"

        messageText = """${quotedTextBlock ? "${messageText}\n\n${quotedTextBlock}" : messageText}""".toString()
        // println "messageText: $messageText"

        // =========================================================================
        // MIME structure:
        // multipart/mixed
        //   ├─ multipart/alternative
        //   │    ├─ text/plain
        //   │    └─ multipart/related
        //   │          ├─ text/html
        //   │          └─ inline parts (Content-ID)
        //   └─ regular attachments
        // =========================================================================

        MimeMultipart mixed = new MimeMultipart("mixed")

        // alternative container
        MimeBodyPart alternativeContainer = new MimeBodyPart()
        MimeMultipart alternative = new MimeMultipart("alternative")

        // text/plain part
        MimeBodyPart textPart = new MimeBodyPart()
        textPart.setText(messageText, "UTF-8")
        alternative.addBodyPart(textPart)

        // related container for html + inline
        MimeBodyPart relatedContainer = new MimeBodyPart()
        MimeMultipart related = new MimeMultipart("related")

        // html part
        MimeBodyPart htmlPart = new MimeBodyPart()
        htmlPart.setContent(messageHtml, "text/html; charset=UTF-8")
        related.addBodyPart(htmlPart)

        attachments.findAll { it['disposition'] == 'inline' }.each { attachment ->
            String name = attachment['name']
            def bytes = attachment['bytes'] as byte[]
            String ct = attachment['contentType']

            DataSource ds = new ByteArrayDataSource(bytes, ct)
            MimeBodyPart part = new MimeBodyPart()
            part.setDataHandler(new DataHandler(ds))
            part.setFileName(MimeUtility.encodeText(name, "UTF-8", null))

            part.setDisposition(MimePart.INLINE)
            if (attachment['contentId']) part.setHeader("Content-ID", attachment['contentId'] as String)
            // some clients like this:
            part.setHeader("Content-Transfer-Encoding", "base64")

            related.addBodyPart(part)
        }

        relatedContainer.setContent(related)
        alternative.addBodyPart(relatedContainer)

        alternativeContainer.setContent(alternative)
        mixed.addBodyPart(alternativeContainer)

        attachments.findAll { it['disposition'] == 'attachment' }.each { attachment ->
            String name = attachment['name']
            def bytes = attachment['bytes'] as byte[]
            String ct = attachment['contentType']

            DataSource ds = new ByteArrayDataSource(bytes, ct)
            MimeBodyPart part = new MimeBodyPart()
            part.setDataHandler(new DataHandler(ds))
            part.setFileName(MimeUtility.encodeText(name, "UTF-8", null))

            part.setDisposition(MimePart.ATTACHMENT)
            mixed.addBodyPart(part)
        }

        message.setContent(mixed)
        message.setSentDate(new Date())

        Transport.send(message)
        println "send successfully"
        return message
    }

    MimeMessage sendNewIssueAsMessage(
        Issue newIssue,
        Collection<InternetAddress> to,
        Collection<InternetAddress> cc,
        Collection<InternetAddress> bcc
    ) {
        def getDescriptionRenderedBodyResponse = get("/rest/api/3/issue/${newIssue.id}")
            .queryString('expand', 'renderedFields')
            .queryString('fields', 'description')
            .asObject(Map)
        String renderedBody = getDescriptionRenderedBodyResponse.body['renderedFields']['description']

        def processedRenderedBody = processRenderedBody(renderedBody)
        def messageHtml = processedRenderedBody['html'] as String
        def inlineAttachments = processedRenderedBody['attachments'] as List ?: []
        // id         : attachmentId,
        // name       : fn ?: "unknown",
        // contentType: ct,
        // disposition: disposition,
        // bytes      : part.getInputStream().bytes,
        // contentId  : contentId // Works fine, only inline attachment needs contentId
        def regularAttachments = newIssue.attachments
            .findAll { it.id !in inlineAttachments.collect { it['id'] }}
            .collect { attachment -> [
                name: attachment['filename'],
                contentType: attachment['mimeType'],
                bytes: (get("/rest/api/3/attachment/content/${attachment.id}").asBinary().body as ByteArrayInputStream).bytes,
                disposition: 'attachment',
            ]} ?: []
        sendMessage(newIssue.summary, messageHtml, inlineAttachments + regularAttachments, null, to, cc, bcc)
    }

    MimeMessage sendLatestCommentAsMessage(
        Issue issue,
        Collection<InternetAddress> to,
        Collection<InternetAddress> cc,
        Collection<InternetAddress> bcc
    ) {
        // NOTE: A comment_created event is not triggered for an additional comment, which is created from an issue created
        //       from an external email. The comment seems to be just all attachments as inline attachments.
        def newestFirstComments = issue.comments.toList().asReversed()
        def previousCommentMessageId = newestFirstComments.drop(1).findResult {
            JiraUtils.instance.getEmailMessageIdCommentProperty(it.id)
        }
        def originalMessageId = previousCommentMessageId ?: JiraUtils.instance.getEmailMessageIdIssueProperty(issue.id)
        def originalMessage = getMailMessageById(originalMessageId)
        def originalMessageSubject = originalMessage.getSubject()
        def subject = originalMessageSubject ?: ''
        if (subject && !subject.toLowerCase().startsWith('re:')) {
            subject = "Re: $subject"
        }
        def getCommentRenderedBodyResponse = get("/rest/api/3/issue/${issue.id}/comment/${newestFirstComments.first().id}")
            .queryString('expand', 'renderedBody')
            .asObject(Map)
        String renderedBody = getCommentRenderedBodyResponse.body['renderedBody']
        def processedRenderedBody = processRenderedBody(renderedBody)
        def messageHtml = processedRenderedBody['html'] as String
        def inlineAttachments = processedRenderedBody['attachments'] as List
        sendMessage(subject, messageHtml, inlineAttachments, originalMessage, to, cc, bcc)
    }
}