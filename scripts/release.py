#!/usr/bin/env python3
"""Build a fresh Maven staging repository, validate it, and optionally publish it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import base64
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def validate(repo, expected):
    poms = sorted(repo.rglob('*.pom'))
    if not poms:
        raise ValueError('Build produced no Maven publications')
    found = set()
    for pom in poms:
        xml = ET.parse(pom).getroot()
        def field(name):
            return xml.findtext('{*}' + name)
        group, artifact, version = field('groupId'), field('artifactId'), field('version')
        if group not in expected or version != expected[group]:
            raise ValueError(f'Unexpected publication: {group}:{artifact}:{version}')
        rel = Path(group.replace('.', '/')) / artifact / version / f'{artifact}-{version}.pom'
        if pom.relative_to(repo) != rel:
            raise ValueError(f'Incorrect Maven path: {pom}')
        found.add(group)
        module = pom.with_suffix('.module')
        if module.exists():
            data = json.loads(module.read_text())
            if data['component']['version'] != version:
                raise ValueError(f'Metadata version mismatch: {module}')
            for variant in data.get('variants', []):
                for item in variant.get('files', []):
                    payload = (module.parent / item['url']).resolve()
                    if not payload.is_relative_to(repo.resolve()) or not payload.is_file():
                        raise ValueError(f'Missing/unsafe metadata payload: {item}')
                    for algorithm in ('sha256', 'sha512', 'sha1', 'md5'):
                        if algorithm in item and hashlib.new(algorithm, payload.read_bytes()).hexdigest() != item[algorithm]:
                            raise ValueError(f'Metadata checksum mismatch: {payload}')
    if found != set(expected):
        raise ValueError(f'Missing publication groups: {set(expected) - found}')
    for path in repo.rglob('maven-metadata-local.xml*'):
        path.unlink()
    print(f'Validated {len(poms)} publications and their metadata payloads', flush=True)


def upload_reposilite(repo):
    url = os.environ['REPOSILITE_URL'].rstrip('/')
    if not url.startswith('https://'):
        raise ValueError('REPOSILITE_URL must use HTTPS')
    token = base64.b64encode((os.environ['REPOSILITE_USER'] + ':' + os.environ['REPOSILITE_TOKEN']).encode()).decode()
    headers = {'Authorization': 'Basic ' + token}
    files = sorted(p for p in repo.rglob('*') if p.is_file())
    # Check every path before the first write; never overwrite immutable releases.
    for path in files:
        target = url + '/' + path.relative_to(repo).as_posix()
        try:
            with urllib.request.urlopen(urllib.request.Request(target, method='HEAD', headers=headers), timeout=30):
                raise ValueError(f'Artifact already exists: {path.relative_to(repo)}')
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
    for path in files:
        target = url + '/' + path.relative_to(repo).as_posix()
        request = urllib.request.Request(target, data=path.read_bytes(), method='PUT', headers=headers)
        with urllib.request.urlopen(request, timeout=120):
            pass
    for path in files:
        target = url + '/' + path.relative_to(repo).as_posix()
        with urllib.request.urlopen(urllib.request.Request(target, headers=headers), timeout=120) as response:
            if hashlib.sha256(response.read()).digest() != hashlib.sha256(path.read_bytes()).digest():
                raise ValueError(f'Remote verification failed: {path.relative_to(repo)}')
    print(f'Uploaded and verified {len(files)} files', flush=True)


def sign_bundle(repo, output):
    key = os.environ['PUBLISH_SIGNING_KEY_ID']
    password = os.environ.get('PUBLISH_SIGNING_PASSWORD', '')
    for pom in repo.rglob('*.pom'):
        # Metadata-only platform publications may legitimately have no source archive.
        xml = ET.parse(pom).getroot()
        if xml.findtext('{*}packaging') != 'pom':
            for classifier in ('sources', 'javadoc'):
                if not pom.with_name(pom.stem + '-' + classifier + '.jar').exists():
                    raise ValueError(f'Missing {classifier} archive beside {pom}')
    for path in sorted(p for p in repo.rglob('*') if p.is_file()):
        if path.suffix in ('.asc', '.md5', '.sha1', '.sha256', '.sha512'):
            continue
        subprocess.run(['gpg', '--batch', '--yes', '--pinentry-mode', 'loopback', '--passphrase-fd', '0',
                        '--local-user', key, '--armor', '--detach-sign', '--output', str(path) + '.asc', str(path)],
                       input=password + '\n', text=True, check=True)
        for algorithm in ('md5', 'sha1'):
            Path(str(path) + '.' + algorithm).write_text(hashlib.new(algorithm, path.read_bytes()).hexdigest() + '\n')
    bundle = output / 'central-bundle.zip'
    with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(repo.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(repo))
    return bundle


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', choices=['reposilite', 'central'])
    parser.add_argument('--version', required=True)
    parser.add_argument('--plugin-version')
    parser.add_argument('--dry-run', action='store_true', help='Print commands without building or network access')
    parser.add_argument('--publish', action='store_true', help='Upload after building and validating; default stages only')
    args = parser.parse_args()
    if os.environ.get('CI') or os.environ.get('GITHUB_ACTIONS'):
        parser.error('Releases are manual only; run this script locally')
    if args.dry_run and args.publish:
        parser.error('--dry-run and --publish cannot be combined')
    config = json.loads((ROOT / 'scripts/release-config.json').read_text())
    if config.get('separatePluginVersion') and not args.plugin_version:
        parser.error('--plugin-version is required')
    versions = {'version': args.version, 'plugin_version': args.plugin_version or args.version}
    for version in versions.values():
        if not re.fullmatch(r'[0-9][A-Za-z0-9._-]*', version) or '..' in version:
            parser.error(f'Invalid version: {version}')
        if args.destination == 'central' and ('SNAPSHOT' in version or '-dev.' in version):
            parser.error('Central requires explicit release versions without SNAPSHOT/dev suffixes')
    if args.destination == 'central' and not args.dry_run:
        subprocess.run(['git', 'diff', '--quiet', 'HEAD'], cwd=ROOT, check=True)
    output = ROOT / 'build/release'
    if args.dry_run:
        staging = output / 'DRY-RUN'
    else:
        output.mkdir(parents=True, exist_ok=True)
        staging = Path(tempfile.mkdtemp(prefix='stage-', dir=output))
    repo = staging / 'maven'
    env = dict(os.environ, IS_RELEASE='false')
    for build in config['builds']:
        command = [str(ROOT / build['wrapper']), '-p', str(ROOT / build['project']),
                   '--no-daemon', '--no-configuration-cache', '--stacktrace',
                   '-Dmaven.repo.local=' + str(repo), '-PtvosUnsignedStaging=true']
        command += [item.format(**versions) for item in build['arguments']]
        build_env = dict(env, **{key: value.format(**versions) for key, value in build.get('env', {}).items()})
        print(shlex.join(command), flush=True)
        if not args.dry_run:
            subprocess.run(command, cwd=ROOT, env=build_env, check=True)
    if args.dry_run:
        print('Plan only. No build, signing, network calls, or uploads.')
        return
    expected = {group: value.format(**versions) for group, value in config['groups'].items()}
    validate(repo, expected)
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    (staging / 'release.json').write_text(json.dumps({'source': source, 'groups': expected}, indent=2) + '\n')
    if args.destination == 'central':
        bundle = sign_bundle(repo, staging)
        if args.publish:
            subprocess.run([sys.executable, str(ROOT / 'scripts/publish-central.py'), '--bundle', str(bundle)], check=True)
    elif args.publish:
        upload_reposilite(repo)
    print(f'Release output: {staging}')
    if not args.publish:
        print('Staging and validation passed. Nothing uploaded.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
