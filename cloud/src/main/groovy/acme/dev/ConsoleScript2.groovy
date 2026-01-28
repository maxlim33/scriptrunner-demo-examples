import com.atlassian.jira.component.ComponentAccessor

def projectManager = ComponentAccessor.projectManager
def projects = projectManager.projectObjects

log.warn("Another console script: found ${projects.size()} projects")
