const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const { BRANCH, FORMAT, MAX_CODE, compareVersions, recordPath, createStore, validateRecord } = require('./release-state.cjs');
const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trim();

module.exports = async ({ github, context, core }) => {
  const properties = Object.fromEntries(fs.readFileSync('version.properties', 'utf8').split(/\r?\n/)
    .filter(line => /^[A-Za-z]+=/.test(line)).map(line => line.split('=').map(value => value.trim())));
  const base = properties.versionName;
  compareVersions(base, base);
  const localCode = Number(properties.versionCode);
  if (!Number.isSafeInteger(localCode) || localCode < 1 || localCode >= MAX_CODE) throw new Error('Invalid versionCode migration floor');
  const sha = git('rev-parse', 'HEAD');
  if (!/^[a-f0-9]{40}$/.test(sha)) throw new Error('Invalid source commit');
  const stable = process.env.RELEASE_CHANNEL === 'stable';
  const branch = context.payload.repository.default_branch;
  const pr = context.eventName === 'pull_request';
  const distributable = stable || (!pr && context.ref === `refs/heads/${branch}` && process.env.DISTRIBUTE === 'true');
  const output = data => {
    for (const [key, value] of Object.entries(data)) core.setOutput(key, String(value));
    core.info(`Version ${data.name} (${data.code}), source ${sha}; distribute=${data.publish}, build=${data.build}`);
  };
  if (!distributable) {
    const suffix = pr ? `pr.${context.payload.pull_request.number}` : 'check';
    output({ base, name: `${base}-${suffix}.${sha.slice(0, 12)}`, code: localCode, sha, publish: false, build: true, record: '', tag: '' });
    return;
  }
  // Resolve source once, and use this exact commit in every dependent job.
  const defaultHead = (await github.rest.repos.getBranch({ ...context.repo, branch })).data.commit.sha;
  if (!stable && sha !== defaultHead) {
    core.notice('A newer default-branch commit exists; this run validates source but does not distribute an obsolete preview.');
    output({ base, name: `${base}-check.${sha.slice(0, 12)}`, code: localCode, sha, publish: false, build: true, record: '', tag: '' });
    return;
  }
  const channel = stable ? 'stable' : 'preview';
  const tag = stable ? context.ref.replace(/^refs\/tags\//, '') : null;
  if (stable) {
    if (tag !== `v${base}` || context.ref !== `refs/tags/v${base}`) {
      throw new Error(`Release tag must exactly match version.properties: expected v${base}`);
    }
    const tagSha = git('rev-parse', `${context.ref}^{commit}`);
    if (tagSha !== sha) throw new Error('Tag does not resolve to the checked out source');
    // The publishing policy only accepts reviewed commits already merged to the default branch.
    const comparison = (await github.rest.repos.compareCommitsWithBasehead({ ...context.repo, basehead: `${sha}...${defaultHead}` })).data;
    if (!['ahead', 'identical'].includes(comparison.status)) throw new Error('Release source must be on the default branch history');
  }
  // A new manual dispatch is a new build request; rerun-attempts retain runId.
  // This also distinguishes a path-filtered push from a full manual build at the same SHA.
  const path = recordPath(channel, stable ? tag : String(context.runId));
  const store = createStore(github, context.repo);
  const bootstrap = async () => {
    const releases = await github.paginate(github.rest.repos.listReleases, { ...context.repo, per_page: 100 });
    let floor = localCode;
    // Legacy CI tags are retained even after assets are pruned. No commit-distance arithmetic.
    for (const ref of git('tag', '--list', 'ci-*').split('\n')) {
      const match = /^ci-(\d+)$/.exec(ref);
      if (match) floor = Math.max(floor, Number(match[1]));
    }
    let latestStable = '0.0.0';
    for (const release of releases.filter(item => !item.draft)) {
      const ci = /^ci-(\d+)$/.exec(release.tag_name);
      if (ci) floor = Math.max(floor, Number(ci[1]));
      // Old release notes explicitly record versionCode, including stable APKs.
      const code = /versionCode[：:\s=`]*(\d+)/i.exec(release.body || '');
      if (code) floor = Math.max(floor, Number(code[1]));
      const version = /^v?(\d+\.\d+\.\d+)$/.exec(release.tag_name)?.[1];
      if (!release.prerelease && version && compareVersions(version, latestStable) > 0) latestStable = version;
    }
    return { schema: FORMAT, lastCode: floor, publishedCode: floor, latestStable, stableReserved: latestStable };
  };
  const record = await store.transact(async (state, read) => {
    const existing = await read(path);
    if (existing) {
      validateRecord(existing, { channel, sha, name: stable ? base : `${base}-ci-${existing.code}`, tag: stable ? tag : `ci-${existing.code}` });
      if (!existing.complete && existing.code < state.publishedCode) {
        throw new Error('A newer build was already distributed. Publish a new commit/version instead of completing this obsolete build.');
      }
      return { result: existing };
    }
    if (stable && compareVersions(base, state.stableReserved) <= 0) {
      throw new Error(`Choose a version greater than ${state.stableReserved}; published/reserved versions cannot be reused`);
    }
    const code = state.lastCode + 1;
    if (!Number.isSafeInteger(code) || code > MAX_CODE) throw new Error('Android versionCode range exhausted');
    state.lastCode = code;
    if (stable) state.stableReserved = base;
    const record = { schema: FORMAT, channel, sha, code, name: stable ? base : `${base}-ci-${code}`,
      tag: stable ? tag : `ci-${code}`, runId: context.runId, complete: false };
    return { files: { [path]: record }, message: `Allocate ${record.tag} (${code}) for ${sha}`, result: record };
  }, bootstrap);
  output({ base, name: record.name, code: record.code, tag: record.tag, sha, publish: true, build: !record.complete, record: path });
  await core.summary.addHeading('Version allocation').addTable([
    [{ data: 'Version', header: true }, { data: 'Code', header: true }, { data: 'Commit', header: true }],
    [record.name, String(record.code), sha],
  ]).addRaw(`\nLedger: \`${BRANCH}/${path}\`. Reruns reuse this allocation.\n`).write();
};
