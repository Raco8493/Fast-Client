#!/usr/bin/env python3
"""PhoneCraft: local-only Minecraft Java / Bedrock host dashboard for Termux.

No third-party Python modules. Minecraft is run by OpenJDK inside Termux on
this Android phone. GitHub is for source distribution, never the game host.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import threading
import time
from collections import deque
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse
from urllib.request import Request, urlopen
import zipfile

ROOT = Path(__file__).resolve().parent
HOME = Path(os.environ.get('PHONECRAFT_HOME', str(Path.home() / '.phonecraft'))).expanduser()
HOME.mkdir(parents=True, exist_ok=True)
CONFIG_FILE = HOME / 'config.json'
MC_VERSION = '26.3'  # compatible with Geyser at the time this project was created
API_USER_AGENT = 'PhoneCraft-Android-Host/1.0 (https://github.com/Raco8493)'
DEFAULTS = {
    'mode': 'paper', 'ram_percent': 75, 'cpu_percent': 90,
    'max_players': 10, 'view_distance': 6, 'simulation_distance': 4,
    'java_port': 25565, 'bedrock_port': 19132, 'version': MC_VERSION,
}


def read_config():
    try:
        loaded = json.loads(CONFIG_FILE.read_text('utf-8'))
        return {**DEFAULTS, **{k: v for k, v in loaded.items() if k in DEFAULTS}}
    except (OSError, ValueError):
        return DEFAULTS.copy()


def save_config(c):
    temp = CONFIG_FILE.with_suffix('.tmp')
    temp.write_text(json.dumps(c, indent=2), 'utf-8')
    os.replace(temp, CONFIG_FILE)


def profile_dir(mode):
    return HOME / 'servers' / mode


def mem_kib():
    data = {}
    try:
        with open('/proc/meminfo', encoding='ascii') as f:
            for line in f:
                key, sep, rest = line.partition(':')
                if sep:
                    data[key] = int(rest.strip().split()[0])
    except (OSError, ValueError, IndexError):
        pass
    return data


def java_heap_mb(percent, mem=None):
    """Xmx is a max Java heap, NOT total process RAM or guaranteed free RAM.

    Leave reserve for Android, Java native allocations and other phone apps.
    """
    m = mem if mem is not None else mem_kib()
    total = m.get('MemTotal', 4 * 1024 * 1024) // 1024
    available = m.get('MemAvailable', total * 1024 // 2) // 1024
    if available < 1300:
        raise RuntimeError('الرام المتاحة قليلة جدًا. اقفل التطبيقات أو قلل الضغط على الجهاز.')
    return int(min(total * int(percent) / 100, total - 1200, available - 750))


def set_property(path, name, value):
    existing = path.read_text('utf-8') if path.exists() else ''
    lines = [s for s in existing.splitlines() if not s.strip().startswith(name + '=')]
    lines.append(name + '=' + str(value))
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def is_safe_filename(filename):
    return (bool(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.+()\-]{0,150}\.jar', filename))
            and '..' not in filename)


def get_json(url):
    with urlopen(Request(url, headers={'User-Agent': API_USER_AGENT,
                                       'Accept': 'application/json'}), timeout=35) as r:
        return json.load(r)


def download(url, dest, sha256=None):
    """Atomic download. Only call using URLs from trusted vendor metadata."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_name(dest.name + '.download')
    digest = hashlib.sha256()
    try:
        with urlopen(Request(url, headers={'User-Agent': API_USER_AGENT}), timeout=60) as response:
            if response.geturl().split(':', 1)[0].lower() != 'https':
                raise RuntimeError('غير مسموح بالتحميل عبر اتصال غير مشفر')
            with tmp.open('wb') as out:
                total = 0
                while True:
                    chunk = response.read(128 * 1024)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > 300 * 1024 * 1024:
                        raise RuntimeError('الملف أكبر من الحد المسموح 300MB')
                    out.write(chunk)
                    digest.update(chunk)
        if tmp.stat().st_size < 1024:
            raise RuntimeError('حجم الملف الذي نزل غير منطقي')
        if sha256 and digest.hexdigest().lower() != sha256.lower():
            raise RuntimeError('فشل التحقق من سلامة الملف SHA256')
        tmp.replace(dest)
    finally:
        tmp.unlink(missing_ok=True)


def install_modrinth(slug, game_version, target):
    from urllib.parse import quote
    url = ('https://api.modrinth.com/v2/project/' + quote(slug, safe='')
           + '/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22'
           + quote(game_version, safe='') + '%22%5D')
    results = get_json(url)
    versions = [v for v in results if v.get('version_type') == 'release'] or results
    if not versions:
        raise RuntimeError(f'لا يوجد إصدار متوافق لمود {slug} مع {game_version}')
    files = versions[0].get('files', [])
    files = sorted(files, key=lambda f: not f.get('primary', False))
    if not files:
        raise RuntimeError(f'لا يوجد ملف تحميل لمود {slug}')
    sha = files[0].get('hashes', {}).get('sha512')  # sha512 not in download() validator
    _ = sha
    download(files[0]['url'], target, files[0].get('hashes', {}).get('sha256'))


class Host:
    def __init__(self):
        self.lock = threading.RLock()
        self.config = read_config()
        self.process = None
        self.installing = False
        self.last_error = ''
        self.log = deque(maxlen=500)
        self.log_line('PhoneCraft جاهز. لازم تثبت السيرفر أول مرة.')

    def log_line(self, line):
        with self.lock:
            self.log.append(f'[{datetime.now().strftime("%H:%M:%S")}] {str(line).rstrip()[:1800]}')

    def running(self):
        return self.process is not None and self.process.poll() is None

    def directory(self):
        return profile_dir(self.config['mode'])

    def set_config(self, changes):
        valid = {'mode': ('paper', 'fabric')}
        nums = {'ram_percent': (30, 85), 'cpu_percent': (25, 100),
                'max_players': (1, 100), 'view_distance': (2, 16),
                'simulation_distance': (2, 12)}
        with self.lock:
            if self.running() or self.installing:
                raise RuntimeError('وقف السيرفر/التثبيت الأول عشان تغيّر الإعدادات')
            for k, value in changes.items():
                if k in valid:
                    if value not in valid[k]:
                        raise ValueError('نوع السيرفر غير مدعوم')
                elif k in nums:
                    if not isinstance(value, int) or isinstance(value, bool) or not (nums[k][0] <= value <= nums[k][1]):
                        raise ValueError('قيمة غير مسموح بيها: ' + k)
                else:
                    raise ValueError('إعداد غير معروف: ' + k)
            self.config.update(changes)
            save_config(self.config)
        self.log_line('الإعدادات اتحفظت. شغّل السيرفر عشان تطبّقها.')

    def install_async(self, accepted):
        if accepted is not True:
            raise ValueError('لازم توافق على Minecraft EULA بنفسك عشان التثبيت يكمل')
        with self.lock:
            if self.installing or self.running():
                raise RuntimeError('فيه سيرفر شغال أو تثبيت جاري')
            self.installing = True
            mode = self.config['mode']
            self.last_error = ''
        threading.Thread(target=self._install, args=(mode,), daemon=True).start()

    def _install(self, mode):
        dest = profile_dir(mode)
        dest.mkdir(parents=True, exist_ok=True)
        try:
            if shutil.which('java') is None:
                raise RuntimeError('لازم تثبت Java 25 من Termux: pkg install openjdk-25')
            if mode == 'paper':
                self.log_line('بنجيب آخر إصدار مستقر لـPaper 26.3...')
                builds = get_json('https://fill.papermc.io/v3/projects/paper/versions/26.3/builds')
                if not isinstance(builds, list):
                    raise RuntimeError('رد خدمة Paper غير متوقع')
                stable = next((b for b in builds if b.get('channel') == 'STABLE'), None)
                if stable is None:
                    raise RuntimeError('مفيش نسخة Paper STABLE متاحة لـ26.3')
                jar = stable['downloads']['server:default']
                download(jar['url'], dest / 'server.jar', jar.get('checksums', {}).get('sha256'))
                plug = dest / 'plugins'
                plug.mkdir(exist_ok=True)
                for project, name in [('geyser', 'Geyser-Spigot.jar'), ('floodgate', 'floodgate-spigot.jar')]:
                    self.log_line('تحميل ' + name)
                    url = f'https://download.geysermc.org/v2/projects/{project}/versions/latest/builds/latest/downloads/spigot'
                    download(url, plug / name)
            else:
                self.log_line('بنثبّت Fabric 26.3...')
                loader_list = get_json('https://meta.fabricmc.net/v2/versions/loader')
                installer_list = get_json('https://meta.fabricmc.net/v2/versions/installer')
                loader = next(x['version'] for x in loader_list if x.get('stable'))
                installer = next(x['version'] for x in installer_list if x.get('stable'))
                url = f'https://meta.fabricmc.net/v2/versions/loader/26.3/{loader}/{installer}/server/jar'
                download(url, dest / 'server.jar')
                moddir = dest / 'mods'
                moddir.mkdir(exist_ok=True)
                for slug, name in [('fabric-api', 'fabric-api.jar'), ('floodgate', 'floodgate-fabric.jar')]:
                    self.log_line('تحميل ' + name)
                    install_modrinth(slug, MC_VERSION, moddir / name)
                self.log_line('تحميل Geyser-Fabric.jar')
                download('https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/fabric',
                         moddir / 'Geyser-Fabric.jar')
            # EULA only written after explicit user approval in the UI.
            (dest / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
            self._apply_properties(dest)
            self.log_line('تم التثبيت! اضغط تشغيل. بعد أول تشغيل اعمل Restart مرة لضبط Geyser.')
        except Exception as ex:
            with self.lock:
                self.last_error = str(ex)
            self.log_line('خطأ في التثبيت: ' + str(ex))
        finally:
            with self.lock:
                self.installing = False

    def _apply_properties(self, dest):
        c = self.config
        props = dest / 'server.properties'
        values = {
            'server-port': c['java_port'],
            'online-mode': 'true',
            'max-players': c['max_players'],
            'view-distance': c['view_distance'],
            'simulation-distance': c['simulation_distance'],
            'enable-command-block': 'true',
            'server-ip': '',
            'motd': 'PhoneCraft Android Host - Java + Bedrock',
        }
        for k, v in values.items():
            set_property(props, k, v)

    def _tune_geyser(self, dest):
        configfile = (dest / 'plugins' / 'Geyser-Spigot' / 'config.yml') if self.config['mode'] == 'paper' else (dest / 'config' / 'Geyser-Fabric' / 'config.yml')
        if not configfile.exists():
            return False
        original = configfile.read_text('utf-8')
        updated = re.sub(r'(?m)^(\s*auth-type:\s*).+$', r'\g<1>floodgate', original)
        updated = re.sub(r'(?m)^(\s*transport:\s*).+$', r'\g<1>nethernet', updated)
        updated = re.sub(r'(?m)^(\s*port:\s*)19132\s*$', lambda m: m.group(1) + str(self.config['bedrock_port']), updated)
        if updated != original:
            configfile.write_text(updated, 'utf-8')
            self.log_line('Geyser اتضبط على Floodgate وNetherNet. هيتفعّل مع تشغيل جديد.')
        return True

    def start(self):
        with self.lock:
            if self.installing or self.running():
                raise RuntimeError('السيرفر شغال أو لسه بيتم تثبيته')
            dest = self.directory()
            if not (dest / 'server.jar').exists() or not (dest / 'eula.txt').exists():
                raise RuntimeError('اضغط تثبيت الأول قبل تشغيل السيرفر')
            heap = java_heap_mb(self.config['ram_percent'])
            cpu_total = max(1, os.cpu_count() or 4)
            cpu_visible = max(1, min(cpu_total, math.ceil(cpu_total * self.config['cpu_percent'] / 100)))
            self._apply_properties(dest)
            self._tune_geyser(dest)
            args = ['java', '-Xms256M', f'-Xmx{heap}M', f'-XX:ActiveProcessorCount={cpu_visible}',
                    '-jar', 'server.jar', '--nogui']
            self.log_line(f'تشغيل {self.config["mode"]}: Java heap {heap}MB / {cpu_visible} threads ظاهرة')
            try:
                self.process = subprocess.Popen(args, cwd=dest, stdin=subprocess.PIPE,
                                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                                text=True, encoding='utf-8', errors='replace',
                                                bufsize=1, start_new_session=True)
            except OSError as ex:
                self.process = None
                raise RuntimeError('فشل تشغيل Java: ' + str(ex)) from ex
            threading.Thread(target=self._collect_output, args=(self.process, dest), daemon=True).start()
            return heap

    def _collect_output(self, proc, dest):
        try:
            for line in proc.stdout:
                self.log_line(line)
        except Exception as ex:
            self.log_line('مشكلة في قراءة Console: ' + str(ex))
        finally:
            code = proc.wait()
            self.log_line('سيرفر ماينكرافت وقف. Exit code: ' + str(code))
            if self._tune_geyser(dest):
                self.log_line('لو دي أول مرة، شغّل السيرفر تاني عشان تغييرات Geyser تتطبّق.')

    def command(self, command):
        if not isinstance(command, str) or not command.strip() or len(command) > 200:
            raise ValueError('الكوماند غير صالح')
        with self.lock:
            if not self.running():
                raise RuntimeError('السيرفر مش شغال')
            try:
                self.process.stdin.write(command.lstrip('/') + '\n')
                self.process.stdin.flush()
            except (OSError, BrokenPipeError) as ex:
                raise RuntimeError('الكونسول مش متاح: ' + str(ex)) from ex
        self.log_line('> ' + command)

    def stop(self):
        self.command('stop')

    def backup(self):
        with self.lock:
            if self.running() or self.installing:
                raise RuntimeError('اعمل Stop الأول عشان النسخة الاحتياطية متطلعش تالفة')
            dest = self.directory()
            if not (dest / 'server.jar').exists():
                raise RuntimeError('مفيش سيرفر متثبت')
            backupdir = HOME / 'backups'
            backupdir.mkdir(parents=True, exist_ok=True)
            target = backupdir / f'{self.config["mode"]}-{datetime.now().strftime("%Y%m%d-%H%M%S")}.zip'
            include_dirs = ('world', 'world_nether', 'world_the_end')
            include_files = ('server.properties', 'ops.json', 'whitelist.json', 'banned-players.json')
            with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=4) as z:
                for folder in include_dirs:
                    base = dest / folder
                    if base.exists():
                        for p in base.rglob('*'):
                            if p.is_file() and not p.is_symlink():
                                z.write(p, p.relative_to(dest))
                for name in include_files:
                    p = dest / name
                    if p.exists() and p.is_file():
                        z.write(p, p.relative_to(dest))
        self.log_line('Backup اتعمل: ' + target.name)
        return str(target)

    def upload(self, filename, data):
        if not is_safe_filename(filename):
            raise ValueError('ارفع ملف JAR باسم صحيح فقط')
        with self.lock:
            if self.running() or self.installing:
                raise RuntimeError('اعمل Stop الأول عشان تضيف مود/بلجن')
            mode = self.config['mode']
            dest = self.directory() / ('plugins' if mode == 'paper' else 'mods')
            if not (self.directory() / 'server.jar').exists():
                raise RuntimeError('ثبت السيرفر الأول')
            dest.mkdir(exist_ok=True, parents=True)
            target = dest / filename
            with target.open('wb') as f:
                f.write(data)
        self.log_line('تمت إضافة ' + filename + ' — من مصدر تثق فيه فقط')
        return filename

    def status(self):
        m = mem_kib()
        with self.lock:
            c = self.config.copy()
            dest = self.directory()
            r = self.running()
            return {
                'running': r, 'installing': self.installing,
                'installed': (dest / 'server.jar').exists(),
                'error': self.last_error,
                'config': c,
                'ram_total_mb': round(m.get('MemTotal', 0) / 1024),
                'ram_free_mb': round(m.get('MemAvailable', 0) / 1024),
                'cores': os.cpu_count() or 1,
                'files': sorted(p.name for p in (dest / ('plugins' if c['mode'] == 'paper' else 'mods')).glob('*.jar')),
                'logs': list(self.log)[-160:],
                'backups': sorted((p.name for p in (HOME / 'backups').glob('*.zip')), reverse=True)[:10],
            }


HOST = Host()
TOKEN = secrets.token_urlsafe(32)


class Handler(BaseHTTPRequestHandler):
    server_version = 'PhoneCraft/1.0'

    def log_message(self, fmt, *args):
        return  # no extra web-server noise in console

    def send_data(self, code, data):
        raw = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(raw)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == '/':
            page = (ROOT / 'dashboard.html').read_text('utf-8').replace('/*APP_TOKEN*/null', json.dumps(TOKEN))
            raw = page.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'unsafe-inline' 'self'; style-src 'unsafe-inline' 'self'; img-src 'none'; connect-src 'self'")
            self.send_header('X-Frame-Options', 'DENY')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)
        elif path == '/api/status':
            self.send_data(200, HOST.status())
        else:
            self.send_data(404, {'error': 'مش موجود'})

    def do_POST(self):
        if self.headers.get('X-PhoneCraft-Token') != TOKEN:
            return self.send_data(403, {'error': 'طلب غير مصرح به'})
        path = urlparse(self.path).path
        try:
            length = int(self.headers.get('Content-Length', '-1'))
            if length < 0 or length > 150 * 1024 * 1024:
                raise ValueError('حجم الطلب غير مسموح')
            body = self.rfile.read(length)
            if path == '/api/upload':
                filename = self.headers.get('X-Filename', '')
                if len(body) < 128:
                    raise ValueError('ملف JAR صغير بصورة غير صحيحة')
                name = HOST.upload(filename, body)
                return self.send_data(200, {'ok': True, 'name': name})
            data = json.loads(body.decode('utf-8') or '{}')
            if not isinstance(data, dict):
                raise ValueError('صيغة الطلب غير صحيحة')
            if path == '/api/config':
                HOST.set_config(data)
            elif path == '/api/install':
                HOST.install_async(data.get('eula'))
            elif path == '/api/start':
                HOST.start()
            elif path == '/api/stop':
                HOST.stop()
            elif path == '/api/command':
                HOST.command(data.get('command', ''))
            elif path == '/api/op':
                name = data.get('name', '')
                if not isinstance(name, str) or not re.fullmatch(r'[.A-Za-z0-9_\-]{1,32}', name):
                    raise ValueError('اسم اللاعب غير صحيح. Bedrock: جرّب .PlayerName')
                HOST.command('op ' + name)
            elif path == '/api/deop':
                name = data.get('name', '')
                if not isinstance(name, str) or not re.fullmatch(r'[.A-Za-z0-9_\-]{1,32}', name):
                    raise ValueError('اسم اللاعب غير صحيح')
                HOST.command('deop ' + name)
            elif path == '/api/backup':
                return self.send_data(200, {'ok': True, 'path': HOST.backup()})
            else:
                return self.send_data(404, {'error': 'مش موجود'})
            self.send_data(200, {'ok': True})
        except (ValueError, RuntimeError, OSError, KeyError, TypeError) as ex:
            self.send_data(400, {'error': str(ex)})
        except Exception as ex:
            HOST.log_line('خطأ داخلي: ' + str(ex))
            self.send_data(500, {'error': 'حدث خطأ. راجع الـConsole.'})


def main():
    print('=== PhoneCraft Android Host ===', flush=True)
    print('Host: Android phone using Termux. Dashboard: http://127.0.0.1:8765', flush=True)
    print('Keep Termux running and disable Android battery optimization.', flush=True)
    server = ThreadingHTTPServer(('127.0.0.1', 8765), Handler)
    try:
        server.serve_forever(poll_interval=.3)
    except KeyboardInterrupt:
        if HOST.running():
            HOST.stop()
            try:
                HOST.process.wait(timeout=35)
            except subprocess.TimeoutExpired:
                HOST.process.terminate()
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
