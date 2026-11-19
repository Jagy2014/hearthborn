#!/usr/bin/env python3
"""Build a standalone launcher JAR and optionally a native app with bundled Java 22."""
import argparse
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def normalize(repository):
    value = repository.strip().rstrip('/')
    prefix = 'https://github.com/'
    if value.lower().startswith(prefix):
        value = value[len(prefix):]
    if value and not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9-]{0,38}/hearthborn(?:-client)?', value):
        raise ValueError('Repository must be ACCOUNT/hearthborn')
    return value


def launcher_version():
    version = (ROOT / 'launcher/VERSION').read_text(encoding='utf-8').strip()
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('launcher/VERSION must look like 1.0.0')
    return version


def build(repository, jdk, output, native=False):
    repository = normalize(repository)
    launcher = launcher_version()
    java = jdk / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
    if not any(s in version.stderr + version.stdout for s in ('version "22', 'openjdk 22')):
        raise ValueError('Build the launcher with JDK 22')
    output.mkdir(parents=True, exist_ok=True)
    exe = '.exe' if os.name == 'nt' else ''
    with tempfile.TemporaryDirectory(prefix='hearthborn-launcher-') as temporary:
        work = Path(temporary)
        classes, app_input = work / 'classes', work / 'input'
        classes.mkdir(); app_input.mkdir()
        sources = sorted((ROOT / 'launcher/src').rglob('*.java'))
        subprocess.run([str(jdk / 'bin' / ('javac' + exe)), '--release', '22', '-d', str(classes), *map(str, sources)], check=True)
        shutil.copy2(ROOT / 'assets/icons/app/hearthborn.png', classes / 'icon.png')
        (classes / 'launcher.properties').write_text(f'repository={repository}\nversion={launcher}\n', encoding='utf-8')
        jar = output / 'hearthborn-launcher.jar'
        subprocess.run([str(jdk / 'bin' / ('jar' + exe)), '--create', '--file', str(jar), '--main-class', 'hearthborn.launcher.Launcher', '-C', str(classes), '.'], check=True)
        if native:
            shutil.copy2(jar, app_input)
            name = 'Hearthborn'
            image = output / (name + '.app' if platform.system() == 'Darwin' else name)
            if image.exists():
                raise FileExistsError(f'Native application already exists: {image}; choose an empty output directory')
            icon = ROOT / 'assets' / 'icons' / 'app' / ('hearthborn.icns' if platform.system() == 'Darwin' else 'hearthborn.ico')
            if platform.system() == 'Linux':
                icon = ROOT / 'assets/icons/app/hearthborn.png'
            subprocess.run([str(jdk / 'bin' / ('jpackage' + exe)), '--type', 'app-image', '--name', name,
                            '--app-version', launcher, '--vendor', 'Hearthborn', '--icon', str(icon), '--input', str(app_input),
                            '--java-options', '--enable-native-access=ALL-UNNAMED', '--main-jar', jar.name, '--main-class', 'hearthborn.launcher.Launcher', '--dest', str(output),
                            '--add-modules', 'ALL-MODULE-PATH', '--jlink-options', '--strip-debug --no-header-files --no-man-pages'], check=True)
            # The bundled java executable is needed to start the downloaded game.
            candidates = list(image.rglob('bin/java.exe' if os.name == 'nt' else 'bin/java'))
            if not candidates:
                raise RuntimeError('Native runtime has no java executable')
            system = {'Darwin': 'macos', 'Windows': 'windows', 'Linux': 'linux'}[platform.system()]
            machine = {'arm64': 'aarch64', 'AMD64': 'x86_64'}.get(platform.machine(), platform.machine())
            archive = output / f'hearthborn-launcher-{system}-{machine}.zip'
            # ditto preserves macOS bundle permissions and links.
            if platform.system() == 'Darwin':
                subprocess.run(['ditto', '-c', '-k', '--sequesterRsrc', '--keepParent', str(image), str(archive)], check=True)
            else:
                with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
                    for entry in sorted(image.rglob('*')):
                        if entry.is_file(): bundle.write(entry, entry.relative_to(output))
            print(archive)
        print(jar)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', default='Jagy2014/hearthborn', help='GitHub repository that publishes the client')
    parser.add_argument('--jdk', type=Path, default=Path(os.environ.get('JAVA_HOME', '')))
    parser.add_argument('--output', type=Path, default=ROOT / 'output/launcher')
    parser.add_argument('--native', action='store_true', help='Bundle Java into an application for this OS/CPU')
    args = parser.parse_args()
    build(args.repository, args.jdk.resolve(), args.output.resolve(), args.native)


if __name__ == '__main__': main()
