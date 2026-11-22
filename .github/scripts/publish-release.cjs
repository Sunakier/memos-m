// Adapted from PaperWorld's sealed, resumable GitHub Release publication.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { createStore, validateRecord } = require('./release-state.cjs');

module.exports = async ({ github, context, core }) => {
  const repo = context.repo;
  const store = createStore(github, repo);
  const recordFile = process.env.VERSION_RECORD;
  if (!/^allocations\/[a-f0-9]{64}\.json$/.test(recordFile || '')) throw new Error('Missing version allocation');
  const record = await store.read(recordFile, await store.head());
  if (!record) throw new Error('Version allocation was lost; refusing to publish');
  validateRecord(record, {
    channel: process.env.RELEASE_CHANNEL, sha: process.env.SOURCE_SHA,
    name: process.env.VERSION_NAME, tag: process.env.RELEASE_TAG,
  });
  if (String(record.code) !== process.env.VERSION_CODE) throw new Error('Version code differs from allocation');
  if (execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim() !== record.sha) {
    throw new Error('Publisher source differs from the allocation');
  }
  if (record.complete) {
    core.notice('This allocation is already published; leaving its assets unchanged.');
    core.setOutput('published', false);
    return;
  }
  const names = [
    `MemosM-${record.name}-arm64-v8a.apk`,
    `MemosM-${record.name}-armeabi-v7a.apk`,
    `MemosM-${record.name}-mapping.txt.gz`,
    `SHA256SUMS-${record.name}.txt`,
  ];
  if (record.branch) names.push(`MemosM-${record.name}-debug.zip`);
  const directory = 'tmp/distribution';
  const files = names.map(name => path.join(directory, name));
  const assets = files.map(file => {
    const bytes = fs.readFileSync(file);
    if (!bytes.length) throw new Error(`Empty artifact: ${file}`);
    return { name: path.basename(file), size: bytes.length,
      digest: `sha256:${crypto.createHash('sha256').update(bytes).digest('hex')}` };
  }).sort((a, b) => a.name.localeCompare(b.name));
  const manifest = { schema: 1, source: record.sha, channel: record.channel,
    versionName: record.name, versionCode: record.code, tag: record.tag,
    applicationId: process.env.EXPECTED_APP_ID, signerSha256: process.env.EXPECTED_SIGNER_SHA256, assets,
    ...(record.branch ? { branch: record.branch } : {}) };
  // CI and tagged releases hold the same concurrency group through completion.
  await store.transact(async (state, read) => {
    const current = await read(recordFile);
    if (!current || current.code !== record.code) throw new Error('Allocation changed');
    if (record.code < state.publishedCode) throw new Error('A newer publication exists');
    if (current.manifest && JSON.stringify(current.manifest) !== JSON.stringify(manifest)) {
      throw new Error('Artifact hashes differ from this sealed release; publish a new commit/version');
    }
    current.manifest = manifest;
    state.publishedCode = Math.max(state.publishedCode, record.code);
    return { files: { [recordFile]: current }, message: `Seal artifacts for ${record.tag}` };
  });

  async function tagCommit() {
    let object = (await github.rest.git.getRef({ ...repo, ref: `tags/${record.tag}` })).data.object;
    for (let depth = 0; object.type === 'tag' && depth < 5; depth++) {
      object = (await github.rest.git.getTag({ ...repo, tag_sha: object.sha })).data.object;
    }
    if (object.type !== 'commit') throw new Error('Release tag does not point to a commit');
    return object.sha;
  }
  let tagSha;
  try { tagSha = await tagCommit(); } catch (error) {
    if (error.status !== 404 || record.channel !== 'preview') throw error;
    await github.rest.git.createRef({ ...repo, ref: `refs/tags/${record.tag}`, sha: record.sha });
    tagSha = await tagCommit();
  }
  if (tagSha !== record.sha) throw new Error('Release tag moved; refusing to publish');
  let release;
  try { release = (await github.rest.repos.getReleaseByTag({ ...repo, tag: record.tag })).data; }
  catch (error) { if (error.status !== 404) throw error; }
  const marker = `<!-- memosm-source:${record.sha};code:${record.code} -->`;
  const body = `${record.channel === 'stable' ? '正式版' : '自动 CI 预览版'} **MemosM ${record.name}**\n\n` +
    `- versionCode: ${record.code}\n- 源码：${record.sha}\n- 包名：top.lazyerpaper.memosm\n` +
    (record.branch ? `- 分支：${record.branch}\n- Debug ZIP 包含单独安装的 \`top.lazyerpaper.memosm.debug\` APK。\n` : '') +
    '- ARM64 推荐；armeabi-v7a 适用于较老的 32 位 ARM 设备。\n' +
    '- 沿用正式签名；同包名预览版与正式版共用递增编号，可安装编号更高的后续版本。\n' +
    '- 附带 R8 mapping、SHA-256 校验和及 release-manifest.json。\n' +
    '- 通过定向 JVM 测试、R8 构建及签名/版本/ABI 校验；未执行设备交互测试。\n\n' + marker;
  if (release && (!release.body?.includes(marker) || release.prerelease !== (record.channel === 'preview'))) {
    throw new Error('An unrelated release already owns this tag');
  }
  if (!release) release = (await github.rest.repos.createRelease({
    ...repo, tag_name: record.tag, target_commitish: record.sha, name: `MemosM ${record.name}`, body,
    draft: true, prerelease: record.channel === 'preview', make_latest: 'false',
  })).data;
  const manifestFile = path.join(directory, 'release-manifest.json');
  fs.writeFileSync(manifestFile, `${JSON.stringify(manifest, null, 2)}\n`);
  const remote = await github.paginate(github.rest.repos.listReleaseAssets,
    { ...repo, release_id: release.id, per_page: 100 });
  for (const file of [...files, manifestFile]) {
    const bytes = fs.readFileSync(file), name = path.basename(file);
    const digest = `sha256:${crypto.createHash('sha256').update(bytes).digest('hex')}`;
    let asset = remote.find(item => item.name === name);
    if (!asset) {
      if (!release.draft) throw new Error('Published release is missing an immutable asset');
      const contentType = {
        '.json': 'application/json',
        '.gz': 'application/gzip',
        '.txt': 'text/plain',
        '.zip': 'application/zip',
        '.apk': 'application/vnd.android.package-archive',
      }[path.extname(name)];
      asset = (await github.rest.repos.uploadReleaseAsset({
        ...repo, release_id: release.id, name, data: bytes,
        headers: { 'content-type': contentType, 'content-length': bytes.length },
      })).data;
    }
    if (asset.state !== 'uploaded' || asset.size !== bytes.length || asset.digest !== digest) {
      throw new Error(`Release asset checksum mismatch: ${name}; preserving existing bytes`);
    }
  }
  if (release.draft) release = (await github.rest.repos.updateRelease({
    ...repo, release_id: release.id, draft: false, make_latest: record.channel === 'stable' ? 'true' : 'false',
  })).data;
  await store.transact(async (state, read) => {
    const current = await read(recordFile);
    if (record.code < state.publishedCode) throw new Error('Publication lock was violated');
    current.complete = true;
    current.publishedRunId = context.runId;
    if (record.channel === 'stable') state.latestStable = record.name;
    return { files: { [recordFile]: current }, message: `Complete ${record.tag}` };
  });
  core.setOutput('published', true);
  await core.summary.addHeading('Published MemosM').addRaw(
    `[${record.name}](${release.html_url}) — versionCode ${record.code}, source \`${record.sha}\`.\n\n` +
    'Signed APKs, R8 mapping and checksums were verified before publication.\n').write();
};
