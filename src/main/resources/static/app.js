const form = document.querySelector('#leave-form');
const result = document.querySelector('#result');
const emailInput = document.querySelector('#employeeEmail');
const balanceBox = document.querySelector('#balances');
const submitButton = document.querySelector('#submit-button');

const today = new Date().toISOString().slice(0, 10);
document.querySelector('#startDate').min = today;
document.querySelector('#endDate').min = today;

function showResult(message, kind) {
  result.textContent = message;
  result.className = kind || '';
}

async function showBalances() {
  const email = emailInput.value.trim();
  if (!email) {
    showResult('Enter your work email first.', 'error');
    return;
  }
  balanceBox.innerHTML = '';
  try {
    const response = await fetch(`/api/employees/${encodeURIComponent(email)}/balance`);
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Could not retrieve your balance.');
    data.forEach(balance => {
      const item = document.createElement('span');
      item.className = 'balance';
      item.textContent = `${balance.leaveType}: ${balance.availableDays} available`;
      balanceBox.append(item);
    });
    showResult('', '');
  } catch (error) {
    showResult(error.message, 'error');
  }
}

document.querySelector('#balance-button').addEventListener('click', showBalances);

document.querySelector('#startDate').addEventListener('change', event => {
  document.querySelector('#endDate').min = event.target.value || today;
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(form));
  submitButton.disabled = true;
  showResult('Submitting your request…', '');
  try {
    const response = await fetch('/api/leave-requests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || 'Could not submit your request.');
    form.reset();
    document.querySelector('#startDate').min = today;
    document.querySelector('#endDate').min = today;
    balanceBox.innerHTML = '';
    showResult(`Request ${payload.id} sent to your manager for approval.`, 'success');
  } catch (error) {
    showResult(error.message, 'error');
  } finally {
    submitButton.disabled = false;
  }
});
