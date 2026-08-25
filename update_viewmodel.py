with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add _selectedValidatedOrder to MainViewModel
state_insertion = '''    private val _manualTrackedOrders = MutableStateFlow<List<SmartTrackedOrder>>(emptyList())
    val manualTrackedOrders: StateFlow<List<SmartTrackedOrder>> = _manualTrackedOrders.asStateFlow()
    
    private val _selectedValidatedOrder = MutableStateFlow<SmartTrackedOrder?>(null)
    val selectedValidatedOrder: StateFlow<SmartTrackedOrder?> = _selectedValidatedOrder.asStateFlow()'''

content = content.replace(
    '    private val _manualTrackedOrders = MutableStateFlow<List<SmartTrackedOrder>>(emptyList())\n    val manualTrackedOrders: StateFlow<List<SmartTrackedOrder>> = _manualTrackedOrders.asStateFlow()',
    state_insertion
)

# Add clearCurrentPoints() and removeValidatedOrder() and selectValidatedOrder()
methods_insertion = '''    fun clearCurrentSmartTrack() {
        _currentUserPoints.value = emptyList()
        _currentSmartPath.value = emptyList()
    }
    
    fun clearCurrentPoints() {
        _currentUserPoints.value = emptyList()
        _currentSmartPath.value = emptyList()
    }
    
    fun selectValidatedOrder(order: SmartTrackedOrder?) {
        _selectedValidatedOrder.value = order
    }
    
    fun removeValidatedOrder(order: SmartTrackedOrder) {
        _manualTrackedOrders.value = _manualTrackedOrders.value.filter { it != order }
        if (_selectedValidatedOrder.value == order) {
            _selectedValidatedOrder.value = null
        }
    }'''

content = content.replace(
    '''    fun clearCurrentSmartTrack() {
        _currentUserPoints.value = emptyList()
        _currentSmartPath.value = emptyList()
    }''',
    methods_insertion
)

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("MainViewModel updated.")
