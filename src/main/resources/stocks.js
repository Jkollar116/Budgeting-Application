
document.addEventListener('DOMContentLoaded', function() {
    // Initialize the tabs functionality
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            // Get the parent tabs container
            const tabsContainer = this.parentElement;

            // Remove active class from all tabs in this container
            tabsContainer.querySelectorAll('.tab').forEach(t => {
                t.classList.remove('active');
            });

            // Add active class to clicked tab
            this.classList.add('active');

            // Get the tab content id from data attribute
            const tabContentId = this.getAttribute('data-tab');

            // Hide all tab contents that are siblings to this tab's contents
            const tabContents = document.querySelectorAll('.tab-content');
            tabContents.forEach(content => {
                if (content.id === tabContentId) {
                    content.classList.add('active');
                } else if (content.parentElement === this.parentElement.nextElementSibling) {
                    content.classList.remove('active');
                }
            });
        });
    });

    // Initialize timeframe selector
    const timeframes = document.querySelectorAll('.timeframe');
    timeframes.forEach(btn => {
        btn.addEventListener('click', function() {
            timeframes.forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            // Here you would normally fetch and update the chart
            fetchStockHistory(currentSymbol, this.getAttribute('data-timeframe'));
        });
    });

    // API URLs - use our backend API instead of direct Alpha Vantage calls
    const API_BASE = '/api/stocks'; 

    // Current selected stock
    let currentSymbol = '';
    let currentStockDetails = null;

    // Fetch account info
    function fetchAccountInfo() {
        showLoading('account-overview');
        fetch(`${API_BASE}/account`)
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to fetch account info');
                }
                return response.json();
            })
            .then(data => {
                // Update account overview
                document.getElementById('account-value').textContent = formatCurrency(parseFloat(data.portfolio_value));
                document.getElementById('buying-power').textContent = formatCurrency(parseFloat(data.buying_power));
                document.getElementById('cash-balance').textContent = formatCurrency(parseFloat(data.cash));

                // Calculate unrealized P&L if available
                if (data.equity && data.last_equity) {
                    const equity = parseFloat(data.equity);
                    const lastEquity = parseFloat(data.last_equity);
                    const pl = equity - lastEquity;
                    const plPercent = (pl / lastEquity) * 100;

                    const plElement = document.getElementById('unrealized-pl');
                    plElement.textContent = `${formatCurrency(pl)} (${plPercent.toFixed(2)}%)`;
                    plElement.className = pl >= 0 ? 'profit' : 'loss';
                }

                hideLoading('account-overview');
            })
            .catch(error => {
                console.error('Error fetching account info:', error);
                hideLoading('account-overview');
                showError('account-overview', 'Failed to load account info. Please try again.');
            });
    }

    // Fetch portfolio positions
    function fetchPortfolio() {
        showLoading('portfolio-card');
        hideError('portfolio-card');

        fetch(`${API_BASE}/portfolio`)
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to fetch portfolio');
                }
                return response.json();
            })
            .then(data => {
                const positions = data.positions || [];
                const positionsTable = document.getElementById('positions-table');
                const positionsBody = document.getElementById('positions-body');
                const noPositionsMessage = document.getElementById('no-positions-message');

                // Clear the table
                positionsBody.innerHTML = '';

                if (positions.length === 0) {
                    positionsTable.style.display = 'none';
                    noPositionsMessage.style.display = 'block';
                } else {
                    positionsTable.style.display = 'table';
                    noPositionsMessage.style.display = 'none';

                    // Add each position to the table
                    positions.forEach(position => {
                        const symbol = position.symbol;
                        const quantity = position.qty;
                        const avgPrice = parseFloat(position.avg_entry_price);
                        const currentPrice = parseFloat(position.current_price);
                        const marketValue = parseFloat(position.market_value);
                        const unrealizedPL = parseFloat(position.unrealized_pl);
                        const unrealizedPLPercent = parseFloat(position.unrealized_plpc);

                        const row = document.createElement('tr');

                        // Format P&L with color
                        const plClass = unrealizedPL >= 0 ? 'profit' : 'loss';
                        const plSign = unrealizedPL >= 0 ? '+' : '';

                        row.innerHTML = `
                            <td>${symbol}</td>
                            <td>${quantity}</td>
                            <td>${formatCurrency(avgPrice)}</td>
                            <td>${formatCurrency(currentPrice)}</td>
                            <td>${formatCurrency(marketValue)}</td>
                            <td class="${plClass}">${plSign}${formatCurrency(unrealizedPL)} (${plSign}${unrealizedPLPercent.toFixed(2)}%)</td>
                            <td class="position-actions">
                                <button class="buy-more" data-symbol="${symbol}">Buy More</button>
                                <button class="sell" data-symbol="${symbol}" data-qty="${quantity}">Sell</button>
                            </td>
                        `;

                        positionsBody.appendChild(row);
                    });

                    // Add event listeners to the action buttons
                    const buyMoreButtons = document.querySelectorAll('.position-actions .buy-more');
                    const sellButtons = document.querySelectorAll('.position-actions .sell');

                    buyMoreButtons.forEach(button => {
                        button.addEventListener('click', function() {
                            const symbol = this.getAttribute('data-symbol');
                            searchStock(symbol);
                            // Focus on market order tab
                            document.querySelector('.tabs [data-tab="market-order"]').click();
                        });
                    });

                    sellButtons.forEach(button => {
                        button.addEventListener('click', function() {
                            const symbol = this.getAttribute('data-symbol');
                            const quantity = this.getAttribute('data-qty');
                            searchStock(symbol);
                            // Focus on market order tab
                            document.querySelector('.tabs [data-tab="market-order"]').click();
                            // Set quantity for selling
                            document.getElementById('market-quantity').value = quantity;
                        });
                    });
                }

                hideLoading('portfolio-card');
            })
            .catch(error => {
                console.error('Error fetching portfolio:', error);
                hideLoading('portfolio-card');
                showError('portfolio-card', 'Failed to load portfolio. Please try again.');
            });
    }

    // Fetch orders
    function fetchOrders(status = 'open') {
        const contentId = status === 'open' ? 'open-orders' : 'filled-orders';
        showLoading(contentId);
        hideError(contentId);

        fetch(`${API_BASE}/orders?status=${status}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`Failed to fetch ${status} orders`);
                }
                return response.json();
            })
            .then(data => {
                const orders = data.orders || [];
                const ordersTable = document.getElementById(`${contentId}-table`);
                const ordersBody = document.getElementById(`${contentId}-body`);
                const noOrdersMessage = document.getElementById(`no-${contentId}-message`);

                // Clear the table
                ordersBody.innerHTML = '';

                if (orders.length === 0) {
                    ordersTable.style.display = 'none';
                    noOrdersMessage.style.display = 'block';
                } else {
                    ordersTable.style.display = 'table';
                    noOrdersMessage.style.display = 'none';

                    // Add each order to the table
                    orders.forEach(order => {
                        const symbol = order.symbol;
                        const type = order.type;
                        const side = order.side;
                        const quantity = order.qty;
                        const price = type === 'market' ? 'Market' : formatCurrency(parseFloat(order.limit_price || order.stop_price || 0));
                        const orderStatus = order.status;
                        const createdAt = new Date(order.created_at).toLocaleString();

                        const row = document.createElement('tr');

                        let rowHTML = `
                            <td>${symbol}</td>
                            <td>${type.toUpperCase()}</td>
                            <td>${side.toUpperCase()}</td>
                            <td>${quantity}</td>
                            <td>${price}</td>
                            <td>${orderStatus}</td>
                            <td>${createdAt}</td>
                        `;

                        // Add cancel button for open orders
                        if (status === 'open') {
                            rowHTML += `
                                <td>
                                    <button class="cancel-order" data-order-id="${order.id}">Cancel</button>
                                </td>
                            `;
                        }

                        row.innerHTML = rowHTML;
                        ordersBody.appendChild(row);
                    });

                    // Add event listeners to cancel buttons
                    if (status === 'open') {
                        const cancelButtons = document.querySelectorAll('.cancel-order');
                        cancelButtons.forEach(button => {
                            button.addEventListener('click', function() {
                                const orderId = this.getAttribute('data-order-id');
                                cancelOrder(orderId);
                            });
                        });
                    }
                }

                hideLoading(contentId);
            })
            .catch(error => {
                console.error(`Error fetching ${status} orders:`, error);
                hideLoading(contentId);
                showError(contentId, `Failed to load ${status} orders. Please try again.`);
            });
    }

    // Cancel an order
    function cancelOrder(orderId) {
        fetch(`${API_BASE}/orders/${orderId}`, {
            method: 'DELETE'
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to cancel order');
            }
            return response.json();
        })
        .then(data => {
            // Refresh open orders
            fetchOrders('open');
            showSuccess('order-success', 'Order canceled successfully.');
        })
        .catch(error => {
            console.error('Error canceling order:', error);
            showError('order-error', 'Failed to cancel order. Please try again.');
        });
    }

    // Search for a stock - USE OUR BACKEND API INSTEAD OF DIRECT API CALL
    function searchStock(symbol) {
        if (!symbol) {
            symbol = document.getElementById('stock-search-input').value.trim().toUpperCase();
        }

        if (!symbol) {
            showError('stock-search-error', 'Please enter a stock symbol.');
            return;
        }

        showLoading('stock-search-loading');
        hideError('stock-search-error');
        document.getElementById('stock-details').style.display = 'none';

        // Use our backend API endpoint instead of direct Alpha Vantage call
        fetch(`${API_BASE}/${symbol}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`API error: ${response.status} ${response.statusText}`);
                }
                return response.json();
            })
            .then(data => {
                currentSymbol = symbol;
                currentStockDetails = data;

                // Update stock details
                document.getElementById('stock-symbol').textContent = data.symbol || symbol;
                document.getElementById('stock-name').textContent = data.name || getCompanyName(symbol);

                // Update price info - structure based on our backend API
                if (data.quote) {
                    const quote = data.quote;
                    const price = parseFloat(quote.price);
                    const change = parseFloat(quote.change);
                    const changePercent = parseFloat(quote.changePercent);
                    const volume = parseInt(quote.volume);

                    document.getElementById('stock-price').textContent = formatCurrency(price);

                    const changeElement = document.getElementById('stock-change');
                    const changeSign = change >= 0 ? '+' : '';
                    changeElement.textContent = `${changeSign}${change.toFixed(2)} (${changeSign}${changePercent.toFixed(2)}%)`;
                    changeElement.className = change >= 0 ? 'profit' : 'loss';

                    document.getElementById('stock-volume').textContent = formatNumber(volume);
                }

                // Show stock details
                document.getElementById('stock-details').style.display = 'block';

                // Update chart
                fetchStockHistory(symbol, document.querySelector('.timeframe.active').getAttribute('data-timeframe'));

                hideLoading('stock-search-loading');
            })
            .catch(error => {
                console.error('Error searching stock:', error);
                hideLoading('stock-search-loading');
                
                // Show more descriptive error message
                let errorMessage = error.message;
                
                // Add debugging info to console
                console.log('Search URL:', `${API_BASE}/${symbol}`);
                console.log('Error details:', error);
                
                // Show user-friendly message
                if (errorMessage.includes('API rate limit') || errorMessage.includes('API call frequency')) {
                    showError('stock-search-error', 'API rate limit reached. Please try again in a minute.');
                } else if (errorMessage.includes('not found') || errorMessage.includes('symbol')) {
                    showError('stock-search-error', `Stock symbol "${symbol}" not found. Please check and try again.`);
                } else {
                    showError('stock-search-error', 'Failed to retrieve stock data: ' + errorMessage);
                }
            });
    }

    // Fetch stock history for chart - USE OUR BACKEND API INSTEAD OF DIRECT API CALL
    function fetchStockHistory(symbol, timeframe) {
        console.log(`Fetching history for ${symbol} (${timeframe})`);
        
        // Use our backend API endpoint instead of direct Alpha Vantage call
        fetch(`${API_BASE}/${symbol}/history?timeframe=${timeframe}`)
            .then(response => {
                // Check for API rate limit errors (status 429)
                if (response.status === 429) {
                    throw new Error('API rate limit reached. Please try again in a moment.');
                }
                
                // Check for other HTTP errors
                if (!response.ok) {
                    throw new Error(`Failed to fetch history: ${response.status} ${response.statusText}`);
                }
                
                return response.json();
            })
            .then(data => {
                const historyData = data.history || [];
                
                console.log('Received history data');
                console.log('Processed history data points:', historyData.length);

                // For this demo we'll just display a placeholder
                const chartElement = document.getElementById('price-chart');
                chartElement.innerHTML = `<div style="height: 100%; display: flex; align-items: center; justify-content: center;">
                    <p>Chart for ${symbol} (${timeframe}) - ${historyData.length} data points received</p>
                </div>`;
            })
            .catch(error => {
                console.error('Error fetching stock history:', error);
                
                // Add debugging info to console
                console.log('History URL:', `${API_BASE}/${symbol}/history?timeframe=${timeframe}`);
                console.log('Error details:', error);
                
                // Display error in chart area
                const chartElement = document.getElementById('price-chart');
                chartElement.innerHTML = `<div style="height: 100%; display: flex; align-items: center; justify-content: center; color: #F44336;">
                    <p>Error loading chart: ${error.message}</p>
                </div>`;
            });
    }

    // Place an order using the backend API
    function placeOrder(orderType, side, formId) {
        const form = document.getElementById(formId);
        const symbol = currentSymbol;

        if (!symbol) {
            showError('order-error', 'Please search for a stock first.');
            return;
        }

        // Get common form values
        const quantity = parseInt(form.querySelector(`#${orderType}-quantity`).value);
        const timeInForce = form.querySelector(`#${orderType}-time-in-force`).value;

        if (!quantity || quantity <= 0) {
            showError('order-error', 'Please enter a valid quantity.');
            return;
        }

        // Build order data based on order type
        const orderData = {
            symbol: symbol,
            quantity: side === 'buy' ? quantity : -quantity, // Negative for sell
            orderType: orderType,
            timeInForce: timeInForce
        };
        
        // Add specific order parameters based on type
        switch (orderType) {
            case 'market':
                // Market orders don't need additional parameters
                break;
                
            case 'limit':
                const limitPrice = parseFloat(form.querySelector('#limit-price').value);
                if (!limitPrice || limitPrice <= 0) {
                    showError('order-error', 'Please enter a valid limit price.');
                    return;
                }
                orderData.limitPrice = limitPrice;
                break;
                
            case 'stop':
                const stopPrice = parseFloat(form.querySelector('#stop-price').value);
                if (!stopPrice || stopPrice <= 0) {
                    showError('order-error', 'Please enter a valid stop price.');
                    return;
                }
                orderData.stopPrice = stopPrice;
                break;
                
            case 'stop-limit':
                const slStopPrice = parseFloat(form.querySelector('#stop-limit-stop-price').value);
                const slLimitPrice = parseFloat(form.querySelector('#stop-limit-limit-price').value);
                if (!slStopPrice || slStopPrice <= 0 || !slLimitPrice || slLimitPrice <= 0) {
                    showError('order-error', 'Please enter valid stop and limit prices.');
                    return;
                }
                orderData.stopPrice = slStopPrice;
                orderData.limitPrice = slLimitPrice;
                break;
                
            case 'trailing-stop':
                const trailPercent = parseFloat(form.querySelector('#trailing-stop-percent').value);
                if (!trailPercent || trailPercent <= 0) {
                    showError('order-error', 'Please enter a valid trail percent.');
                    return;
                }
                orderData.trailPercent = trailPercent;
                break;
        }

        // Hide any previous messages
        hideError('order-error');
        hideSuccess('order-success');
        
        // Submit the order to the backend
        fetch(`${API_BASE}/orders`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(orderData)
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`Order failed: ${response.status} ${response.statusText}`);
            }
            return response.json();
        })
        .then(data => {
            // Show success message with order details
            showSuccess('order-success', `Order ${data.id} placed successfully: ${data.status}`);
            
            // Reset form
            form.reset();
            
            // Refresh data
            fetchPortfolio();
            fetchOrders('open');
            
            // If the order was for the currently displayed stock, refresh that too
            if (data.symbol === currentSymbol) {
                searchStock(currentSymbol);
            }
        })
        .catch(error => {
            console.error("Order placement error:", error);
            showError('order-error', `Failed to place order: ${error.message}`);
        });
    }

    // Helper functions
    function formatCurrency(value) {
        return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
    }

    function formatNumber(value) {
        return new Intl.NumberFormat('en-US').format(value);
    }

    function showLoading(elementId) {
        const loadingElement = document.getElementById(`${elementId}-loading`);
        if (loadingElement) {
            loadingElement.style.display = 'block';
        }
    }

    function hideLoading(elementId) {
        const loadingElement = document.getElementById(`${elementId}-loading`);
        if (loadingElement) {
            loadingElement.style.display = 'none';
        }
    }

    function showError(elementId, message) {
        const errorElement = document.getElementById(elementId);
        if (errorElement) {
            errorElement.textContent = message;
            errorElement.style.display = 'block';
            // Hide error after 5 seconds
            setTimeout(() => {
                errorElement.style.display = 'none';
            }, 5000);
        }
    }

    function hideError(elementId) {
        const errorElement = document.getElementById(`${elementId}-error`);
        if (errorElement) {
            errorElement.style.display = 'none';
        }
    }

    function showSuccess(elementId, message) {
        const successElement = document.getElementById(elementId);
        if (successElement) {
            successElement.textContent = message;
            successElement.style.display = 'block';
            // Hide success message after 5 seconds
            setTimeout(() => {
                successElement.style.display = 'none';
            }, 5000);
        }
    }

    function hideSuccess(elementId) {
        const successElement = document.getElementById(elementId);
        if (successElement) {
            successElement.style.display = 'none';
        }
    }
    
    // Helper function to get company names
    function getCompanyName(symbol) {
        const companyNames = {
            'AAPL': 'Apple Inc.',
            'MSFT': 'Microsoft Corporation',
            'GOOGL': 'Alphabet Inc.',
            'AMZN': 'Amazon.com Inc.',
            'META': 'Meta Platforms Inc.',
            'TSLA': 'Tesla Inc.',
            'NVDA': 'NVIDIA Corporation',
            'JPM': 'JPMorgan Chase & Co.',
            'V': 'Visa Inc.',
            'JNJ': 'Johnson & Johnson',
            'WMT': 'Walmart Inc.',
            'PG': 'Procter & Gamble Co.',
            'MA': 'Mastercard Inc.',
            'UNH': 'UnitedHealth Group Inc.',
            'HD': 'Home Depot Inc.'
        };
        
        return companyNames[symbol] || `${symbol} Inc.`;
    }

    // Set up event listeners for stock dropdown
    document.getElementById('stock-dropdown').addEventListener('change', function() {
        const symbol = this.value;
        if (symbol) {
            searchStock(symbol);
        }
    });

    // Event listeners
    document.getElementById('refresh-account').addEventListener('click', fetchAccountInfo);
    document.getElementById('refresh-portfolio').addEventListener('click', fetchPortfolio);
    document.getElementById('refresh-orders').addEventListener('click', function() {
        const activeTab = document.querySelector('#orders-card .tab.active');
        const status = activeTab.getAttribute('data-tab') === 'open-orders' ? 'open' : 'closed';
        fetchOrders(status);
    });

    document.getElementById('stock-search-button').addEventListener('click', function() {
        searchStock();
    });
    
    // Enable pressing Enter in the search input
    document.getElementById('stock-search-input').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            searchStock();
        }
    });
    
    // Set up event listeners for order buttons
    document.getElementById('market-buy-button').addEventListener('click', function() {
        placeOrder('market', 'buy', 'market-order-form');
    });
    
    document.getElementById('market-sell-button').addEventListener('click', function() {
        placeOrder('market', 'sell', 'market-order-form');
    });
    
    document.getElementById('limit-buy-button').addEventListener('click', function() {
        placeOrder('limit', 'buy', 'limit-order-form');
    });
    
    document.getElementById('limit-sell-button').addEventListener('click', function() {
        placeOrder('limit', 'sell', 'limit-order-form');
    });
    
    document.getElementById('stop-buy-button').addEventListener('click', function() {
        placeOrder('stop', 'buy', 'stop-order-form');
    });
    
    document.getElementById('stop-sell-button').addEventListener('click', function() {
        placeOrder('stop', 'sell', 'stop-order-form');
    });
    
    document.getElementById('stop-limit-buy-button').addEventListener('click', function() {
        placeOrder('stop-limit', 'buy', 'stop-limit-order-form');
    });
    
    document.getElementById('stop-limit-sell-button').addEventListener('click', function() {
        placeOrder('stop-limit', 'sell', 'stop-limit-order-form');
    });
    
    document.getElementById('trailing-stop-buy-button').addEventListener('click', function() {
        placeOrder('trailing-stop', 'buy', 'trailing-stop-order-form');
    });
    
    document.getElementById('trailing-stop-sell-button').addEventListener('click', function() {
        placeOrder('trailing-stop', 'sell', 'trailing-stop-order-form');
    });
    
    // Listen for authentication changes
    window.addEventListener('DOMContentLoaded', function() {
        // Check if the user is logged in
        const idToken = getCookie('idToken');
        const localId = getCookie('localId');
        const sessionCookie = getCookie('session');
        
        console.log("Authentication status check:");
        console.log("- session cookie:", sessionCookie);
        console.log("- idToken cookie:", idToken ? "exists" : "missing");
        console.log("- localId cookie:", localId ? "exists" : "missing");
        
        // Initialize with real data from Firebase if tokens are available
        if (idToken && localId) {
            // Initialize with real data from Firebase
            fetchAccountInfo();
            fetchPortfolio();
            fetchOrders('open');
        } else {
            console.log("Firebase tokens not available, initializing with empty state");
            
            // Clear any existing mock data
            window.mockPortfolio = {};
            window.mockOrderHistory = [];
            
            // Show empty tables where needed
            const positionsTable = document.getElementById('positions-table');
            const noPositionsMessage = document.getElementById('no-positions-message');
            if (positionsTable && noPositionsMessage) {
                positionsTable.style.display = 'none';
                noPositionsMessage.style.display = 'block';
            }
            
            const ordersTable = document.getElementById('open-orders-table');
            const noOrdersMessage = document.getElementById('no-open-orders-message');
            if (ordersTable && noOrdersMessage) {
                ordersTable.style.display = 'none';
                noOrdersMessage.style.display = 'block';
            }
            
            // Set default account values to zero
            document.getElementById('account-value').textContent = '$0.00';
            document.getElementById('buying-power').textContent = '$0.00';
            document.getElementById('cash-balance').textContent = '$0.00';
            document.getElementById('unrealized-pl').textContent = '$0.00 (0.00%)';
        }
    });
    
    // Helper function to get cookies
    function getCookie(name) {
        const value = `; ${document.cookie}`;
        const parts = value.split(`; ${name}=`);
        if (parts.length === 2) return parts.pop().split(';').shift();
        return null;
    }
});
