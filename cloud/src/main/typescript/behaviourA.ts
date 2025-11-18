console.warn("Running")
// Access the context for the UI Modification

const context = await getContext()


// Get the current issue key

const issueKey = context.extension.issue.key


// Get the current issue object

const issue = await makeRequest("/rest/api/2/issue/" + issueKey)


// Log out to the browser some field values to show how to get them from the issue.

// Note you can access any field on an issue through the fields property
const descriptionField = getFieldById("description")
const descriptionValue = descriptionField.getValue()

if (!descriptionValue) {
    descriptionField.setValue("Oh, hello there. Welcome to creating issues.")
}

console.log("Issue Field Values:")

console.log("Status field: ", issue.body.fields.status)

console.log("Assignee field: ", issue.body.fields.assignee)
