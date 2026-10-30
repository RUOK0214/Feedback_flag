"""Package only player-facing files; run after gradlew build."""
from pathlib import Path
import hashlib
import json
import zipfile

root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line)
version = properties['mod_version']
jar = root / 'build' / 'libs' / f'feedback-flag-{version}.jar'
with zipfile.ZipFile(jar) as built:
    assert json.loads(built.read('fabric.mod.json'))['version'] == version
    assert not any('gametest' in name.lower() or 'FlagBookTest' in name for name in built.namelist())
    ko = json.loads(built.read('assets/feedback_flag/lang/ko_kr.json'))
    en = json.loads(built.read('assets/feedback_flag/lang/en_us.json'))
    assert ko.keys() == en.keys()

guide = (root / 'docs' / 'INSTALL.txt').read_bytes()
assert jar.name.encode() in guide, 'Update installation guide to match mod version'
files = {jar.name: jar.read_bytes(), '설치안내.txt': guide, 'LICENSE.txt': (root / 'LICENSE').read_bytes()}
files['SHA256SUMS.txt'] = (hashlib.sha256(files[jar.name]).hexdigest() + '  ' + jar.name + '\n').encode()
out = root / 'build' / 'release'
out.mkdir(parents=True, exist_ok=True)
archive = out / f'feedback-flag-{version}-mc26.2.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
    for name, contents in files.items():
        bundle.writestr(name, contents)
with zipfile.ZipFile(archive) as bundle:
    assert bundle.testzip() is None and set(bundle.namelist()) == set(files)
print(archive)
