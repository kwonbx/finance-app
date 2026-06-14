package com.example.we_spend

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ShoppingViewModel(
    private val shoppingRepository: ShoppingRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    var lists by mutableStateOf<List<ShoppingList>>(emptyList())
        private set
    var currentList by mutableStateOf<ShoppingList?>(null)
        private set
    var items by mutableStateOf<List<ShoppingItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var familyId by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")
        private set

    val filteredLists: List<ShoppingList>
        get() {
            if (searchQuery.isBlank()) return lists
            return lists.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.shopName.contains(searchQuery, ignoreCase = true) 
            }
        }

    init {
        loadData()
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
    }

    fun clearError() {
        errorMessage = null
    }

    fun loadData() {
        viewModelScope.launch {
            loadDataInternal()
        }
    }

    private suspend fun loadDataInternal() {
        isLoading = true
        try {
            val user = userRepository.getUserProfile()
            familyId = if (user?.familyId.isNullOrBlank()) null else user?.familyId
            
            Log.d("ShoppingVM", "Loading data for familyId: $familyId")
            val fetchedLists = shoppingRepository.getLists(familyId)
            lists = fetchedLists
            
            if (currentList != null) {
                val stillExists = lists.find { it.id == currentList!!.id }
                if (stillExists != null) {
                    currentList = stillExists
                    loadItemsInternal()
                } else {
                    currentList = null
                    items = emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("ShoppingVM", "Error loading data", e)
            errorMessage = "Błąd ładowania danych: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    fun selectList(list: ShoppingList) {
        viewModelScope.launch {
            selectListInternal(list)
        }
    }

    fun unselectList() {
        currentList = null
        items = emptyList()
    }

    private suspend fun selectListInternal(list: ShoppingList) {
        currentList = list
        isLoading = true
        try {
            items = shoppingRepository.getItems(list.id, list.familyId)
        } catch (e: Exception) {
            errorMessage = "Błąd ładowania produktów: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    fun addList(name: String, shopName: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                val user = userRepository.getUserProfile()
                val currentFamilyId = if (user?.familyId.isNullOrBlank()) null else user?.familyId
                familyId = currentFamilyId
                
                Log.d("ShoppingVM", "Adding list '$name' at '$shopName' for familyId: $currentFamilyId")
                val newList = ShoppingList(name = name, shopName = shopName, familyId = currentFamilyId)
                shoppingRepository.addList(newList)
                
                // Refresh lists
                val updatedLists = shoppingRepository.getLists(currentFamilyId)
                lists = updatedLists
                
                // Automatically select the new list
                val createdList = updatedLists.find { it.name == name }
                if (createdList != null) {
                    selectListInternal(createdList)
                }
            } catch (e: Exception) {
                Log.e("ShoppingVM", "Error adding list", e)
                errorMessage = "Nie udało się dodać listy: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun updateList(list: ShoppingList, newName: String, newShopName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                val updatedList = list.copy(name = newName, shopName = newShopName)
                shoppingRepository.updateList(updatedList)
                loadDataInternal()
            } catch (e: Exception) {
                errorMessage = "Nie udało się zaktualizować listy: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteList(list: ShoppingList) {
        viewModelScope.launch {
            isLoading = true
            try {
                shoppingRepository.deleteList(list)
                if (currentList?.id == list.id) {
                    currentList = null
                    items = emptyList()
                }
                loadDataInternal()
            } catch (e: Exception) {
                errorMessage = "Nie udało się usunąć listy: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val categories = listOf("Jedzenie", "Transport", "Rozrywka", "Zdrowie", "Rachunki", "Inne")

    fun addItem(name: String, quantity: String, count: Int, price: Double, category: String = "Inne") {
        val list = currentList ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newItem = ShoppingItem(
                    listId = list.id,
                    name = name,
                    quantity = quantity,
                    count = count,
                    price = price,
                    category = category,
                    familyId = list.familyId
                )
                shoppingRepository.addItem(newItem)
                loadItemsInternal()
            } catch (e: Exception) {
                errorMessage = "Nie udało się dodać produktu: ${e.message}"
            }
        }
    }

    fun updateItem(item: ShoppingItem, newName: String, newQuantity: String, newCount: Int, newPrice: Double, newCategory: String) {
        viewModelScope.launch {
            try {
                val updatedItem = item.copy(name = newName, quantity = newQuantity, count = newCount, price = newPrice, category = newCategory)
                shoppingRepository.updateItem(updatedItem)
                loadItemsInternal()
            } catch (e: Exception) {
                errorMessage = "Nie udało się zaktualizować produktu: ${e.message}"
            }
        }
    }

    fun toggleItemChecked(item: ShoppingItem) {
        val newCheckedState = !item.isChecked
        val updatedItem = item.copy(isChecked = newCheckedState)
        
        // Optimistic update
        items = items.map { 
            if (it.id == item.id) updatedItem else it 
        }
        
        viewModelScope.launch {
            try {
                shoppingRepository.updateItem(updatedItem)
            } catch (e: Exception) {
                Log.e("ShoppingVM", "Error toggling item", e)
                errorMessage = "Błąd: ${e.message}"
                loadItemsInternal() // Rollback on error
            }
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch {
            try {
                shoppingRepository.deleteItem(item)
                loadItemsInternal()
            } catch (e: Exception) {
                errorMessage = "Nie udało się usunąć produktu: ${e.message}"
            }
        }
    }

    var priceHistory by mutableStateOf<List<ShoppingItem>>(emptyList())
        private set

    fun loadPriceHistory(itemName: String) {
        viewModelScope.launch {
            try {
                priceHistory = shoppingRepository.getItemPriceHistory(itemName, familyId)
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    private suspend fun loadItemsInternal() {
        val list = currentList ?: return
        try {
            items = shoppingRepository.getItems(list.id, list.familyId)
        } catch (e: Exception) {
            errorMessage = "Błąd ładowania produktów: ${e.message}"
        }
    }

    class Factory(
        private val shoppingRepository: ShoppingRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ShoppingViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ShoppingViewModel(shoppingRepository, userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
