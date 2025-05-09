document.addEventListener("DOMContentLoaded", function() {
    function getInt(field) {
        if (typeof field === 'number') return field;
        return field && field.integerValue ? parseInt(field.integerValue) : 0;
    }
    function getMap(mapField) {
        if (mapField && mapField.mapValue && mapField.mapValue.fields) {
            return mapField.mapValue.fields;
        } else if (mapField && mapField.stringValue) {
            try {
                return JSON.parse(mapField.stringValue);
            } catch (e) {
                console.error("Error parsing map string", e);
                return {};
            }
        }
        return {};
    }
    function getArray(arrayField) {
        return arrayField && arrayField.arrayValue && arrayField.arrayValue.values
            ? arrayField.arrayValue.values
            : [];
    }
    fetch('/api/getData', { credentials: 'include' })
        .then(function(response) {
            return response.json();
        })
        .then(function(data) {
            var fields = data.fields;
            var netWorth = getInt(fields.netWorth);
            document.getElementById("netWorthDisplay").textContent = "$" + netWorth;
            var totalIncome = getInt(fields.totalIncome);
            document.getElementById("totalIncomeDisplay").textContent = "$" + totalIncome;
            var totalExpenses = getInt(fields.totalExpenses);
            document.getElementById("totalExpensesDisplay").textContent = "$" + totalExpenses;
            var billsDue = getInt(fields.billsDue);
            document.getElementById("billRemindersDisplay").innerHTML = "<h1>" + billsDue + "</h1>";
            var monthlyExpensesArr = getArray(fields.monthlyExpenses).map(function(item) {
                return getInt(item);
            });
            if (monthlyExpensesArr.length === 0) {
                monthlyExpensesArr = Array(12).fill(0);
            }
            var svgNS = "http://www.w3.org/2000/svg";
            var barContainer = document.getElementById("chartContainer");
            var containerWidth = barContainer.clientWidth;
            var chartWidth = containerWidth < 500 ? 500 : containerWidth;
            var chartHeight = 300;
            var barWidth = chartWidth / monthlyExpensesArr.length;
            var maxExpense = Math.max.apply(null, monthlyExpensesArr);
            if (maxExpense === 0) {
                maxExpense = 1;
            }
            var barSvg = document.createElementNS(svgNS, "svg");
            barSvg.setAttribute("width", "100%");
            barSvg.setAttribute("viewBox", "0 0 " + chartWidth + " " + chartHeight);
            monthlyExpensesArr.forEach(function(expense, i) {
                var barHeight = (expense / maxExpense) * (chartHeight - 40);
                var x = i * barWidth;
                var y = chartHeight - barHeight;
                var rect = document.createElementNS(svgNS, "rect");
                rect.setAttribute("x", x);
                rect.setAttribute("y", y);
                rect.setAttribute("width", barWidth - 4);
                rect.setAttribute("height", barHeight);
                rect.setAttribute("fill", "#FF8C00");
                barSvg.appendChild(rect);
                var label = document.createElementNS(svgNS, "text");
                label.setAttribute("x", x + (barWidth - 4) / 2);
                label.setAttribute("y", chartHeight - 5);
                label.setAttribute("fill", "#000");
                label.setAttribute("font-size", "10");
                label.setAttribute("text-anchor", "middle");
                label.textContent = i + 1;
                barSvg.appendChild(label);
            });
            barContainer.innerHTML = "";
            barContainer.appendChild(barSvg);
            var netWorthBreakdown = getMap(fields.netWorthBreakdown);
            var categories = Object.keys(netWorthBreakdown);
            var nwChartWidth = 300, nwChartHeight = 100;
            var margin = 20, barGap = 10;
            var barWidthNW = categories.length > 0
                ? (nwChartWidth - margin * 2 - barGap * (categories.length - 1)) / categories.length
                : 0;
            var maxCategory = categories.length > 0
                ? Math.max.apply(null, categories.map(function(cat) {
                    return getInt(netWorthBreakdown[cat]);
                }))
                : 0;
            if (maxCategory === 0) {
                maxCategory = 1;
            }
            var nwSvg = document.createElementNS(svgNS, "svg");
            nwSvg.setAttribute("width", nwChartWidth);
            nwSvg.setAttribute("height", nwChartHeight);
            var colorsNW = { cash: "#32CD32", equity: "#1E90FF", investments: "#FF8C00" };
            categories.forEach(function(cat, i) {
                var value = getInt(netWorthBreakdown[cat]);
                var barHeight = (value / maxCategory) * (nwChartHeight - margin * 2);
                var x = margin + i * (barWidthNW + barGap);
                var y = nwChartHeight - margin - barHeight;
                var rect = document.createElementNS(svgNS, "rect");
                rect.setAttribute("x", x);
                rect.setAttribute("y", y);
                rect.setAttribute("width", barWidthNW);
                rect.setAttribute("height", barHeight);
                rect.setAttribute("fill", colorsNW[cat] || "#ccc");
                nwSvg.appendChild(rect);
                var textValue = document.createElementNS(svgNS, "text");
                textValue.setAttribute("x", x + barWidthNW / 2);
                textValue.setAttribute("y", y - 5);
                textValue.setAttribute("fill", "#fff");
                textValue.setAttribute("font-size", "10");
                textValue.setAttribute("text-anchor", "middle");
                textValue.textContent = value;
                nwSvg.appendChild(textValue);
                var textCat = document.createElementNS(svgNS, "text");
                textCat.setAttribute("x", x + barWidthNW / 2);
                textCat.setAttribute("y", nwChartHeight - 5);
                textCat.setAttribute("fill", "#fff");
                textCat.setAttribute("font-size", "10");
                textCat.setAttribute("text-anchor", "middle");
                textCat.textContent = cat;
                nwSvg.appendChild(textCat);
            });
            document.getElementById("netWorthChart").innerHTML = "";
            document.getElementById("netWorthChart").appendChild(nwSvg);
            var incomeBreakdown = getMap(fields.totalIncomeBreakdown);
            var incomeChartContainer = document.getElementById("incomeChart");
            var pieWidth = 300, pieHeight = 300;
            var incomeSvg = document.createElementNS(svgNS, "svg");
            incomeSvg.setAttribute("width", pieWidth);
            incomeSvg.setAttribute("height", pieHeight);
            var cx = pieWidth / 2, cy = pieHeight / 2, r = Math.min(cx, cy) - 20;
            var totalIncomeBreakdown = 0;
            for (var key in incomeBreakdown) {
                totalIncomeBreakdown += getInt(incomeBreakdown[key]);
            }
            if (totalIncomeBreakdown === 0) {
                totalIncomeBreakdown = 1;
            }
            var startAngle = 0;
            var colorsIncome = { salary: "#FFD700", bonus: "#FF69B4", other: "#8A2BE2" };
            for (var key in incomeBreakdown) {
                var amount = getInt(incomeBreakdown[key]);
                var sliceAngle = (amount / totalIncomeBreakdown) * 2 * Math.PI;
                var endAngle = startAngle + sliceAngle;
                var x1 = cx + r * Math.cos(startAngle);
                var y1 = cy + r * Math.sin(startAngle);
                var x2 = cx + r * Math.cos(endAngle);
                var y2 = cy + r * Math.sin(endAngle);
                var largeArc = sliceAngle > Math.PI ? 1 : 0;
                var d = "M " + cx + " " + cy + " L " + x1 + " " + y1 + " A " + r + " " + r + " 0 " + largeArc + " 1 " + x2 + " " + y2 + " Z";
                var path = document.createElementNS(svgNS, "path");
                path.setAttribute("d", d);
                path.setAttribute("fill", colorsIncome[key] || "#ccc");
                incomeSvg.appendChild(path);
                var midAngle = startAngle + sliceAngle / 2;
                var labelX = cx + (r / 2) * Math.cos(midAngle);
                var labelY = cy + (r / 2) * Math.sin(midAngle);
                var text = document.createElementNS(svgNS, "text");
                text.setAttribute("x", labelX);
                text.setAttribute("y", labelY);
                text.setAttribute("fill", "#000");
                text.setAttribute("font-size", "12");
                text.setAttribute("text-anchor", "middle");
                text.textContent = key + " (" + amount + ")";
                incomeSvg.appendChild(text);
                startAngle = endAngle;
            }
            incomeChartContainer.innerHTML = "";
            incomeChartContainer.appendChild(incomeSvg);
            var expensesPieContainer = document.getElementById("expensesPieChart");
            var pieSvg = document.createElementNS(svgNS, "svg");
            pieSvg.setAttribute("width", pieWidth);
            pieSvg.setAttribute("height", pieHeight);
            var cx2 = pieWidth / 2, cy2 = pieHeight / 2, r2 = Math.min(cx2, cy2) - 20;
            var quarters = [0, 0, 0, 0];
            monthlyExpensesArr.forEach(function(expense, i) {
                quarters[Math.floor(i / 3)] += expense;
            });
            var totalQuarterExpenses = quarters.reduce(function(sum, val) {
                return sum + val;
            }, 0);
            if (totalQuarterExpenses === 0) {
                totalQuarterExpenses = 1;
            }
            var startAngle2 = 0;
            var quarterLabels = ["Q1", "Q2", "Q3", "Q4"];
            var colorsExpenses = ["#FF8C00", "#FF4500", "#1E90FF", "#32CD32"];
            quarters.forEach(function(qExpense, i) {
                var sliceAngle = (qExpense / totalQuarterExpenses) * 2 * Math.PI;
                var endAngle2 = startAngle2 + sliceAngle;
                var x1 = cx2 + r2 * Math.cos(startAngle2);
                var y1 = cy2 + r2 * Math.sin(startAngle2);
                var x2 = cx2 + r2 * Math.cos(endAngle2);
                var y2 = cy2 + r2 * Math.sin(endAngle2);
                var largeArc = sliceAngle > Math.PI ? 1 : 0;
                var d = "M " + cx2 + " " + cy2 + " L " + x1 + " " + y1 + " A " + r2 + " " + r2 + " 0 " + largeArc + " 1 " + x2 + " " + y2 + " Z";
                var path = document.createElementNS(svgNS, "path");
                path.setAttribute("d", d);
                path.setAttribute("fill", colorsExpenses[i] || "#ccc");
                pieSvg.appendChild(path);
                var midAngle = startAngle2 + sliceAngle / 2;
                var labelX = cx2 + (r2 / 2) * Math.cos(midAngle);
                var labelY = cy2 + (r2 / 2) * Math.sin(midAngle);
                var label = document.createElementNS(svgNS, "text");
                label.setAttribute("x", labelX);
                label.setAttribute("y", labelY);
                label.setAttribute("fill", "#000");
                label.setAttribute("font-size", "12");
                label.setAttribute("text-anchor", "middle");
                label.textContent = quarterLabels[i] + " (" + qExpense + ")";
                pieSvg.appendChild(label);
                startAngle2 = endAngle2;
            });
            expensesPieContainer.innerHTML = "";
            expensesPieContainer.appendChild(pieSvg);
        })
        .catch(function(err) {
            console.error("Error fetching dashboard data:", err);
        });
});

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