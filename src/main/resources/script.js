const expenses = [];

function addExpense() {
    const amountInput = document.getElementById('amount');
    const categoryInput = document.getElementById('category');
    const amount = parseFloat(amountInput.value);
    const category = categoryInput.value.trim();

    if (amount > 0 && category) {
        expenses.push({ amount, category });
        amountInput.value = '';
        categoryInput.value = '';
    } else {
        alert('Amount Invalid');
    }
}

function showTotalSpending() {
    const total = expenses.reduce((sum, expense) => sum + expense.amount, 0);
    document.getElementById('total-display').textContent = total.toFixed(2);
}

function showSpendingByCategory() {
    const categoryTotals = expenses.reduce((totals, expense) => {
        totals[expense.category] = (totals[expense.category] || 0) + expense.amount;
        return totals;
    }, {});

    const categoryDisplay = document.getElementById('category-display');
    categoryDisplay.innerHTML = '';
    for (const category in categoryTotals) {
        const listItem = document.createElement('li');
        listItem.textContent = `${category}: $${categoryTotals[category].toFixed(2)}`;
        categoryDisplay.appendChild(listItem);
    }
}
