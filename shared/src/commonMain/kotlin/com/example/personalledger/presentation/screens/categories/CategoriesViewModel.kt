package com.example.personalledger.presentation.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.repository.DomainException
import com.example.personalledger.domain.usecase.DeleteCategoryUseCase
import com.example.personalledger.domain.usecase.ObserveCategoriesUseCase
import com.example.personalledger.domain.usecase.SaveCategoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryEditorState(
    val visible: Boolean = false,
    val editing: Category? = null,
    val nameInput: String = "",
    val editorType: TransactionType = TransactionType.EXPENSE,
    val nameError: String? = null,
    val saving: Boolean = false,
)

data class CategoriesUiState(
    val typeFilter: TransactionType? = null,
    val items: List<Category> = emptyList(),
    val editor: CategoryEditorState = CategoryEditorState(),
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel(
    private val observeCategories: ObserveCategoriesUseCase,
    private val saveCategory: SaveCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : ViewModel() {
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val editor = MutableStateFlow(CategoryEditorState())
    private val message = MutableStateFlow<String?>(null)

    private val items = typeFilter.flatMapLatest { observeCategories(it) }

    val uiState: StateFlow<CategoriesUiState> = combine(
        items,
        typeFilter,
        editor,
        message,
    ) { list, filter, ed, msg ->
        CategoriesUiState(
            items = list,
            typeFilter = filter,
            editor = ed,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun onFilterChange(type: TransactionType?) {
        typeFilter.value = type
    }

    fun openCreate() {
        editor.value = CategoryEditorState(
            visible = true,
            editorType = typeFilter.value ?: TransactionType.EXPENSE,
        )
    }

    fun openEdit(category: Category) {
        editor.value = CategoryEditorState(
            visible = true,
            editing = category,
            nameInput = category.name,
            editorType = category.type,
        )
    }

    fun closeEditor() {
        editor.update { it.copy(visible = false) }
    }

    fun onNameChange(value: String) {
        editor.update { it.copy(nameInput = value, nameError = null) }
    }

    fun onEditorTypeChange(type: TransactionType) {
        editor.update { state ->
            if (state.editing == null) state.copy(editorType = type) else state
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun save() {
        val current = editor.value
        if (current.saving) return
        editor.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                saveCategory(
                    id = current.editing?.id,
                    name = current.nameInput,
                    type = current.editing?.type ?: current.editorType,
                    iconKey = current.editing?.iconKey.orEmpty(),
                )
                editor.value = CategoryEditorState(visible = false)
            } catch (e: DomainException.Validation) {
                editor.update { it.copy(saving = false, nameError = e.message) }
            } catch (e: Exception) {
                editor.update { it.copy(saving = false) }
                message.value = e.message
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                deleteCategory(id)
            } catch (e: DomainException) {
                message.value = e.message
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }
}
