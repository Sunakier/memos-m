with open("app/src/main/java/org/example/memosm/model/MemosModels.kt", "r") as f:
    content = f.read()

content = content.replace(
    '@SerializedName("content") val content: String? = null,',
    'val content: String,'
)

with open("app/src/main/java/org/example/memosm/model/MemosModels.kt", "w") as f:
    f.write(content)
