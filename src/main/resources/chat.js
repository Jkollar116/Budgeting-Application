let financialDataMessage = "";

fetch("/api/getData")
    .then(response => response.json())
    .then(data => {
        financialDataMessage =
            "I am going to ask you a question below, only answer questions about the user's financial data provided below, don't answer any other questions. if its not a question about the financial data then respond saying you can only answer questions about that " +
            "Here is the data: " +
            "Net Worth: $" + data.netWorth + ", " +
            "Cash: $" + data.netWorthBreakdown.cash + ", " +
            "Equity: $" + data.netWorthBreakdown.equity + ", " +
            "Investments: $" + data.netWorthBreakdown.investments + ", " +
            "Total Income: $" + data.totalIncome + ", " +
            "Salary: $" + data.totalIncomeBreakdown.salary + ", " +
            "Bonus: $" + data.totalIncomeBreakdown.bonus + ", " +
            "Other Income: $" + data.totalIncomeBreakdown.other + ", " +
            "Total Expenses: $" + data.totalExpenses + ", " +
            "Bills Due: " + data.billsDue;
    })
    .catch(error => {
        financialDataMessage = "Financial data is not available.";
    });

document.addEventListener("DOMContentLoaded", function() {
    const chatWindow = document.getElementById("chat-window");
    const userInput = document.getElementById("user-input");
    const sendBtn = document.getElementById("send-btn");

    function appendMessage(text, sender) {
        const messageDiv = document.createElement("div");
        messageDiv.className = "message " + sender;
        messageDiv.textContent = text;
        chatWindow.appendChild(messageDiv);
        chatWindow.scrollTop = chatWindow.scrollHeight;
    }

    function sendMessage() {
        const text = userInput.value.trim();
        if (!text) return;
        appendMessage(text, "user");
        userInput.value = "";

        const payload = {
            model: "gpt-4",
            messages: [
                { role: "system", content: financialDataMessage },
                { role: "user", content: text }
            ]
        };

        fetch("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
            .then(response => {
                if (!response.ok) {
                    return response.text().then(text => {
                        throw { status: response.status, message: text };
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.error) {
                    appendMessage("Error: " + JSON.stringify(data.error), "bot");
                } else {
                    const reply =
                        data.choices &&
                        data.choices[0] &&
                        data.choices[0].message &&
                        data.choices[0].message.content;
                    appendMessage(reply ? reply : "No response", "bot");
                }
            })
            .catch(error => {
                appendMessage("Error: " + JSON.stringify(error), "bot");
            });
    }

    sendBtn.addEventListener("click", sendMessage);
    userInput.addEventListener("keydown", function(e) {
        if (e.key === "Enter") sendMessage();
    });
});
