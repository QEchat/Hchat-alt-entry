// Runs the production cache with real DexKit descriptor resolution, without Gradle.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const cache = process.argv[2] || path.join(os.homedir(), '.gradle/caches/modules-2/files-2.1');
const java = process.env.JAVA || 'java';
function artifact(group, name, version, ext = 'jar') {
    const directory = path.join(cache, group, name, version);
    for (const hash of fs.readdirSync(directory)) {
        const candidate = path.join(directory, hash, name + '-' + version + '.' + ext);
        if (fs.existsSync(candidate)) return candidate;
    }
    throw new Error('Missing dependency: ' + directory);
}
function run(command, args, options = {}) {
    const result = spawnSync(command, args, {cwd: root, timeout: 60_000, maxBuffer: 20 * 1024 * 1024, ...options});
    if (result.error || result.status !== 0) throw result.error || new Error(command + ' failed: ' + result.stderr);
    return result.stdout;
}
const stdlib = artifact('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.0');
const compiler = [
    artifact('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.0'), stdlib,
    artifact('org.jetbrains.kotlin', 'kotlin-reflect', '2.3.20'),
    artifact('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.4.0'),
    artifact('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
    artifact('org.jetbrains', 'annotations', '13.0')
];
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'hchat-cache-integrity-'));
const dex = path.join(output, 'dexkit.jar');
fs.writeFileSync(dex, run('unzip', ['-p', artifact('org.luckypray', 'dexkit', '2.0.1', 'aar'), 'classes.jar']));
const tests = path.join(root, 'scripts/tests/dex_cache_integrity');
const jar = path.join(output, 'tests.jar');
process.stdout.write(run(java, ['-Xmx256m', '-cp', compiler.join(path.delimiter),
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
    '-classpath', [stdlib, dex].join(path.delimiter), '-d', jar,
    path.join(root, 'app/src/main/java/h/Hchat/dexkit/DexMethodCache.kt'),
    ...fs.readdirSync(tests).filter(name => name.endsWith('.kt')).map(name => path.join(tests, name))]));
process.stdout.write(run(java, ['-cp', [jar, stdlib, dex].join(path.delimiter), 'CacheIntegrityRegressionKt']));
console.log('Test artifacts: ' + output);
