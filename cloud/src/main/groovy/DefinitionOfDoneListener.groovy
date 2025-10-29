import groovy.json.JsonOutput

def adfBody = [
        "version": 1,
        "type": "doc",
        "content": [
                [
                        "type": "paragraph",
                        "content": [
                                [
                                        "type": "text",
                                        "text": "Definition Of Done Checklist:",
                                ],
                        ]
                ],
                [
                        "type": "rule"
                ],
                [
                        "type": "paragraph",
                        "content": [
                                [
                                        "type": "text",
                                        "text": "Checklist:",
                                        "marks": [
                                                [
                                                        "type": "strong"
                                                ]
                                        ]
                                ]
                        ]
                ],
                [
                        "type": "bulletList",
                        "content": [
                                [
                                        "type": "listItem",
                                        "content": [
                                                [
                                                        "type": "paragraph",
                                                        "content": [
                                                                [
                                                                        "type": "text",
                                                                        "text": "All Requirements on Ticket Complete."
                                                                ]
                                                        ]
                                                ]
                                        ]
                                ],
                                [
                                        "type": "listItem",
                                        "content": [
                                                [
                                                        "type": "paragraph",
                                                        "content": [
                                                                [
                                                                        "type": "text",
                                                                        "text": "Pull Request reviewed by at least 2 developers."
                                                                ]
                                                        ]
                                                ]
                                        ]
                                ],
                                [
                                        "type": "listItem",
                                        "content": [
                                                [
                                                        "type": "paragraph",
                                                        "content": [
                                                                [
                                                                        "type": "text",
                                                                        "text": "Thorough spot checking done and video attached to PR covering relevant edge cases."
                                                                ],
                                                        ],
                                                ]
                                        ]
                                ],
                                [
                                        "type": "listItem",
                                        "content": [
                                                [
                                                        "type": "paragraph",
                                                        "content": [
                                                                [
                                                                        "type": "text",
                                                                        "text": "Unit tests all updated or added where necessary and pass."
                                                                ]
                                                        ]
                                                ]
                                        ]
                                ],
                                [
                                        "type": "listItem",
                                        "content": [
                                                [
                                                        "type": "paragraph",
                                                        "content": [
                                                                [
                                                                        "type": "text",
                                                                        "text": "Appropriate Monitoring or Error detection added."
                                                                ]
                                                        ]
                                                ]
                                        ]
                                ],
                                [
                                        "type": "listItem",
                                        "content": [
                                                [
                                                        "type": "paragraph",
                                                        "content": [
                                                                [
                                                                        "type": "text",
                                                                        "text": "Change fully regression tested for older code/config versions."
                                                                ]
                                                        ]
                                                ]
                                        ]
                                ],
                                [
                                        "type": "listItem",
                                        "content": [
                                                [
                                                        "type": "paragraph",
                                                        "content": [
                                                                [
                                                                        "type": "text",
                                                                        "text": "Release order considered."
                                                                ]
                                                        ]
                                                ]
                                        ]
                                ],
                                [
                                        "type": "listItem",
                                        "content": [
                                                [
                                                        "type": "paragraph",
                                                        "content": [
                                                                [
                                                                        "type": "text",
                                                                        "text": "Documentation updated."
                                                                ]
                                                        ]
                                                ]
                                        ]
                                ]
                        ]
                ],
                [
                        "type": "rule"
                ]
        ]
]

def commentBody = [
        body: [
                type: "doc",
                version: 1,
                content: adfBody.content
        ]
]

def addComment = post("/rest/api/3/issue/${issue.key}/comment")
        .header("Content-Type", "application/json")
        .body(JsonOutput.toJson(commentBody))
        .asJson()

assert addComment.status == 201
