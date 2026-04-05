with open("app/src/main/java/org/example/memosm/model/UserModels.kt", "r") as f:
    content = f.read()

content = content.replace(
    'val memoVisibility: String? = null',
    'val memoVisibility: Visibility? = null'
)

with open("app/src/main/java/org/example/memosm/model/UserModels.kt", "w") as f:
    f.write(content)
