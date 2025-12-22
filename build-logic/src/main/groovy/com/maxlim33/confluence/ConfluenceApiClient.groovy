package com.maxlim33.confluence

import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import groovy.json.JsonSlurper
import kong.unirest.Unirest
import kong.unirest.jackson.JacksonObjectMapper

/**
 * Utility class for interacting with Confluence Cloud APIs.
 * Handles authentication, resource loading, and API calls.
 */
class ConfluenceApiClient {

    private final String baseUrl
    private final String accountEmail
    private final String accountToken

    /**
     * Creates a new ConfluenceApiClient with the specified credentials.
     *
     * @param baseUrl The base URL of the Atlassian site (e.g., https://your-site.atlassian.net)
     * @param accountEmail The Atlassian account email
     * @param accountToken The Atlassian API token
     */
    ConfluenceApiClient(String baseUrl, String accountEmail, String accountToken) {
        this.baseUrl = baseUrl
        this.accountEmail = accountEmail
        this.accountToken = accountToken
        configureUnirest()
    }

    /**
     * Configures Unirest HTTP client with authentication and JSON handling.
     */
    private void configureUnirest() {
        Unirest.config().with {
            setDefaultBasicAuth(accountEmail, accountToken)
            defaultBaseUrl(baseUrl)
            setObjectMapper(new JacksonObjectMapper(
                new ObjectMapper().registerModule(
                    // Handle GString serialization for Groovy interpolated strings
                    new SimpleModule().addSerializer(GString, ToStringSerializer.instance as JsonSerializer)
                )
            ))
        }
    }

    /**
     * Loads a resource file from the classpath.
     *
     * @param resourcePath The path to the resource (e.g., "com/maxlim33/confluence/templates/offer-letter-adf.json")
     * @return The resource content as a String
     */
    static String loadResource(String resourcePath) {
        def inputStream = ConfluenceApiClient.class.classLoader.getResourceAsStream(resourcePath)
        if (!inputStream) {
            throw new IllegalArgumentException("Resource not found: ${resourcePath}")
        }
        return inputStream.text
    }

    /**
     * Loads a JSON resource and parses it into a Map/List structure.
     *
     * @param resourcePath The path to the JSON resource
     * @return The parsed JSON as a Map or List
     */
    static Object loadJsonResource(String resourcePath) {
        def content = loadResource(resourcePath)
        return new JsonSlurper().parseText(content)
    }

    /**
     * Loads a GraphQL query from the resources folder.
     *
     * @param queryName The name of the query file (without .graphql extension)
     * @return The GraphQL query string
     */
    static String loadGraphQLQuery(String queryName) {
        return loadResource("com/maxlim33/confluence/graphql/${queryName}.graphql")
    }

    /**
     * Loads the ADF template for offer letters.
     *
     * @return The ADF template as a Map structure
     */
    static Map loadOfferLetterTemplate() {
        return loadJsonResource("com/maxlim33/confluence/templates/offer-letter-adf.json") as Map
    }

    /**
     * Loads PDF styles from resources.
     *
     * @return The CSS styles as a String
     */
    static String loadPdfStyles() {
        return loadResource("com/maxlim33/confluence/pdf/styles.css")
    }

    /**
     * Loads PDF header HTML from resources.
     *
     * @param logoLink The URL to the logo image to inject into the header
     * @return The header HTML with the logo link substituted
     */
    static String loadPdfHeader(String logoLink) {
        def template = loadResource("com/maxlim33/confluence/pdf/header.html")
        return template.replace('{{LOGO_LINK}}', logoLink)
    }

    /**
     * Gets the space ID for a given space key.
     *
     * @param spaceKey The Confluence space key
     * @return The space ID
     */
    String getSpaceId(String spaceKey) {
        def response = Unirest.get("/wiki/api/v2/spaces")
            .queryString("keys", spaceKey)
            .asObject(Map)
        def results = response.body['results'] as List
        if (!results) {
            throw new IllegalArgumentException("Space not found: ${spaceKey}")
        }
        return (results.first() as Map)['id'] as String
    }

    /**
     * Executes a GraphQL mutation against the Confluence API.
     *
     * @param operationName The name of the GraphQL operation
     * @param query The GraphQL query string
     * @param variables The variables to pass to the query
     * @return The response body as a Map
     */
    Map executeGraphQL(String operationName, String query, Map variables) {
        def response = Unirest.post("/cgraphql")
            .header('Content-Type', 'application/json')
            .body([
                operationName: operationName,
                query: query,
                variables: variables
            ])
            .asObject(Map)
        
        def body = response.body
        if (body['errors']) {
            throw new RuntimeException("GraphQL error: ${body['errors']}")
        }
        return body
    }

    /**
     * Updates the PDF export configuration for a space.
     *
     * @param spaceId The Confluence space ID
     * @param header The HTML header content
     * @param footer The HTML footer content
     * @param style The CSS styles
     * @param titlePage The title page content
     */
    void updatePdfExportConfiguration(String spaceId, String header, String footer = '', String style = '', String titlePage = '') {
        def query = loadGraphQLQuery('update-pdf-export')
        executeGraphQL('PdfExportConfigurationMutation', query, [
            input: [
                footer: footer,
                header: header,
                spaceId: spaceId,
                style: style,
                titlePage: titlePage
            ]
        ])
    }

    /**
     * Creates a Confluence template.
     *
     * @param spaceKey The space key where the template will be created
     * @param templateName The name of the template
     * @param adfContent The ADF content as a JSON string
     * @param description Optional description for the template
     * @param labels Optional labels for the template
     * @return The response from the API
     */
    Map createTemplate(String spaceKey, String templateName, String adfContent, String description = '', List labels = []) {
        def query = loadGraphQLQuery('create-template')
        return executeGraphQL('CreateTemplateMutation', query, [
            contentTemplate: [
                body: [
                    atlasDocFormat: [
                        representation: "atlas_doc_format",
                        value: adfContent
                    ]
                ],
                description: description,
                labels: labels,
                name: templateName,
                space: [
                    key: spaceKey
                ],
                templateType: "PAGE"
            ]
        ])
    }
}

