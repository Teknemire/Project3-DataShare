import { spawnSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const qualityDirectory = dirname(fileURLToPath(import.meta.url));

export const paths = {
  root: resolve(qualityDirectory, '..'),
  quality: qualityDirectory,
  backend: resolve(qualityDirectory, '..', 'backend'),
  frontend: resolve(qualityDirectory, '..', 'frontend'),
};

export const command = (name) =>
  process.platform === 'win32' && ['mvn', 'npm', 'npx'].includes(name) ? `${name}.cmd` : name;

export function section(number, title, explanation) {
  console.log(`\n[${number}] ${title}`);
  if (explanation) console.log(`    ${explanation}`);
}

export function run(program, args, options = {}) {
  const result = spawnSync(program, args, {
    cwd: options.cwd ?? paths.root,
    env: { ...process.env, ...options.env },
    encoding: 'utf8',
    stdio: options.capture ? 'pipe' : 'inherit',
    shell: process.platform === 'win32' && program.endsWith('.cmd'),
  });

  if (result.error) throw result.error;
  if (options.capture && result.stdout) process.stdout.write(result.stdout);
  if (options.capture && result.stderr) process.stderr.write(result.stderr);

  if (result.status !== 0 && !options.allowFailure) {
    throw new Error(`${program} s'est termine avec le code ${result.status}.`);
  }

  return result;
}

export function runAndSave(program, args, outputPath, options = {}) {
  const result = spawnSync(program, args, {
    cwd: options.cwd ?? paths.root,
    env: { ...process.env, ...options.env },
    encoding: 'utf8',
    stdio: ['inherit', 'pipe', 'inherit'],
    shell: process.platform === 'win32' && program.endsWith('.cmd'),
  });

  if (result.error) throw result.error;
  writeFileSync(outputPath, result.stdout ?? '', 'utf8');

  if (result.status !== 0 && !options.allowFailure) {
    throw new Error(`${program} s'est termine avec le code ${result.status}.`);
  }

  return result;
}

export function readOutput(program, args, options = {}) {
  const result = spawnSync(program, args, {
    cwd: options.cwd ?? paths.root,
    env: { ...process.env, ...options.env },
    encoding: 'utf8',
    stdio: ['inherit', 'pipe', 'inherit'],
    shell: process.platform === 'win32' && program.endsWith('.cmd'),
  });

  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${program} s'est termine avec le code ${result.status}.`);
  }
  return (result.stdout ?? '').trim();
}

export function finish(message) {
  console.log(`\nOK - ${message}`);
}
