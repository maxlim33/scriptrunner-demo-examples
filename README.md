# About This Repository

This repository contains ScriptRunner demonstration examples and is a fork of the [ScriptRunner Migration Project](https://bitbucket.org/adaptavistlabs/migration-example-project) maintained by Adaptavist.

The upstream project provides a Gradle plugin that enables programmatic deployment of ScriptRunner configurations. This fork extends that functionality with additional Gradle features tailored for my demonstration purposes.

> **Disclaimer:** The custom Gradle additions in this fork are experimental and may be rough around the edges — I'm learning as I go!

## Automated Setup and Deployment

Beyond standard ScriptRunner configuration deployment, this fork introduces a custom Gradle task group called `implementation`. Tasks within this group execute the necessary setup scripts required to prepare a complete demonstration environment.

Currently, this repository includes only one implementation example, defined in `cloud/build.gradle`:

```
./gradlew "implement-Generate offer letter dynamically as Confluence page and export as PDF"
```

This implementation performs the following actions:
1. Configures PDF export settings in a designated Confluence space.
2. Creates an offer letter template within the same Confluence space.
3. Deploys a ScriptRunner listener that generates an offer letter and attaches its PDF version to a Jira issue upon a specific workflow transition.

**Prerequisites:**
1. Configure the required parameters in `cloud/build.gradle`.
2. Configure the required parameters in `GenerateOfferLetter.groovy`.

Future iterations will aim to provide a more streamlined approach for authoring setup scripts.

---

**The following documentation is from the upstream [ScriptRunner Migration Project](https://bitbucket.org/adaptavistlabs/migration-example-project).**

---

# ScriptRunner Migration Suite Development and Deployment Tool

This sample repository provides a template for developing, testing, and deploying scripts for ScriptRunner Cloud from a
local command line and IDE.

## Try it

You can clone this using Git (or your favorite Git GUI, such as SourceTree). 
We recommend cloning to a different subdirectory for each migration you're working on. For example:

`git clone https://bitbucket.org/adaptavistlabs/migration-example-project.git migration-x`

### Install Java

You will also need to have a Java Developer Kit (JDK), version 17 or later installed to work with this project. We
recommend using [SDKMAN!](https://sdkman.io/) to manage Java installations.

## Set Up Credentials and Point to Instance

You need to specify which Jira Cloud instance the scripts should deploy to and the credentials to connect to it.

You will need to update two files to do this. 

One file will be in this repository: the gradle.properties, which will be shared with anyone who works on this project 
with you. This is where you will specify the URL that points to your Atlassian Cloud Instance.

The other file will be in a different directory, though it will also be named gradle.properties. The directory is 
the [Gradle user home directory](https://docs.gradle.org/current/userguide/directory_layout.html#dir:gradle_user_home),
which is a hidden directory inside your user home directory. Your user home directory will be in different locations, 
depending on your operating system. Assuming your username is `me`, it might be /Users/home/me on Mac OSX, 
C:\Users\me on Windows, or /home/me on Linux.

The Gradle user home directory's gradle.properties file is where you will put the credentials, which you want to keep 
private to yourself.
In that file, you will specify the Atlassian User and Token that allow you to sign in to your Atlassian Cloud Site.

We will refer to the gradle.properties file in your user home directory as `~/.gradle/gradle.properties` for the rest of
this document.  Different people working on this project will have different credentials. As such, they will each specify their own
credentials in their user home.

### Getting the credentials

Create a new API token from [your Atlassian profile](https://id.atlassian.com/manage-profile/security/api-tokens). We
recommend creating a regular API token without scopes.

**Note:** Make sure you copy this token because it will not be visible once closed. We recommend saving it in a password
manager.

**Warning:** Atlassian API tokens allow a user to act as you on any instance connected to your Atlassian account.
Be as protective of this token as you would any password.

### Updating the files

Update your user home gradle.properties file `~/.gradle/gradle.properties` with the following properties set (without the square brackets):

```properties
atlassianUser=[your Adaptavist email address]
atlassianToken=[your token from the previous step]
```

Update the gradle.properties file in this project to point to the Atlassian instance you want to work with.
```properties
targetAtlassianSite=https://your-instance-goes-here.atlassian.net
```

Keeping your credentials in your user home gradle.properties file (`~/.gradle/gradle.properties`) will allow you to
reuse them across multiple projects. Setting the target instance in the local gradle.properties file will make it easier
if you have multiple engagements in flight, each one in different directories. It will also save you from publishing
them somewhere that you shouldn't.

## Configuration and Code
The Groovy code for your listeners can go in the cloud/src/main/groovy folder. 
To help with migrations and allow a side-by-side comparison of DataCenter and Cloud scripts, your DataCenter scripts can go in the 
onprem/src/main/groovy directory.

The configuration for your Cloud scripts is defined in the cloud/src/main/resources/extensions.yaml file.

## Using an IDE

Any IDE with Gradle and Groovy support can add value to this project, but we
recommend [IntelliJ](https://www.jetbrains.com/idea/download/?section=mac). The free Community Edition supports
everything that you need to use this project.

## Automated Deployment

Scripts and their configuration can be deployed to the configured cloud instance using various Gradle tasks.

You'll need to define the configuration for each script manually in `cloud/src/main/resources/extensions.yaml`. 
Only script fields, listeners, and jobs are supported currently, though support for all features is on the way!

There are examples in there to start you off, but don't keep them unless you *actually* want those scripts in your
instance. Likewise, make sure to carefully verify any configuration you get from the Migration Agent or other AI-based 
tools. 

Once you have some configuration in extensions.yaml, the project will automatically configure Gradle tasks for each
script configuration. Tasks can either be run from the command line (eg `./gradlew cloud:deploy-all`) or from the Gradle
tool window in IntelliJ IDEA (or your IDE of choice). There are tasks to deploy scripts in bulk:

    ./gradlew cloud:deploy-all # Deploy all extensions
    ./gradlew cloud:deploy-listener-all # Deploy all listeners
    ./gradlew cloud:deploy-scriptField-all # Deploy all script fields
    ./gradlew cloud:deploy-job-all # Deploy all jobs

There will also be tasks to deploy individual scripts. This can be useful if you only want to verify your most recent
change is correct. For example, some of our pre-configured samples will generate tasks like these:

    ./gradlew "deploy-job-Create time logging issue"
    ./gradlew "deploy-scriptField-Date Difference"
    ./gradlew "deploy-listener-Add a definition of done checklist to an issue on creation"

For the Gradle task name, listeners are identified by their description. Script fields and script jobs by their name.

If there are errors in the YAML, the plugin will fail to apply and log out any errors received on any Gradle task.

## Dynamic Members for Better Autocompletion

To provide better autocompletion for scripts, we provide some context for the variables and methods that are always
imported in ScriptRunner. This includes the `logger`, your `baseUrl`, and
the [methods from Unirest](https://docs.adaptavist.com/sr4jc/latest/get-started/technical-background#unirest) which are
imported automatically into most script contexts. These are defined in the `cloud/src/main/groovy/cloud.gdsl` file,
which uses [IntelliJ IDEA's GroovyDSL Scripting Framework](https://youtrack.jetbrains.com/articles/GROOVY-A-15796912).

You may want to set up more dynamic members for a specific script to aid development.
See https://youtrack.jetbrains.com/articles/GROOVY-A-12779640 for details on how to do that via the IntelliJ UI.

You may want some of the following, depending on the context you are working in:

| Name         | Type                                             | Contexts where useful                                                 |
|--------------|--------------------------------------------------|-----------------------------------------------------------------------|
| issue        | com.atlassian.jira.rest.clientv2.model.IssueBean | Escalation Services                                                   |
| issue        | java.util.Map                                    | Workflow functions, Listeners on issue-specific events, Script Fields |
| webhookEvent | java.lang.String                                 | Listeners                                                             |
| timestamp    | java.lang.String                                 | Listeners                                                             |

# Updating the Plugin

A Gradle plugin developed by Adaptavist powers deployments in this project. We are actively working on it, so you will 
need to apply updates to get additional feature support and bugfixes as we release them.

To update, open the `settings.gradle` file in this project and edit this set of lines:

```gradle
plugins {
    id 'com.adaptavist.scriptrunner.migration-settings' version '0.0.8' // Change the version number to the latest release
}
```

You can check back in the [sample project on Bitbucket](https://bitbucket.org/adaptavistlabs/migration-example-project/src/main/settings.gradle) 
or look at the [plugin artifact in our public nexus repository](https://nexus.adaptavist.net/service/rest/repository/browse/external/com/adaptavist/scriptrunner/cloud/migration-dev-and-deployment-plugin/)
to get the latest version number of the plugin.

# Further information

For further information read the [documentation on our website](https://docs.adaptavist.com/sms/).

# Known Issues

## TypeScript Compilation Warnings

For Behaviours, the TypeScript compiler may emit some errors during compilation of the TypeScript code into JavaScript. Some of these errors may indicate problems in your script, but some can be safely ignored. 

Specifically, warnings like these are expected. We're working on better ways to suppress them.
🚧 Please pardon our progress. 🚧

```properties
../../src/main/typescript/behaviours.d.ts(388,5): error TS1038: A 'declare' modifier cannot be used in an already ambient context.
../../src/main/typescript/behaviourA.ts(14,15): error TS1378: Top-level 'await' expressions are only allowed when the 'module' option is set to 'es2022', 'esnext', 'system', 'node16', 'node18', 'node20', 'nodenext', or 'preserve', and the 'target' option is set to 'es2017' or higher.
```
