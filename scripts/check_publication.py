"""Check the approved publication boundary; no server or client is started."""
from pathlib import Path
import argparse
import hashlib
import ipaddress
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MODULES = {
    'plugins': {'EltenaCore', 'EltenaEffectCore', 'EltenaSoundCore'},
    'mods': {'EltenaAddon', 'EltenaEffect', 'EltenaSound'},
}
IGNORED = {'.git', '.gradle', 'build', 'bin', 'run', '__pycache__'}
FORBIDDEN_EXT = {'.jar', '.class', '.ogg', '.png', '.zip', '.log', '.db', '.sqlite', '.jks', '.p12', '.pem'}
PATTERNS = {
    'home_path': re.compile(r'(?:[A-Za-z]:[\\/]Users[\\/]|/(?:Users|home)/)[^\s"<>]+'),
    'email': re.compile(r'\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b'),
    'private_key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'credential_token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|AKIA[A-Z0-9]{16})\b'),
    'uuid': re.compile(r'\b[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\b'),
}

def files():
    return sorted(p for p in ROOT.rglob('*') if p.is_file() and not any(x in IGNORED for x in p.relative_to(ROOT).parts))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--deny-token', action='append', default=[], help='Additional private strings to reject; matched values are not printed.')
    args = parser.parse_args()
    issues = []
    for folder, expected in MODULES.items():
        actual = {p.name for p in (ROOT / folder).iterdir() if p.is_dir()}
        if actual != expected:
            issues.append({'path': folder, 'kind': 'module_scope'})
    selected = files()
    assets = {x["path"]: x for x in json.loads((ROOT / "docs/included-assets.json").read_text(encoding="utf8"))}
    actual_assets = {p.relative_to(ROOT).as_posix() for p in selected if p.suffix.lower() in {".png", ".ogg"}}
    if actual_assets != set(assets):
        issues.append({"kind": "asset_set_mismatch"})
    for p in selected:
        rel = p.relative_to(ROOT).as_posix()
        if rel in assets:
            data = p.read_bytes()
            if hashlib.sha256(data).hexdigest() != assets[rel]['sha256']:
                issues.append({'path': rel, 'kind': 'asset_hash_mismatch'})
            for token in args.deny_token:
                if token.lower().encode() in data.lower():
                    issues.append({'path': rel, 'kind': 'private_string_in_asset'})
            continue
        if p.suffix.lower() in FORBIDDEN_EXT:
            issues.append({'path': rel, 'kind': 'binary_or_runtime_file'})
            continue
        try:
            text = p.read_text(encoding='utf-8-sig')
        except UnicodeDecodeError:
            issues.append({'path': rel, 'kind': 'not_utf8_text'})
            continue
        for token in args.deny_token:
            if token.lower() in (rel + '\n' + text).lower():
                issues.append({'path': rel, 'kind': 'private_string'})
        for name, pattern in PATTERNS.items():
            if pattern.search(text):
                issues.append({'path': rel, 'kind': name})
        for match in re.finditer(r'\b(?:\d{1,3}\.){3}\d{1,3}\b', text):
            try:
                ipaddress.ip_address(match.group())
            except ValueError:
                continue
            issues.append({'path': rel, 'kind': 'ipv4_review'})
        if p.name == 'build.gradle.kts' and re.search(r'deployJar|finalizedBy|server/plugins|client/mods', text):
            issues.append({'path': rel, 'kind': 'deployment_coupling'})
        if p.suffix == '.java':
            match = re.search(r'^package ([\w.]+);', text, re.MULTILINE)
            if match and '/src/main/java/' in rel:
                actual = rel.split('/src/main/java/', 1)[1].rsplit('/', 1)[0].replace('/', '.')
                if actual != match.group(1):
                    issues.append({'path': rel, 'kind': 'java_package_path'})
        if p.suffix == '.json':
            try:
                json.loads(text)
            except ValueError:
                issues.append({'path': rel, 'kind': 'invalid_json'})
    print(json.dumps({'files_checked': len(selected), 'issues': issues}, ensure_ascii=True, indent=2))
    return 1 if issues else 0

if __name__ == '__main__':
    sys.exit(main())
