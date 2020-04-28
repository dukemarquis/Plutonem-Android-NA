package com.plutonem.ui.nemur.adapters

interface OnSuggestionClickListener {
    fun onSuggestionClicked(query: String?)
}

interface OnSuggestionDeleteClickListener {
    fun onDeleteClicked(query: String?)
}

interface OnSuggestionClearClickListener {
    fun onClearClicked()
}