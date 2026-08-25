import test from 'node:test';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  bumpSemVer,
  parseArgs,
  parseConventionalCommits,
  buildReleaseDocMarkdown,
  updateRootChangelog,
  getAndroidVersion,
  getWebappVersion
} from '../scripts/release.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');

test('bumpSemVer correctly increments versions', () => {
  assert.equal(bumpSemVer('1.1.0', 'patch'), '1.1.1');
  assert.equal(bumpSemVer('1.1.0', 'minor'), '1.2.0');
  assert.equal(bumpSemVer('1.1.0', 'major'), '2.0.0');
  // Normalizes 2-part versions like "1.1" -> "1.1.0" -> "1.1.1"
  assert.equal(bumpSemVer('1.1', 'patch'), '1.1.1');
  assert.equal(bumpSemVer('1.1', 'minor'), '1.2.0');
  // Custom explicit version string
  assert.equal(bumpSemVer('1.1.0', '2.5.0'), '2.5.0');
});

test('parseArgs parses flags correctly', () => {
  const args = parseArgs(['--target=android', '--bump=minor', '--dry-run', '--notes=Test release note']);
  assert.equal(args.target, 'android');
  assert.equal(args.bump, 'minor');
  assert.equal(args.dryRun, true);
  assert.equal(args.notes, 'Test release note');
});

test('parseConventionalCommits categorizes commit messages', () => {
  const sampleCommits = [
    { subject: 'feat(auth): add google login', hash: 'abc1' },
    { subject: 'fix(map): resolve marker jitter', hash: 'abc2' },
    { subject: 'perf: optimize firestore query', hash: 'abc3' },
    { subject: 'refactor: simplify state handling', hash: 'abc4' },
    { subject: 'docs: update spec document', hash: 'abc5' },
    { subject: 'chore: bump dependencies', hash: 'abc6' },
    { subject: 'random unformatted message', hash: 'abc7' }
  ];

  const result = parseConventionalCommits(sampleCommits);
  assert.equal(result.features.length, 1);
  assert.equal(result.features[0], 'add google login');
  assert.equal(result.fixes.length, 1);
  assert.equal(result.fixes[0], 'resolve marker jitter');
  assert.equal(result.performance.length, 1);
  assert.equal(result.performance[0], 'optimize firestore query');
  assert.equal(result.refactors.length, 1);
  assert.equal(result.refactors[0], 'simplify state handling');
  assert.equal(result.docs.length, 1);
  assert.equal(result.docs[0], 'update spec document');
  assert.equal(result.chores.length, 1);
  assert.equal(result.chores[0], 'bump dependencies');
  assert.equal(result.others.length, 1);
  assert.equal(result.others[0], 'random unformatted message');
});

test('buildReleaseDocMarkdown creates formatted markdown release notes', () => {
  const md = buildReleaseDocMarkdown({
    platform: 'webapp',
    version: '0.2.0',
    date: '2026-08-25',
    highlights: ['Điểm nổi bật 1', 'Điểm nổi bật 2'],
    categorized: {
      features: ['Tính năng mới A'],
      fixes: ['Sửa lỗi B']
    }
  });

  assert.ok(md.includes('# Release Notes - WebApp v0.2.0'));
  assert.ok(md.includes('**Ngày phát hành:** 2026-08-25'));
  assert.ok(md.includes('## 🌟 Điểm nổi bật (Highlights)'));
  assert.ok(md.includes('- Điểm nổi bật 1'));
  assert.ok(md.includes('### ✨ Tính năng mới (Features)'));
  assert.ok(md.includes('- Tính năng mới A'));
  assert.ok(md.includes('### 🐛 Sửa lỗi (Bug Fixes)'));
  assert.ok(md.includes('- Sửa lỗi B'));
});

test('getAndroidVersion and getWebappVersion read valid configurations', () => {
  const android = getAndroidVersion();
  assert.ok(typeof android.versionCode === 'number' && android.versionCode > 0);
  assert.ok(typeof android.versionName === 'string' && android.versionName.length > 0);

  const web = getWebappVersion();
  assert.ok(typeof web === 'string' && /^\d+\.\d+\.\d+$/.test(web));
});

test('CLI script execution with --dry-run completes successfully with exit code 0', () => {
  const output = execSync('node scripts/release.mjs --target=webapp --bump=patch --dry-run', {
    cwd: ROOT_DIR,
    encoding: 'utf-8'
  });

  assert.ok(output.includes('[Release Manager]'));
  assert.ok(output.includes('Đang xử lý phát hành cho: WEBAPP'));
  assert.ok(output.includes('[DRY RUN]'));
});
