
fun toggleCheckbox(content: String, start: Int, end: Int, checked: Boolean): String {
    // Expected content at range [start, end] is "[ ]" or "[x]"
    val newStatus = if (checked) "[x]" else "[ ]"
    return content.replaceRange(start, end, newStatus)
}
