#!/usr/bin/env node

/**
 * Release Automation Script for MAPSUPERVISION-Firebase
 * Supports Android App & WebApp version bumping, Conventional Commits parsing,
 * Release Notes generation (docs/releases/), root CHANGELOG.md update, and runtime metadata.
 */

import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');

const PATHS = {
  androidGradle: path.join(ROOT_DIR, 'app', 'build.gradle.kts'),
  webappPackageJson: path.join(ROOT_DIR, 'webapp', 'package.json'),
  webappPublicVersion: path.join(ROOT_DIR, 'webapp', 'public', 'version.json'),
  rootChangelog: path.join(ROOT_DIR, 'CHANGELOG.md'),
  docsReleasesAndroid: path.join(ROOT_DIR, 'docs', 'releases', 'android'),
  docsReleasesWebapp: path.join(ROOT_DIR, 'docs', 'releases', 'webapp'),
};

// Parse command line arguments
export function parseArgs(argv = process.argv.slice(2)) {
  const options = {
    target: null, // 'android' | 'webapp' | 'all'
    bump: null, // 'patch' | 'minor' | 'major' | 'x.y.z'
    dryRun: false,
    interactive: false,
    notes: null,
    gitTag: false,
    gitCommit: false,
  };

  for (const arg of argv) {
    if (arg === '--dry-run') options.dryRun = true;
    else if (arg === '--interactive' || arg === '-i') options.interactive = true;
    else if (arg === '--git-tag') options.gitTag = true;
    else if (arg === '--git-commit') options.gitCommit = true;
    else if (arg.startsWith('--target=')) options.target = arg.split('=')[1].trim();
    else if (arg.startsWith('--bump=')) options.bump = arg.split('=')[1].trim();
    else if (arg.startsWith('--notes=')) options.notes = arg.split('=').slice(1).join('=').trim();
  }

  return options;
}

// SemVer utility
export function bumpSemVer(currentVersion, bumpType) {
  // Normalize versions like "1.1" -> "1.1.0"
  const parts = currentVersion.split('.').map(p => parseInt(p, 10));
  while (parts.length < 3) parts.push(0);

  let [major, minor, patch] = parts;
  if (isNaN(major)) major = 1;
  if (isNaN(minor)) minor = 0;
  if (isNaN(patch)) patch = 0;

  if (bumpType === 'major') {
    return `${major + 1}.0.0`;
  } else if (bumpType === 'minor') {
    return `${major}.${minor + 1}.0`;
  } else if (bumpType === 'patch') {
    return `${major}.${minor}.${patch + 1}`;
  } else if (/^\d+\.\d+(\.\d+)?$/.test(bumpType)) {
    // Custom explicit version
    return bumpType;
  }
  return `${major}.${minor}.${patch + 1}`;
}

// Android Gradle parsing & updating
export function getAndroidVersion() {
  if (!fs.existsSync(PATHS.androidGradle)) {
    throw new Error(`Android gradle file not found: ${PATHS.androidGradle}`);
  }
  const content = fs.readFileSync(PATHS.androidGradle, 'utf-8');
  const codeMatch = content.match(/versionCode\s*=\s*(\d+)/);
  const nameMatch = content.match(/versionName\s*=\s*["']([^"']+)["']/);

  return {
    versionCode: codeMatch ? parseInt(codeMatch[1], 10) : 1,
    versionName: nameMatch ? nameMatch[1] : '1.0.0',
  };
}

export function updateAndroidVersion(newCode, newName, dryRun = false) {
  const content = fs.readFileSync(PATHS.androidGradle, 'utf-8');
  let updated = content.replace(/versionCode\s*=\s*(\d+)/, `versionCode = ${newCode}`);
  updated = updated.replace(/versionName\s*=\s*["']([^"']+)["']/, `versionName = "${newName}"`);

  if (!dryRun) {
    fs.writeFileSync(PATHS.androidGradle, updated, 'utf-8');
  }
  return { newCode, newName };
}

// WebApp package.json parsing & updating
export function getWebappVersion() {
  if (!fs.existsSync(PATHS.webappPackageJson)) {
    throw new Error(`Webapp package.json not found: ${PATHS.webappPackageJson}`);
  }
  const pkg = JSON.parse(fs.readFileSync(PATHS.webappPackageJson, 'utf-8'));
  return pkg.version || '0.1.0';
}

export function updateWebappVersion(newVersion, dryRun = false) {
  const raw = fs.readFileSync(PATHS.webappPackageJson, 'utf-8');
  const pkg = JSON.parse(raw);
  pkg.version = newVersion;

  if (!dryRun) {
    fs.writeFileSync(PATHS.webappPackageJson, JSON.stringify(pkg, null, 2) + '\n', 'utf-8');
  }
  return newVersion;
}

// Git commits & changelog extraction
export function getGitCommits(fromRef = null) {
  try {
    const range = fromRef ? `${fromRef}..HEAD` : '-n 30';
    const log = execSync(`git log ${range} --pretty=format:"%h|%s|%an|%ad" --date=short`, {
      cwd: ROOT_DIR,
      encoding: 'utf-8',
    });
    const lines = log.split('\n').filter(Boolean);
    return lines.map(line => {
      const [hash, subject, author, date] = line.split('|');
      return { hash, subject, author, date };
    });
  } catch (err) {
    return [];
  }
}

export function getLatestGitCommitHash() {
  try {
    return execSync('git rev-parse --short HEAD', { cwd: ROOT_DIR, encoding: 'utf-8' }).trim();
  } catch {
    return 'unknown';
  }
}

export function parseConventionalCommits(commits) {
  const categorized = {
    features: [],
    fixes: [],
    performance: [],
    refactors: [],
    docs: [],
    chores: [],
    others: [],
  };

  for (const c of commits) {
    const sub = c.subject.trim();
    if (/^feat(\(.*\))?:/i.test(sub)) {
      categorized.features.push(sub.replace(/^feat(\(.*\))?:\s*/i, ''));
    } else if (/^fix(\(.*\))?:/i.test(sub)) {
      categorized.fixes.push(sub.replace(/^fix(\(.*\))?:\s*/i, ''));
    } else if (/^perf(\(.*\))?:/i.test(sub)) {
      categorized.performance.push(sub.replace(/^perf(\(.*\))?:\s*/i, ''));
    } else if (/^refactor(\(.*\))?:/i.test(sub)) {
      categorized.refactors.push(sub.replace(/^refactor(\(.*\))?:\s*/i, ''));
    } else if (/^docs(\(.*\))?:/i.test(sub)) {
      categorized.docs.push(sub.replace(/^docs(\(.*\))?:\s*/i, ''));
    } else if (/^chore(\(.*\))?:/i.test(sub)) {
      categorized.chores.push(sub.replace(/^chore(\(.*\))?:\s*/i, ''));
    } else {
      categorized.others.push(sub);
    }
  }

  return categorized;
}

// Markdown Release Document generation
export function buildReleaseDocMarkdown({ platform, version, date, highlights = [], categorized = {}, customNotes = null }) {
  let md = `# Release Notes - ${platform === 'android' ? 'Android App' : 'WebApp'} v${version}\n\n`;
  md += `> **Ngày phát hành:** ${date}  \n`;
  md += `> **Phiên bản:** \`v${version}\`  \n`;
  md += `> **Nền tảng:** ${platform.toUpperCase()}\n\n`;

  if (highlights && highlights.length > 0) {
    md += `## 🌟 Điểm nổi bật (Highlights)\n\n`;
    for (const item of highlights) {
      md += `- ${item}\n`;
    }
    md += `\n`;
  }

  if (customNotes) {
    md += `## 📝 Ghi chú phát hành\n\n${customNotes}\n\n`;
  }

  let hasSections = false;
  let changesSection = `## 📋 Chi tiết thay đổi\n\n`;

  if (categorized.features && categorized.features.length > 0) {
    hasSections = true;
    changesSection += `### ✨ Tính năng mới (Features)\n`;
    for (const f of categorized.features) changesSection += `- ${f}\n`;
    changesSection += `\n`;
  }

  if (categorized.fixes && categorized.fixes.length > 0) {
    hasSections = true;
    changesSection += `### 🐛 Sửa lỗi (Bug Fixes)\n`;
    for (const f of categorized.fixes) changesSection += `- ${f}\n`;
    changesSection += `\n`;
  }

  if (categorized.performance && categorized.performance.length > 0) {
    hasSections = true;
    changesSection += `### ⚡ Hiệu năng & Tối ưu (Performance)\n`;
    for (const p of categorized.performance) changesSection += `- ${p}\n`;
    changesSection += `\n`;
  }

  if (categorized.refactors && categorized.refactors.length > 0) {
    hasSections = true;
    changesSection += `### ♻️ Tái cấu trúc mã nguồn (Refactoring)\n`;
    for (const r of categorized.refactors) changesSection += `- ${r}\n`;
    changesSection += `\n`;
  }

  if (categorized.docs && categorized.docs.length > 0) {
    hasSections = true;
    changesSection += `### 📚 Tài liệu (Documentation)\n`;
    for (const d of categorized.docs) changesSection += `- ${d}\n`;
    changesSection += `\n`;
  }

  if (categorized.chores && categorized.chores.length > 0) {
    hasSections = true;
    changesSection += `### 🔧 Bảo trì & Cập nhật phụ thuộc (Chores)\n`;
    for (const c of categorized.chores) changesSection += `- ${c}\n`;
    changesSection += `\n`;
  }

  if (hasSections) {
    md += changesSection;
  } else if (!customNotes && (!highlights || highlights.length === 0)) {
    md += `## 📋 Chi tiết thay đổi\n\n- Cập nhật và cải tiến hệ thống định kỳ.\n\n`;
  }

  return md;
}

// Update root CHANGELOG.md (Keep a Changelog format)
export function updateRootChangelog({ platform, version, date, highlights = [], categorized = {}, customNotes = null }, dryRun = false) {
  const header = `# Changelog

All notable changes to the MAPSUPERVISION-Firebase project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

`;

  let existing = '';
  if (fs.existsSync(PATHS.rootChangelog)) {
    existing = fs.readFileSync(PATHS.rootChangelog, 'utf-8');
  }

  const platformTitle = platform === 'android' ? 'Android App' : platform === 'webapp' ? 'WebApp' : 'System';
  let entry = `## [${version}] - ${date} (${platformTitle})\n\n`;

  if (highlights.length > 0) {
    entry += `### Highlights\n`;
    for (const h of highlights) entry += `- ${h}\n`;
    entry += `\n`;
  }

  if (categorized.features && categorized.features.length > 0) {
    entry += `### Added\n`;
    for (const item of categorized.features) entry += `- ${item}\n`;
    entry += `\n`;
  }

  if (categorized.fixes && categorized.fixes.length > 0) {
    entry += `### Fixed\n`;
    for (const item of categorized.fixes) entry += `- ${item}\n`;
    entry += `\n`;
  }

  if (categorized.performance || categorized.refactors) {
    const changed = [...(categorized.performance || []), ...(categorized.refactors || [])];
    if (changed.length > 0) {
      entry += `### Changed\n`;
      for (const item of changed) entry += `- ${item}\n`;
      entry += `\n`;
    }
  }

  if (customNotes && !categorized.features?.length && !categorized.fixes?.length) {
    entry += `### Notes\n- ${customNotes.replace(/\n+/g, '\n- ')}\n\n`;
  }

  let finalChangelog;
  if (!existing || !existing.includes('# Changelog')) {
    finalChangelog = header + entry;
  } else {
    // Insert new entry right after header
    const insertIndex = existing.indexOf('## [');
    if (insertIndex !== -1) {
      finalChangelog = existing.slice(0, insertIndex) + entry + existing.slice(insertIndex);
    } else {
      finalChangelog = existing.trimEnd() + '\n\n' + entry;
    }
  }

  if (!dryRun) {
    fs.writeFileSync(PATHS.rootChangelog, finalChangelog, 'utf-8');
  }
  return entry;
}

// Update webapp runtime metadata
export function updateWebappMetadata({ version, date, highlights = [] }, dryRun = false) {
  const commitHash = getLatestGitCommitHash();
  const metadata = {
    version,
    platform: 'webapp',
    releaseDate: date,
    commitHash,
    highlights: highlights.length > 0 ? highlights : ['Cập nhật hệ thống định kỳ'],
  };

  const dir = path.dirname(PATHS.webappPublicVersion);
  if (!dryRun) {
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(PATHS.webappPublicVersion, JSON.stringify(metadata, null, 2) + '\n', 'utf-8');
  }
  return metadata;
}

// Interactive helper
async function promptInput(questionText) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });
  return new Promise(resolve => {
    rl.question(questionText, answer => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

// Main release runner
export async function runRelease(args = process.argv.slice(2)) {
  const options = parseArgs(args);
  const date = new Date().toISOString().slice(0, 10);

  console.log(`\n🚀 [Release Manager] MAPSUPERVISION-Firebase (Date: ${date})\n`);

  let target = options.target;
  if (!target) {
    if (options.interactive || process.stdin.isTTY) {
      console.log('Chọn nền tảng cần phát hành:');
      console.log(' 1. WebApp (webapp/package.json)');
      console.log(' 2. Android App (app/build.gradle.kts)');
      console.log(' 3. Cả hai (All)');
      const ans = await promptInput('Nhập lựa chọn (1/2/3) [1]: ');
      if (ans === '2') target = 'android';
      else if (ans === '3') target = 'all';
      else target = 'webapp';
    } else {
      target = 'webapp';
    }
  }

  let bump = options.bump;
  if (!bump) {
    if (options.interactive || process.stdin.isTTY) {
      console.log('\nChọn loại tăng version:');
      console.log(' 1. patch (sửa lỗi nhỏ - v0.1.0 -> v0.1.1)');
      console.log(' 2. minor (tính năng mới - v0.1.0 -> v0.2.0)');
      console.log(' 3. major (thay đổi lớn/breaking - v0.1.0 -> v1.0.0)');
      console.log(' Hoặc nhập trực tiếp version (vd: 1.2.5)');
      const ans = await promptInput('Nhập lựa chọn (1/2/3/version) [1]: ');
      if (ans === '2') bump = 'minor';
      else if (ans === '3') bump = 'major';
      else if (ans && ans !== '1') bump = ans;
      else bump = 'patch';
    } else {
      bump = 'patch';
    }
  }

  let highlights = [];
  if (options.notes) {
    highlights = [options.notes];
  } else if (options.interactive || process.stdin.isTTY) {
    const hlAns = await promptInput('\nNhập tóm tắt điểm nổi bật (Highlights / What\'s new, phân tách bằng dấu chấm phẩy ; hoặc bỏ trống để tự động parse commit): ');
    if (hlAns) {
      highlights = hlAns.split(';').map(s => s.trim()).filter(Boolean);
    }
  }

  // Parse git commits
  const rawCommits = getGitCommits();
  const categorized = parseConventionalCommits(rawCommits);

  if (highlights.length === 0) {
    if (categorized.features.length > 0) highlights.push(...categorized.features.slice(0, 3));
    else if (categorized.fixes.length > 0) highlights.push(...categorized.fixes.slice(0, 3));
  }

  const targets = target === 'all' ? ['webapp', 'android'] : [target];

  for (const plat of targets) {
    console.log(`\n========================================`);
    console.log(`📦 Đang xử lý phát hành cho: ${plat.toUpperCase()}`);
    console.log(`========================================`);

    let currentVersion = '';
    let newVersion = '';
    let newAndroidCode = 1;

    if (plat === 'android') {
      const curr = getAndroidVersion();
      currentVersion = curr.versionName;
      newVersion = bumpSemVer(currentVersion, bump);
      newAndroidCode = curr.versionCode + 1;
      console.log(`Android Version: ${currentVersion} (code: ${curr.versionCode}) -> ${newVersion} (code: ${newAndroidCode})`);
    } else {
      currentVersion = getWebappVersion();
      newVersion = bumpSemVer(currentVersion, bump);
      console.log(`WebApp Version: ${currentVersion} -> ${newVersion}`);
    }

    if (options.dryRun) {
      console.log(`\n[DRY RUN] Sẽ cập nhật:`);
      console.log(`- Version: ${newVersion}`);
      if (plat === 'android') console.log(`- Android versionCode: ${newAndroidCode}`);
    }

    // Build release markdown
    const releaseDoc = buildReleaseDocMarkdown({
      platform: plat,
      version: newVersion,
      date,
      highlights,
      categorized,
      customNotes: options.notes,
    });

    const releaseDocDir = plat === 'android' ? PATHS.docsReleasesAndroid : PATHS.docsReleasesWebapp;
    const releaseDocPath = path.join(releaseDocDir, `v${newVersion}.md`);

    if (options.dryRun) {
      console.log(`\n[DRY RUN] Nội dung file ${releaseDocPath} dự kiến:\n`);
      console.log(releaseDoc);
    } else {
      if (!fs.existsSync(releaseDocDir)) {
        fs.mkdirSync(releaseDocDir, { recursive: true });
      }
      fs.writeFileSync(releaseDocPath, releaseDoc, 'utf-8');
      console.log(`✅ Đã tạo file release notes: ${path.relative(ROOT_DIR, releaseDocPath)}`);

      // Update version files
      if (plat === 'android') {
        updateAndroidVersion(newAndroidCode, newVersion, false);
        console.log(`✅ Đã cập nhật app/build.gradle.kts: versionCode=${newAndroidCode}, versionName="${newVersion}"`);
      } else {
        updateWebappVersion(newVersion, false);
        console.log(`✅ Đã cập nhật webapp/package.json: version="${newVersion}"`);
        updateWebappMetadata({ version: newVersion, date, highlights }, false);
        console.log(`✅ Đã tạo webapp/public/version.json`);
      }

      // Update root CHANGELOG.md
      updateRootChangelog({
        platform: plat,
        version: newVersion,
        date,
        highlights,
        categorized,
        customNotes: options.notes,
      }, false);
      console.log(`✅ Đã cập nhật CHANGELOG.md`);
    }
  }

  console.log(`\n✨ Hoàn thành quá trình phát hành${options.dryRun ? ' (DRY RUN - không thay đổi file)' : ''}!\n`);
}

// Execute if run directly from CLI
if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  runRelease().catch(err => {
    console.error('❌ Lỗi khi thực hiện release:', err);
    process.exit(1);
  });
}
