package org.example.memosm.ui.component.item.markdown

fun toggleCheckbox(content: String, start: Int, end: Int, checked: Boolean): String {
    // Expected content at range [start, end] is "[ ]" or "[x]"
    var newStatus = if (checked) "[x]" else "[ ]"

    // Ensure there is a space after the checkbox.
    // GFM requires a space after the checkbox for it to be valid.
    // If the parser included the space in the range, or if it's missing, we add it back.
    if (end < content.length && content[end] != ' ') {
        newStatus += " "
    }

    return content.replaceRange(start, end, newStatus)
}
