package com.maxlim33

import com.maxlim33.confluence.ConfluenceApiClient
import groovy.json.JsonOutput
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin that sets up implementation tasks for Confluence integration.
 */
class ImplementationSetupsPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.tasks.register('implement-Dynamic Confluence page generation from Jira and attach PDF') { task ->
            task.group = 'implementation'
            task.finalizedBy 'deploy-listener-Generate letter as Confluence page on transition'

            task.doLast {
                /********** Configuration **********/
                final String ACCOUNT_EMAIL = project.property('atlassianUser')
                final String ACCOUNT_TOKEN = project.property('atlassianToken')
                final String BASE_URL = project.property('targetAtlassianSite')

                /********** User Input Start **********/
                final CONFLUENCE_SPACE_KEY = 'HR'
                final TEMPLATE_TITLE = 'ACME offer letter'
                final LOGO_LINK = "$BASE_URL/wiki/download/attachments/2392196/spot-trophy.png?api=v2"
                /********** User Input End **********/

                // Initialize the Confluence API client
                def confluenceClient = new ConfluenceApiClient(BASE_URL, ACCOUNT_EMAIL, ACCOUNT_TOKEN)

                // Get the space ID for the target space
                def confluenceSpaceId = confluenceClient.getSpaceId(CONFLUENCE_SPACE_KEY)

                // Load resources
                def pdfHeader = ConfluenceApiClient.loadPdfHeader(LOGO_LINK)
                def pdfStyles = ConfluenceApiClient.loadPdfStyles()
                def templateAdf = ConfluenceApiClient.loadOfferLetterTemplate()

                // Update PDF export configuration for the space
                confluenceClient.updatePdfExportConfiguration(
                    confluenceSpaceId,
                    pdfHeader,
                    '',         // footer
                    pdfStyles,  // style
                    ''          // titlePage
                )

                // Create the Confluence template
                confluenceClient.createTemplate(
                    CONFLUENCE_SPACE_KEY,
                    TEMPLATE_TITLE,
                    JsonOutput.toJson(templateAdf)
                )

                project.logger.lifecycle("Successfully created template '${TEMPLATE_TITLE}' in space '${CONFLUENCE_SPACE_KEY}'")
            }
        }
    }
}

