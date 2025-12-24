package com.maxlim33

import com.maxlim33.confluence.Confluence
import groovy.json.JsonOutput
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin that sets up implementation tasks for Confluence integration.
 */
class ImplementationSetupsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        final atlassianUserProvider = project.provider { project.property('atlassianUser') as String }
        final atlassianTokenProvider = project.provider { project.property('atlassianToken') as String }
        final targetAtlassianSiteProvider = project.provider { project.property('targetAtlassianSite') as String }

        project.tasks.register('implement-Dynamic Confluence page generation from Jira and attach PDF') { task ->
            task.group = 'implementation'
            task.finalizedBy 'deploy-listener-Generate letter as Confluence page on transition'

            task.doLast {
                final atlassianUser = atlassianUserProvider.get()
                final atlassianToken = atlassianTokenProvider.get()
                final targetAtlassianSite= targetAtlassianSiteProvider.get()

                /********** User Input Start **********/
                final CONFLUENCE_SPACE_KEY = 'HR'
                final TEMPLATE_TITLE = 'ACME offer letter'
                final LOGO_LINK = "$targetAtlassianSite/wiki/download/attachments/2392196/spot-trophy.png?api=v2"
                /********** User Input End **********/

                // Initialize the Confluence API client
                def confluenceClient = new Confluence(targetAtlassianSite, atlassianUser, atlassianToken)

                // Get the space ID for the target space
                def confluenceSpaceId = confluenceClient.getSpaceId(CONFLUENCE_SPACE_KEY)

                // Load resources
                def pdfHeader = Confluence.loadPdfHeader(LOGO_LINK)
                def pdfStyles = Confluence.loadPdfStyles()
                def templateAdf = Confluence.loadOfferLetterTemplate()

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

