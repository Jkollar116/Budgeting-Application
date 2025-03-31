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

    // API URLs
    const API_BASE = '/api/stocks'; // For account/orders/portfolio data 
    const ALPHA_VANTAGE_API = 'https://www.alphavantage.co/query';
    const ALPHA_VANTAGE_KEY = window.alphaVantageKey || '2470IDOB57MHSDPZ'; // Allow key to be provided from server

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

    // Search for a stock
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

        // Use Alpha Vantage API directly
        fetch(`${ALPHA_VANTAGE_API}?function=GLOBAL_QUOTE&symbol=${symbol}&apikey=${ALPHA_VANTAGE_KEY}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`API error: ${response.status} ${response.statusText}`);
                }
                return response.json();
            })
            .then(data => {
                currentSymbol = symbol;
                currentStockDetails = data;

                // Make sure we have Global Quote data
                if (!data['Global Quote'] || Object.keys(data['Global Quote']).length === 0) {
                    throw new Error('No stock data available for symbol: ' + symbol);
                }

                const quote = data['Global Quote'];
                
                // Update stock details
                document.getElementById('stock-symbol').textContent = quote['01. symbol'] || symbol;
                // Get company name - for simplicity we'll just use the symbol
                document.getElementById('stock-name').textContent = getCompanyName(symbol);

                // Update price info
                const price = parseFloat(quote['05. price']);
                const prevClose = parseFloat(quote['08. previous close']);
                const change = parseFloat(quote['09. change']);
                const changePercent = parseFloat(quote['10. change percent'].replace('%', ''));
                const volume = parseInt(quote['06. volume']);

                document.getElementById('stock-price').textContent = formatCurrency(price);

                const changeElement = document.getElementById('stock-change');
                const changeSign = change >= 0 ? '+' : '';
                changeElement.textContent = `${changeSign}${change.toFixed(2)} (${changeSign}${changePercent.toFixed(2)}%)`;
                changeElement.className = change >= 0 ? 'profit' : 'loss';

                document.getElementById('stock-volume').textContent = formatNumber(volume);

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
                console.log('Search URL:', `${API_BASE}/stocks/${symbol}`);
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

    // Fetch stock history for chart
    function fetchStockHistory(symbol, timeframe) {
        console.log(`Fetching history for ${symbol} (${timeframe})`);
        
        // Map timeframe to Alpha Vantage parameters
        let timeSeriesFunction, interval;
        switch(timeframe) {
            case '1D':
                timeSeriesFunction = 'TIME_SERIES_INTRADAY';
                interval = '5min';
                break;
            case '1W':
                timeSeriesFunction = 'TIME_SERIES_INTRADAY';
                interval = '60min';
                break;
            case '1M':
            case '3M':
                timeSeriesFunction = 'TIME_SERIES_DAILY';
                interval = null;
                break;
            case '1Y':
            case 'ALL':
                timeSeriesFunction = 'TIME_SERIES_WEEKLY';
                interval = null;
                break;
            default:
                timeSeriesFunction = 'TIME_SERIES_DAILY';
                interval = null;
        }
        
        // Construct URL based on function and interval
        let apiUrl = `${ALPHA_VANTAGE_API}?function=${timeSeriesFunction}&symbol=${symbol}`;
        if (interval) {
            apiUrl += `&interval=${interval}`;
        }
        apiUrl += `&apikey=${ALPHA_VANTAGE_KEY}`;
        
        fetch(apiUrl)
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
                // Check if we received an error message from Alpha Vantage
                if (data.Note && data.Note.includes('call frequency')) {
                    throw new Error('API rate limit reached. Please try again in a minute.');
                }
                
                // Find the time series data key
                let timeSeriesKey = Object.keys(data).find(key => key.includes('Time Series') || key.includes('Weekly') || key.includes('Daily'));
                
                if (!timeSeriesKey || !data[timeSeriesKey] || Object.keys(data[timeSeriesKey]).length === 0) {
                    throw new Error('No historical data available for this symbol');
                }
                
                console.log('Received history data');
                
                // Create simplified data structure for chart
                const historyData = [];
                const timeSeries = data[timeSeriesKey];
                const timestamps = Object.keys(timeSeries).sort();
                
                // Limit to max 100 points
                const maxPoints = Math.min(timestamps.length, 100);
                
                for (let i = 0; i < maxPoints; i++) {
                    const timestamp = timestamps[i];
                    const dataPoint = timeSeries[timestamp];
                    const closePrice = parseFloat(dataPoint['4. close'] || dataPoint['4: close'] || 0);
                    
                    historyData.push({
                        timestamp: timestamp,
                        price: closePrice
                    });
                }
                
                // In a real application you would render the chart here
                // using a library like Chart.js, D3.js, or another charting library
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
                console.log('History URL:', `${API_BASE}/stocks/${symbol}/history?timeframe=${timeframe}`);
                console.log('Error details:', error);
                
                // Display error in chart area
                const chartElement = document.getElementById('price-chart');
                chartElement.innerHTML = `<div style="height: 100%; display: flex; align-items: center; justify-content: center; color: #F44336;">
                    <p>Error loading chart: ${error.message}</p>
                </div>`;
            });
    }

    // Place an order (mock implementation without backend)
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

        // Get price information for the order
        let orderPrice = 0;
        let priceDescription = "";
        
        switch (orderType) {
            case 'market':
                // For market orders, use current price from the stock details
                if (currentStockDetails && currentStockDetails['Global Quote']) {
                    orderPrice = parseFloat(currentStockDetails['Global Quote']['05. price']);
                    priceDescription = "MARKET";
                } else {
                    showError('order-error', 'Cannot place market order - current price not available.');
                    return;
                }
                break;
                
            case 'limit':
                orderPrice = parseFloat(form.querySelector('#limit-price').value);
                if (!orderPrice || orderPrice <= 0) {
                    showError('order-error', 'Please enter a valid limit price.');
                    return;
                }
                priceDescription = `LIMIT @ ${formatCurrency(orderPrice)}`;
                break;
                
            case 'stop':
                orderPrice = parseFloat(form.querySelector('#stop-price').value);
                if (!orderPrice || orderPrice <= 0) {
                    showError('order-error', 'Please enter a valid stop price.');
                    return;
                }
                priceDescription = `STOP @ ${formatCurrency(orderPrice)}`;
                break;
                
            case 'stop-limit':
                const slStopPrice = parseFloat(form.querySelector('#stop-limit-stop-price').value);
                const slLimitPrice = parseFloat(form.querySelector('#stop-limit-limit-price').value);
                if (!slStopPrice || slStopPrice <= 0 || !slLimitPrice || slLimitPrice <= 0) {
                    showError('order-error', 'Please enter valid stop and limit prices.');
                    return;
                }
                orderPrice = slLimitPrice; // Use limit price for cost calculation
                priceDescription = `STOP-LIMIT @ ${formatCurrency(slStopPrice)}/${formatCurrency(slLimitPrice)}`;
                break;
                
            case 'trailing-stop':
                const trailPercent = parseFloat(form.querySelector('#trailing-stop-percent').value);
                if (!trailPercent || trailPercent <= 0) {
                    showError('order-error', 'Please enter a valid trail percent.');
                    return;
                }
                
                // Calculate trail amount based on current price
                if (currentStockDetails && currentStockDetails['Global Quote']) {
                    const currentPrice = parseFloat(currentStockDetails['Global Quote']['05. price']);
                    const trailAmount = currentPrice * (trailPercent / 100);
                    orderPrice = side === 'buy' ? currentPrice + trailAmount : currentPrice - trailAmount;
                    priceDescription = `TRAILING-STOP ${trailPercent}%`;
                } else {
                    showError('order-error', 'Cannot place trailing stop order - current price not available.');
                    return;
                }
                break;
        }

        // Hide any previous messages
        hideError('order-error');
        hideSuccess('order-success');
        
        // Create mock order ID
        const orderId = 'ord_' + Math.random().toString(36).substring(2, 15);
        
        // Create and add order to the list
        const order = {
            id: orderId,
            symbol: symbol,
            type: orderType,
            side: side,
            qty: quantity,
            price: orderPrice,
            status: 'filled', // Simulate immediate fill
            created_at: new Date().toISOString()
        };
        
        // Add to mock order history
        if (!window.mockOrderHistory) {
            window.mockOrderHistory = [];
        }
        window.mockOrderHistory.push(order);
        
        // Calculate cost
        const cost = quantity * orderPrice;
        const costFormatted = formatCurrency(cost);
        
        // Show success message with details
        showSuccess('order-success', `Order placed successfully: ${side.toUpperCase()} ${quantity} ${symbol} @ ${priceDescription} for ${costFormatted}`);
        
        // Since we're using a mock backend, manually update the portfolio
        updateMockPortfolio(symbol, side, quantity, orderPrice);
        
        // Reset form
        form.reset();
        
        // Call these to refresh the UI with our mock data
        updateOrdersDisplay();
    }
    
    // Helper function to update mock portfolio
    function updateMockPortfolio(symbol, side, quantity, price) {
        // Initialize mock portfolio if it doesn't exist
        if (!window.mockPortfolio) {
            window.mockPortfolio = {};
        }
        
        // Get or create position
        if (!window.mockPortfolio[symbol]) {
            window.mockPortfolio[symbol] = {
                symbol: symbol,
                qty: 0,
                avg_entry_price: 0,
                total_cost: 0
            };
        }
        
        const position = window.mockPortfolio[symbol];
        
        if (side === 'buy') {
            // Calculate new average price
            const oldValue = position.qty * position.avg_entry_price;
            const newValue = quantity * price;
            const newQuantity = position.qty + quantity;
            
            position.qty = newQuantity;
            position.total_cost = oldValue + newValue;
            position.avg_entry_price = position.total_cost / newQuantity;
        } else { // sell
            // Just reduce quantity for sell
            position.qty -= quantity;
            
            // Remove position if quantity is 0 or negative
            if (position.qty <= 0) {
                delete window.mockPortfolio[symbol];
            }
        }
        
        // Update portfolio display
        updatePortfolioDisplay();
    }
    
    // Function to update the orders display with mock data
    function updateOrdersDisplay() {
        // Only proceed if we have mock order history
        if (!window.mockOrderHistory) return;
        
        const ordersBody = document.getElementById('open-orders-body');
        if (!ordersBody) return;
        
        // Clear current display
        ordersBody.innerHTML = '';
        
        // Get orders table and message elements
        const ordersTable = document.getElementById('open-orders-table');
        const noOrdersMessage = document.getElementById('no-open-orders-message');
        
        // Filter to most recent 5 orders
        const recentOrders = window.mockOrderHistory.slice(-5).reverse();
        
        if (recentOrders.length === 0) {
            ordersTable.style.display = 'none';
            noOrdersMessage.style.display = 'block';
        } else {
            ordersTable.style.display = 'table';
            noOrdersMessage.style.display = 'none';
            
            recentOrders.forEach(order => {
                const row = document.createElement('tr');
                
                const priceDisplay = order.type === 'market' ? 'Market' : formatCurrency(order.price);
                
                row.innerHTML = `
                    <td>${order.symbol}</td>
                    <td>${order.type.toUpperCase()}</td>
                    <td>${order.side.toUpperCase()}</td>
                    <td>${order.qty}</td>
                    <td>${priceDisplay}</td>
                    <td>${order.status}</td>
                    <td>${new Date(order.created_at).toLocaleString()}</td>
                `;
                
                ordersBody.appendChild(row);
            });
        }
    }
    
    // Function to update portfolio display with mock data
    function updatePortfolioDisplay() {
        // Only proceed if we have mock portfolio
        if (!window.mockPortfolio) return;
        
        const positionsBody = document.getElementById('positions-body');
        if (!positionsBody) return;
        
        // Clear current display
        positionsBody.innerHTML = '';
        
        // Get positions table and message elements
        const positionsTable = document.getElementById('positions-table');
        const noPositionsMessage = document.getElementById('no-positions-message');
        
        const positions = Object.values(window.mockPortfolio);
        
        if (positions.length === 0) {
            positionsTable.style.display = 'none';
            noPositionsMessage.style.display = 'block';
        } else {
            positionsTable.style.display = 'table';
            noPositionsMessage.style.display = 'none';
            
            positions.forEach(position => {
                const symbol = position.symbol;
                const quantity = position.qty;
                const avgPrice = position.avg_entry_price;
                
                // Get current price from stored stock details or use average price as fallback
                let currentPrice = avgPrice;
                if (currentStockDetails && symbol === currentSymbol && 
                    currentStockDetails['Global Quote']) {
                    currentPrice = parseFloat(currentStockDetails['Global Quote']['05. price']);
                }
                
                const marketValue = quantity * currentPrice;
                const unrealizedPL = marketValue - (quantity * avgPrice);
                const unrealizedPLPC = (unrealizedPL / (quantity * avgPrice)) * 100;
                
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
                    <td class="${plClass}">${plSign}${formatCurrency(unrealizedPL)} (${plSign}${unrealizedPLPC.toFixed(2)}%)</td>
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

    document.getElementById('stock-search-input').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            searchStock();
        }
    });

    // Market order buttons
    document.getElementById('market-buy-button').addEventListener('click', function() {
        placeOrder('market', 'buy', 'market-order-form');
    });

    document.getElementById('market-sell-button').addEventListener('click', function() {
        placeOrder('market', 'sell', 'market-order-form');
    });

    // Limit order buttons
    document.getElementById('limit-buy-button').addEventListener('click', function() {
        placeOrder('limit', 'buy', 'limit-order-form');
    });

    document.getElementById('limit-sell-button').addEventListener('click', function() {
        placeOrder('limit', 'sell', 'limit-order-form');
    });

    // Stop order buttons
    document.getElementById('stop-buy-button').addEventListener('click', function() {
        placeOrder('stop', 'buy', 'stop-order-form');
    });

    document.getElementById('stop-sell-button').addEventListener('click', function() {
        placeOrder('stop', 'sell', 'stop-order-form');
    });

    // Stop-limit order buttons
    document.getElementById('stop-limit-buy-button').addEventListener('click', function() {
        placeOrder('stop-limit', 'buy', 'stop-limit-order-form');
    });

    document.getElementById('stop-limit-sell-button').addEventListener('click', function() {
        placeOrder('stop-limit', 'sell', 'stop-limit-order-form');
    });

    // Trailing stop order buttons
    document.getElementById('trailing-stop-buy-button').addEventListener('click', function() {
        placeOrder('trailing-stop', 'buy', 'trailing-stop-order-form');
    });

    document.getElementById('trailing-stop-sell-button').addEventListener('click', function() {
        placeOrder('trailing-stop', 'sell', 'trailing-stop-order-form');
    });

    // Initialize mock data
    if (!window.mockOrderHistory) {
        window.mockOrderHistory = [];
    }
    
    if (!window.mockPortfolio) {
        window.mockPortfolio = {
            'AAPL': {
                symbol: 'AAPL',
                qty: 10,
                avg_entry_price: 170.25,
                total_cost: 1702.50
            },
            'MSFT': {
                symbol: 'MSFT',
                qty: 5,
                avg_entry_price: 410.50,
                total_cost: 2052.50
            }
        };
    }
    
    // Initialize data on page load
    setTimeout(() => {
        // Skip server API calls that might fail
        // fetchAccountInfo();
        // fetchPortfolio();
        // fetchOrders('open');
        
        // Use our mock data instead
        updatePortfolioDisplay();
        updateOrdersDisplay();
    }, 500);
});
