// A fast-forward-only Git ref is the compare-and-swap boundary for all releases.
const BRANCH = 'automation/release-state';
const FORMAT = 1;
const MAX_CODE = 2100000000;

function compareVersions(left, right) {
  const parse = value => {
    if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value)) {
      throw new Error(`Expected an exact X.Y.Z version: ${value}`);
    }
    return value.split('.').map(BigInt);
  };
  const a = parse(left), b = parse(right);
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return a[i] > b[i] ? 1 : -1;
  return 0;
}

function recordPath(channel, identity) {
  const digest = require('node:crypto').createHash('sha256').update(`${channel}:${identity}`).digest('hex');
  return `allocations/${digest}.json`;
}

function createStore(github, repo) {
  async function head() {
    try {
      return (await github.rest.git.getRef({ ...repo, ref: `heads/${BRANCH}` })).data.object.sha;
    } catch (error) {
      if (error.status === 404) return null;
      throw error;
    }
  }
  async function read(path, ref) {
    if (!ref) return null;
    try {
      const { data } = await github.rest.repos.getContent({ ...repo, path, ref });
      if (data.type !== 'file' || data.encoding !== 'base64') throw new Error(`Invalid ledger file: ${path}`);
      return JSON.parse(Buffer.from(data.content, 'base64').toString('utf8'));
    } catch (error) {
      if (error.status === 404) return null;
      throw error;
    }
  }
  async function commit(previous, files, message) {
    const baseTree = previous ? (await github.rest.git.getCommit({ ...repo, commit_sha: previous })).data.tree.sha : undefined;
    const { data: tree } = await github.rest.git.createTree({
      ...repo, ...(baseTree ? { base_tree: baseTree } : {}),
      tree: Object.entries(files).map(([path, value]) => ({
        path, mode: '100644', type: 'blob', content: `${JSON.stringify(value, null, 2)}\n`,
      })),
    });
    const { data: next } = await github.rest.git.createCommit({
      ...repo, message, tree: tree.sha, parents: previous ? [previous] : [],
    });
    try {
      if (previous) {
        await github.rest.git.updateRef({ ...repo, ref: `heads/${BRANCH}`, sha: next.sha, force: false });
      } else {
        await github.rest.git.createRef({ ...repo, ref: `refs/heads/${BRANCH}`, sha: next.sha });
      }
      return true;
    } catch (error) {
      // Sibling commits cannot both fast-forward the ref. Re-read and recalculate.
      if ([409, 422].includes(error.status) && await head() !== previous) return false;
      throw error;
    }
  }
  async function transact(mutator, bootstrap) {
    for (let attempt = 0; attempt < 12; attempt++) {
      const previous = await head();
      const state = previous ? await read('state.json', previous) : await bootstrap?.();
      if (!state || state.schema !== FORMAT) throw new Error('Missing or unsupported release ledger; do not reset the counter');
      if (!Number.isSafeInteger(state.lastCode) || !Number.isSafeInteger(state.publishedCode) ||
          state.publishedCode < 1 || state.publishedCode > state.lastCode || state.lastCode > MAX_CODE) {
        throw new Error('Invalid release ledger counter; restore its history instead of resetting it');
      }
      const change = await mutator(state, path => read(path, previous));
      if (!change.files) return change.result;
      if (await commit(previous, { 'state.json': state, ...change.files }, change.message)) return change.result;
    }
    throw new Error('Release ledger is busy; rerun this request without changing its version or tag');
  }
  return { head, read, transact };
}

function validateRecord(record, { channel, sha, name, tag }) {
  if (record.channel !== channel || record.sha !== sha || record.name !== name || record.tag !== tag) {
    throw new Error('This release identity is already bound to different source/version metadata');
  }
}

module.exports = { BRANCH, FORMAT, MAX_CODE, compareVersions, recordPath, createStore, validateRecord };
