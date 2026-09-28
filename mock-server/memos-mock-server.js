// Minimal Memos mock server for emulator debugging of memos-m.
// Reports version 0.28.0 so the app uses the v0.28 endpoint set (memoId
// forwarded). Implements server-side idempotency for memo and attachment
// creates, and records every idempotent create to a log file so tests can
// assert de-duplication.
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8787;
const LOG = path.join(__dirname, 'mock-server-requests.log');

// --- in-memory state -------------------------------------------------------
const user = {
  name: 'users/1',
  username: 'testuser',
  displayName: 'Test User',
  role: 'ADMIN',
  state: 'NORMAL',
  email: 'test@example.com',
  createTime: new Date().toISOString(),
  updateTime: new Date().toISOString(),
};
const accessToken = 'mock-access-token-' + Date.now();
const personalToken = 'mock-personal-token-' + Date.now();

// memoId -> memo object; insertion order preserved for listing
const memos = new Map();
let memoSeq = 0;
// attachmentId -> attachment object
const attachments = new Map();
let attachmentSeq = 0;

function log(line) {
  const entry = new Date().toISOString() + ' ' + line + '\n';
  fs.appendFileSync(LOG, entry);
  process.stdout.write(entry);
}

function send(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json' });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => resolve(data));
    req.on('error', () => resolve(data));
  });
}

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://localhost');
  const p = u.pathname;
  const method = req.method;

  // --- instance profile (reachability probe + version detection) -----------
  if (method === 'GET' && p === '/api/v1/instance/profile') {
    log('PROBE instance/profile');
    return send(res, 200, { version: '0.28.0', mode: 'prod', owner: user.name });
  }

  // --- auth ----------------------------------------------------------------
  if (method === 'POST' && p === '/api/v1/auth/signin') {
    return send(res, 200, {
      user,
      accessToken,
      accessTokenExpiresAt: new Date(Date.now() + 86400e3).toISOString(),
    });
  }
  if (method === 'GET' && p === '/api/v1/auth/me') {
    return send(res, 200, { user });
  }
  if (method === 'POST' && p === '/api/v1/auth/refresh') {
    return send(res, 200, { accessToken, expiresAt: new Date(Date.now() + 86400e3).toISOString() });
  }
  if (method === 'POST' && p.endsWith('/personalAccessTokens')) {
    return send(res, 200, {
      personalAccessToken: { name: user.name + '/personalAccessTokens/1', description: 'MemosM' },
      token: personalToken,
    });
  }

  // --- memos ---------------------------------------------------------------
  if (method === 'GET' && p === '/api/v1/memos') {
    return send(res, 200, { memos: Array.from(memos.values()).reverse(), nextPageToken: null });
  }
  if (method === 'POST' && p === '/api/v1/memos') {
    const body = JSON.parse(await readBody(req) || '{}');
    const memoId = u.searchParams.get('memoId');
    if (memoId && memos.has(memoId)) {
      const existing = memos.get(memoId);
      log(`IDEMPOTENT-HIT memo memoId=${memoId} -> ${existing.name}`);
      return send(res, 200, existing); // deduplicated
    }
    memoSeq += 1;
    const name = 'memos/' + (memoId || String(memoSeq));
    const memo = {
      name,
      state: 'NORMAL',
      creator: user.name,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
      content: body.content || '',
      visibility: body.visibility || 'PRIVATE',
      pinned: body.pinned || false,
      attachments: body.attachments || [],
    };
    if (memoId) memos.set(memoId, memo); else memos.set('seq' + memoSeq, memo);
    log(`CREATE memo memoId=${memoId || 'none'} -> ${name} content=${JSON.stringify(memo.content).slice(0, 60)}`);
    return send(res, 200, memo);
  }
  // get/patch single memo
  const memoMatch = p.match(/^\/api\/v1\/(memos\/[^/]+)$/);
  if (memoMatch && method === 'GET') {
    const found = Array.from(memos.values()).find((m) => m.name === memoMatch[1]);
    return found ? send(res, 200, found) : send(res, 404, { error: 'not found' });
  }

  // --- attachments ---------------------------------------------------------
  if (method === 'GET' && p === '/api/v1/attachments') {
    return send(res, 200, { attachments: Array.from(attachments.values()), nextPageToken: null, totalSize: attachments.size });
  }
  if (method === 'POST' && p === '/api/v1/attachments') {
    const raw = await readBody(req);
    let body = {};
    try { body = JSON.parse(raw || '{}'); } catch (e) { /* streaming bodies are JSON too */ }
    const attachmentId = u.searchParams.get('attachmentId');
    if (attachmentId && attachments.has(attachmentId)) {
      const existing = attachments.get(attachmentId);
      log(`IDEMPOTENT-HIT attachment attachmentId=${attachmentId} -> ${existing.name}`);
      return send(res, 200, existing); // deduplicated
    }
    attachmentSeq += 1;
    const name = 'attachments/' + (attachmentId || String(attachmentSeq));
    const att = {
      name,
      filename: body.filename || 'file',
      type: body.type || 'application/octet-stream',
      mimeType: body.type || 'application/octet-stream',
      size: String((body.content || '').length),
      createTime: new Date().toISOString(),
    };
    if (attachmentId) attachments.set(attachmentId, att); else attachments.set('seq' + attachmentSeq, att);
    log(`CREATE attachment attachmentId=${attachmentId || 'none'} -> ${name} filename=${att.filename} bytes=${att.size}`);
    return send(res, 200, att);
  }

  // --- stats / misc used by main screen (best effort) -----------------------
  if (method === 'GET' && p === '/api/v1/activities') {
    return send(res, 200, { activities: [], nextPageToken: null });
  }
  const settingMatch = p.match(/^\/api\/v1\/instance\/settings\/([A-Z_]+)$/);
  if (method === 'GET' && settingMatch) {
    return send(res, 200, { name: 'instance/settings/' + settingMatch[1] });
  }
  if (method === 'GET' && /\/api\/v1\/users\/[^/]+\/stats/.test(p)) {
    return send(res, 200, { memoDisplayTimestamps: [], pinnedMemos: [], totalMemoCount: memos.size });
  }
  if (method === 'GET' && /\/api\/v1\/users/.test(p)) {
    return send(res, 200, { users: [user], nextPageToken: null, totalSize: 1 });
  }
  const userMatch = p.match(/^\/api\/v1\/(users\/[^/]+)$/);
  if (userMatch && method === 'GET') {
    return send(res, 200, user);
  }

  log(`UNHANDLED ${method} ${p}${u.search}`);
  send(res, 404, { error: 'not implemented: ' + method + ' ' + p });
});

server.listen(PORT, '127.0.0.1', () => {
  log(`mock memos server listening on 127.0.0.1:${PORT} (version 0.28.0)`);
});
