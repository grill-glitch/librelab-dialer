package org.librelab.dialer.ui.speeddial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librelab.dialer.data.contacts.ContactsRepository
import org.librelab.dialer.domain.model.Contact
import javax.inject.Inject

/**
 * SpeedDialViewModel — favorites + suggested contacts.
 * Mirrors SpeedDialFragment.java — fetches:
 *  - Starred (favorited) contacts
 *  - Frequent contacts (Suggestions)
 */
@HiltViewModel
class SpeedDialViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<Contact>>(emptyList())
    val favorites: StateFlow<List<Contact>> = _favorites.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Contact>>(emptyList())
    val suggestions: StateFlow<List<Contact>> = _suggestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val favs = contactsRepository.getFavoriteContacts()
                _favorites.value = favs
                _suggestions.value = emptyList() // suggestions require STREQUENT — future enhancement
            } finally {
                _isLoading.value = false
            }
        }
    }
}