def now = Calendar.getInstance()
def version = String.format("%d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
post("/rest/api/2/version")
        .header('Content-Type', 'application/json')
        .body([
                name       : "Version ${version}",
                archived   : false,
                released   : true,
                releaseDate: version,
                project    : "EXAMPLE"
        ])
        .asString()
