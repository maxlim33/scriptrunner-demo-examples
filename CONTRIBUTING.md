If you are wanting to contribute, there are a few ways to get involved:

If you want to request features or make suggestions for the project, open a ticket on our 
[support portal](https://the-adaptavist-group-support.atlassian.net/servicedesk/customer/portal/1069Support).

# For Folks Contributing to the Gradle Plugin
The Gradle plugin is published from a private repository at Adaptavist.
If you have access to that repository, you can switch to using it by applying the patch in this repository.

`git apply test_local_version.patch`

The patch assumes that you have the private repository cloned to a sibling directory to this example project, but you
can adjust the line in settings.gradle to match the path to where you have that cloned:
`includeBuild('../sr-for-connect/migration-dev-and-deployment-plugin')` 

With that, you will be able to try out any changes you make to the plugin.
