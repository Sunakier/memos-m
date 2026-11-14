// Resume publication using the sealed APKs, mapping and checksums when available.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { createStore } = require('./release-state.cjs');

module.exports = async ({ github, context, core }) => {
  core.setOutput('restored', false);
  const store = createStore(github, context.repo);
  const record = await store.read(process.env.VERSION_RECORD, await store.head());
  if (!record || record.sha !== process.env.SOURCE_SHA) throw new Error('Invalid version allocation');
  if (!record.manifest?.assets?.length) return;
  let release;
  try { release = (await github.rest.repos.getReleaseByTag({ ...context.repo, tag: record.tag })).data; }
  catch (error) { if (error.status === 404) return; throw error; }
  const assets = await github.paginate(github.rest.repos.listReleaseAssets, { ...context.repo, release_id: release.id, per_page: 100 });
  if (!record.manifest.assets.every(expected => assets.some(asset => asset.name === expected.name))) return;
  fs.mkdirSync('tmp/distribution', { recursive: true });
  for (const expected of record.manifest.assets) {
    if (path.basename(expected.name) !== expected.name) throw new Error('Invalid artifact name in ledger');
    const asset = assets.find(item => item.name === expected.name);
    const response = await github.rest.repos.getReleaseAsset({
      ...context.repo, asset_id: asset.id, headers: { accept: 'application/octet-stream' },
    });
    const bytes = Buffer.from(response.data);
    const digest = `sha256:${crypto.createHash('sha256').update(bytes).digest('hex')}`;
    if (bytes.length !== expected.size || digest !== expected.digest) throw new Error('Stored APK differs from sealed artifact hash');
    fs.writeFileSync(path.join('tmp/distribution', expected.name), bytes);
  }
  core.setOutput('restored', true);
  core.info('Restored sealed assets for retry; APK version and certificate checks still run.');
};
