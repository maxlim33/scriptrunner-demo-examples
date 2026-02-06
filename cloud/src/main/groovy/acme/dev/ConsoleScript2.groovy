def myself = Users.getLoggedInUser()

def projectKey = 'TP'
def roleId = '10002'

def result = post("rest/api/3/project/${projectKey}/role/${roleId}")
        .header('Content-Type', 'application/json')
        .body([
                "user": [myself.accountId]
        ])
        .asObject(Map)

result.body