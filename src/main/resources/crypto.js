let wallets = [];

document.addEventListener('DOMContentLoaded', function() {
    loadWallets();

    document.getElementById('add-wallet-form').addEventListener('submit', function(e) {
        e.preventDefault();
        addWallet();
    });
});

function showAddWalletDialog() {
    document.getElementById('add-wallet-dialog').style.display = 'block';
}

function closeAddWalletDialog() {
    document.getElementById('add-wallet-dialog').style.display = 'none';
}

function loadWallets() {
    fetch('/api/wallets')
        .then(response => response.json())
        .then(data => {
            wallets = data;
            displayWallets();
        })
        .catch(error => console.error('Error:', error));
}

function displayWallets() {
    const container = document.getElementById('wallets-container');
    container.innerHTML = '';

    wallets.forEach(wallet => {
        const card = createWalletCard(wallet);
        container.appendChild(card);
    });
}

function createWalletCard(wallet) {
    const card = document.createElement('div');
    card.className = 'wallet-card';

    const content = `
        <h3>${wallet.label}</h3>
        <p>Type: ${wallet.cryptoType}</p>
        <p>Balance: ${wallet.balance.toFixed(8)} ${wallet.cryptoType}</p>
        <p>Value: $${wallet.value.toFixed(2)}</p>
        <p>24h Change: ${wallet.change24h.toFixed(2)}%</p>
        <div class="transaction-list">
            <h4>Recent Transactions</h4>
            ${createTransactionsList(wallet.transactions)}
        </div>
        <button onclick="refreshWallet('${wallet.address}')" class="btn">Refresh</button>
    `;

    card.innerHTML = content;
    return card;
}

function createTransactionsList(transactions) {
    if (!transactions || transactions.length === 0) {
        return '<p>No recent transactions</p>';
    }

    return `
        <ul>
            ${transactions.map(tx => `
                <li class="transaction ${tx.type.toLowerCase()}">
                    <div class="tx-type">${tx.type}</div>
                    <div class="tx-amount">${tx.amount.toFixed(8)}</div>
                    <div class="tx-status">${tx.status}</div>
                    <div class="tx-time">${new Date(tx.timestamp).toLocaleString()}</div>
                </li>
            `).join('')}
        </ul>
    `;
}

function addWallet() {
    const label = document.getElementById('wallet-label').value;
    const address = document.getElementById('wallet-address').value;
    const cryptoType = document.getElementById('crypto-type').value;

    fetch('/api/wallets', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            label: label,
            address: address,
            cryptoType: cryptoType
        })
    })
        .then(response => response.json())
        .then(data => {
            wallets.push(data);
            displayWallets();
            closeAddWalletDialog();
        })
        .catch(error => console.error('Error:', error));
}

function refreshWallet(address) {
    fetch(`/api/wallets/${address}/refresh`, {
        method: 'POST'
    })
        .then(response => response.json())
        .then(updatedWallet => {
            const index = wallets.findIndex(w => w.address === address);
            if (index !== -1) {
                wallets[index] = updatedWallet;
                displayWallets();
            }
        })
        .catch(error => console.error('Error:', error));
}
