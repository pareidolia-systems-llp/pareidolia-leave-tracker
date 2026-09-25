/**
 * Pareidolia Leave Tracker Google Apps Script bridge.
 *
 * Required Script Properties:
 *   SPRING_API_BASE_URL   e.g. https://leave.example.com (no trailing slash)
 *   INTEGRATION_SECRET    exactly the same as APP_GOOGLE_SCRIPT_SHARED_SECRET
 *   LEAVE_SHEET_ID        ID from the target Google Sheet URL
 */

const LEAVE_SHEET_NAME = 'Leave Requests';
const SHEET_HEADERS = [
  'Request ID', 'Employee', 'Employee Email', 'Manager Email', 'Leave Type',
  'Start Date', 'End Date', 'Working Days', 'Reason', 'Status',
  'Manager Comment', 'Requested At', 'Decided At', 'Last Synced At'
];

function doGet(e) {
  const parameters = e.parameter || {};
  const action = String(parameters.action || '').toLowerCase();
  const requestId = String(parameters.requestId || '');
  const token = String(parameters.token || '');
  if ((action !== 'approve' && action !== 'reject') || !requestId || !token) {
    return htmlPage('Invalid leave action', '<p>This approval link is incomplete or invalid.</p>');
  }

  const verb = action === 'approve' ? 'Approve' : 'Reject';
  const color = action === 'approve' ? '#16803c' : '#b42318';
  const body = '<p>You are about to <strong>' + verb.toLowerCase() + '</strong> a leave request.</p>'
    + '<form method="post">'
    + hidden('action', action) + hidden('requestId', requestId) + hidden('token', token)
    + '<label for="managerComment">Comment (optional)</label>'
    + '<textarea id="managerComment" name="managerComment" maxlength="1000" rows="4" placeholder="Add a note for the employee"></textarea>'
    + '<button type="submit" style="background:' + color + '">Confirm ' + verb + '</button>'
    + '</form><p class="hint">This confirmation prevents mail-security scanners from accidentally deciding leave.</p>';
  return htmlPage('Confirm leave decision', body);
}

function doPost(e) {
  const contentType = e.postData && e.postData.type ? e.postData.type : '';
  if (contentType.indexOf('application/json') !== -1) {
    return handleSpringSync(e.postData.contents);
  }
  return handleManagerDecision(e.parameter || {});
}

function handleManagerDecision(parameters) {
  const action = String(parameters.action || '').toUpperCase();
  const requestId = String(parameters.requestId || '');
  const token = String(parameters.token || '');
  const managerComment = String(parameters.managerComment || '').trim();
  if ((action !== 'APPROVE' && action !== 'REJECT') || !requestId || !token) {
    return htmlPage('Invalid leave action', '<p>The submitted information was incomplete.</p>');
  }

  const properties = getProperties();
  const body = {
    requestId: requestId,
    action: action,
    token: token,
    managerComment: managerComment
  };
  const signature = hmacHex([requestId, action, token, managerComment].join('\n'), properties.secret);
  try {
    const response = UrlFetchApp.fetch(properties.apiBaseUrl + '/api/integrations/google/decisions', {
      method: 'post',
      contentType: 'application/json',
      payload: JSON.stringify(body),
      headers: { 'X-Google-Signature': signature },
      muteHttpExceptions: true
    });
    const status = response.getResponseCode();
    const payload = safeJson(response.getContentText());
    if (status >= 200 && status < 300) {
      return htmlPage('Decision saved', '<p>The leave request has been <strong>'
        + (action === 'APPROVE' ? 'approved' : 'rejected') + '</strong>. The employee has been notified.</p>');
    }
    return htmlPage('Decision not saved', '<p>' + escapeHtml(payload.error || 'The request could not be processed. It may have expired or already been decided.') + '</p>');
  } catch (error) {
    console.error(error);
    return htmlPage('Connection problem', '<p>Could not reach Pareidolia Leave Tracker. Please try again or contact HR.</p>');
  }
}

function handleSpringSync(rawBody) {
  try {
    const payload = JSON.parse(rawBody);
    const properties = getProperties();
    if (payload.source !== 'springboot' || payload.sharedSecret !== properties.secret || payload.event !== 'UPSERT_LEAVE_REQUEST') {
      return jsonResponse({ ok: false, error: 'Unauthorized sync request' });
    }
    upsertLeaveRequest(payload.request, properties.sheetId);
    return jsonResponse({ ok: true });
  } catch (error) {
    console.error(error);
    return jsonResponse({ ok: false, error: 'Sync failed' });
  }
}

function upsertLeaveRequest(request, sheetId) {
  const spreadsheet = SpreadsheetApp.openById(sheetId);
  let sheet = spreadsheet.getSheetByName(LEAVE_SHEET_NAME);
  if (!sheet) sheet = spreadsheet.insertSheet(LEAVE_SHEET_NAME);
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(SHEET_HEADERS);
    sheet.setFrozenRows(1);
    sheet.getRange(1, 1, 1, SHEET_HEADERS.length).setFontWeight('bold');
  }

  const row = [
    request.id, request.employeeName, request.employeeEmail, request.managerEmail,
    request.leaveType, request.startDate, request.endDate, request.totalDays,
    request.reason, request.status, request.managerComment || '', request.requestedAt,
    request.decidedAt || '', new Date().toISOString()
  ];
  const rowCount = sheet.getLastRow();
  let targetRow = 0;
  if (rowCount > 1) {
    const ids = sheet.getRange(2, 1, rowCount - 1, 1).getValues();
    for (let i = 0; i < ids.length; i++) {
      if (String(ids[i][0]) === String(request.id)) {
        targetRow = i + 2;
        break;
      }
    }
  }
  if (targetRow) {
    sheet.getRange(targetRow, 1, 1, row.length).setValues([row]);
  } else {
    sheet.getRange(rowCount + 1, 1, 1, row.length).setValues([row]);
  }
  sheet.autoResizeColumns(1, SHEET_HEADERS.length);
}

function getProperties() {
  const properties = PropertiesService.getScriptProperties();
  const apiBaseUrl = properties.getProperty('SPRING_API_BASE_URL');
  const secret = properties.getProperty('INTEGRATION_SECRET');
  const sheetId = properties.getProperty('LEAVE_SHEET_ID');
  if (!apiBaseUrl || !secret || !sheetId) {
    throw new Error('Set SPRING_API_BASE_URL, INTEGRATION_SECRET, and LEAVE_SHEET_ID in Script Properties.');
  }
  return { apiBaseUrl: apiBaseUrl.replace(/\/$/, ''), secret: secret, sheetId: sheetId };
}

function hmacHex(value, secret) {
  const bytes = Utilities.computeHmacSha256Signature(value, secret);
  return bytes.map(function (byte) {
    const unsigned = byte < 0 ? byte + 256 : byte;
    return ('0' + unsigned.toString(16)).slice(-2);
  }).join('');
}

function hidden(name, value) {
  return '<input type="hidden" name="' + escapeHtml(name) + '" value="' + escapeHtml(value) + '">';
}

function htmlPage(title, body) {
  const content = '<!doctype html><html><head><base target="_top"><meta name="viewport" content="width=device-width,initial-scale=1">'
    + '<style>body{font-family:Arial,sans-serif;background:#f5f7f6;color:#17212b;margin:0;padding:24px}.card{max-width:580px;margin:48px auto;background:#fff;padding:32px;border-radius:12px;box-shadow:0 8px 24px #00000012}h1{margin-top:0}label{display:block;font-weight:bold;margin:18px 0 7px}textarea{box-sizing:border-box;width:100%;padding:10px;border:1px solid #bbc7c0;border-radius:6px;font:inherit}button{border:0;border-radius:6px;color:white;padding:11px 16px;font-weight:bold;font-size:1rem;margin-top:18px;cursor:pointer}.hint{color:#607068;font-size:.88rem;margin-top:20px}</style>'
    + '</head><body><main class="card"><h1>' + escapeHtml(title) + '</h1>' + body + '</main></body></html>';
  return HtmlService.createHtmlOutput(content).setTitle(title);
}

function jsonResponse(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}

function safeJson(value) {
  try { return JSON.parse(value); } catch (error) { return {}; }
}

function escapeHtml(value) {
  return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}
