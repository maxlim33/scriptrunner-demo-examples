package com.maxlim33

import com.maxlim33.confluence.Confluence
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class SetupTask extends DefaultTask {
    @Input
    String confluenceSpaceKey

    @Input
    String templateTitle

    @Input
    String logoLink

    SetupTask() {
        group = 'implementation'
    }

    @TaskAction
    void run() {
        println "~~~~~~~~~~~~~asdf"
        println "values: $confluenceSpaceKey, $templateTitle, $logoLink"

        final atlassianUser = project.property('atlassianUser') as String
        final atlassianToken = project.property('atlassianToken') as String
        final targetAtlassianSite = project.property('targetAtlassianSite') as String

        // Initialize the Confluence API client
        final confluence = new Confluence(targetAtlassianSite, atlassianUser, atlassianToken)

        // Get the space ID for the target space
        def confluenceSpaceId = confluence.getSpaceId(confluenceSpaceKey)

        // Load resources
        def pdfHeader = Confluence.loadPdfHeader(logoLink)
        def pdfStyles = Confluence.loadPdfStyles()
        def templateAdf = Confluence.loadOfferLetterTemplate()

        // Update PDF export configuration for the space
        confluence.updatePdfExportConfiguration(
            confluenceSpaceId,
            pdfHeader,
            '',         // footer
            pdfStyles,  // style
            ''          // titlePage
        )

        // Create the Confluence template
        confluence.createTemplate(
            confluenceSpaceKey,
            templateTitle,
            JsonOutput.toJson(templateAdf)
        )

        project.logger.lifecycle("Successfully created template '${templateTitle}' in space '${confluenceSpaceKey}'")
    }
}