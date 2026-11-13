"""Isolated Glasmio guide checks; never submits credentials or changes production data."""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--tag', default='glasmio-guide')
args = parser.parse_args()
assert re.fullmatch(r'[\w-]+', args.tag)
adb_path = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
command = [str(adb_path), '-s', args.serial]
package = 'com.pluvist.xachet.benchmark'
out = Path(__file__).resolve().parents[1] / 'artifacts'
out.mkdir(exist_ok=True)
log_path = out / f'{args.tag}-actions.jsonl'


def log_event(event, **details):
    record = {'time': datetime.now().astimezone().isoformat(timespec='milliseconds'),
              'event': event, **details}
    line = json.dumps(record, ensure_ascii=False)
    print(line, flush=True)
    with log_path.open('a', encoding='utf-8') as log:
        log.write(line + '\n')


def visible_labels(root):
    labels = []
    for node in root.iter('node'):
        if node.get('visible-to-user') == 'false' or node.get('password') == 'true':
            continue
        for label in (node.get('text'), node.get('content-desc')):
            if label and label not in labels:
                labels.append(label)
    return labels


def adb(*args):
    return subprocess.check_output(command + list(map(str, args)), text=True, encoding='utf-8', errors='replace', timeout=30)


def tree():
    windows = adb('shell', 'dumpsys', 'window')
    assert any(package in line and 'mCurrentFocus=' in line for line in windows.splitlines()), 'Guide lost focus; stopping.'
    adb('shell', 'uiautomator', 'dump', '/sdcard/xachet-guide-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/xachet-guide-ui.xml'))


def find(label, root=None):
    root = tree() if root is None else root
    nodes = [node for node in root.iter('node') if label in (node.get('text'), node.get('content-desc'))]
    if not nodes:
        log_event('missing_control', label=label, visible_labels=visible_labels(root))
    assert nodes, f'Missing {label}'
    return nodes[0]


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.attrib['bounds'])))


def tap_node(label, node):
    x1, y1, x2, y2 = bounds(node)
    assert x2 > x1 and y2 > y1
    x, y = (x1+x2)//2, (y1+y2)//2
    log_event('tap', label=label, bounds=[x1, y1, x2, y2],
              position=[x, y], enabled=node.get('enabled'), clickable=node.get('clickable'))
    adb('shell', 'input', 'tap', x, y)


def tap(label):
    root = tree()
    for attempt in range(4):
        try:
            node = find(label, root)
            break
        except AssertionError:
            scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true']
            assert scrolls and attempt < 3, 'No visible control: ' + label
            x1,y1,x2,y2 = bounds(max(scrolls, key=lambda n: bounds(n)[3]-bounds(n)[1]))
            adb('shell','input','swipe',(x1+x2)//2,y1+(y2-y1)*3//4,(x1+x2)//2,y1+(y2-y1)//4,280)
            time.sleep(.4)
            root = tree()
    tap_node(label, node)
    time.sleep(.65)


def hide_keyboard():
    shown = 'mInputShown=true' in adb('shell','dumpsys','input_method')
    if shown:
        adb('shell','input','keyevent',4)
        time.sleep(.6)
    find('登录账号')


def snap(name):
    tree()
    adb('shell', 'screencap', '-p', '/sdcard/xachet-guide-check.png')
    adb('pull', '/sdcard/xachet-guide-check.png', out / f'{args.tag}-{name}.png')


def begin():
    log_event('begin', serial=args.serial, package=package)
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', package+'/com.pluvist.sachet.next.MainActivity', '--ez', 'load_fixture', 'true', '--ez', 'show_onboarding', 'true')
    time.sleep(2)


begin()
snap('welcome')
# Reverse the circular reveal before it reaches the edges, then open again.
node = find('开始设置')
tap_node('开始设置', node)
time.sleep(.04)
adb('shell', 'input', 'keyevent', 4)
time.sleep(.6)
find('开始设置')
tap('开始设置')
root = tree()
find('协议与声明', root)
checkbox = next(n for n in root.iter('node') if n.get('checkable') == 'true')
assert checkbox.get('checked') == 'false'
tap('同意并继续')  # Disabled: must stay on the agreement step.
find('协议与声明')
snap('agreement')
tap('我已阅读并同意以上声明')
tap('同意并继续')
find('登录账号')
snap('login')
tap('请输入学号')
adb('shell', 'input', 'text', '2026000000')  # Synthetic only; never submitted.
hide_keyboard()
tap('默认密码为 Xtu@身份证后六位')
adb('shell', 'input', 'text', 'guide-test-only')
snap('keyboard')
hide_keyboard()
tap('Cookie 登录')
find('请输入教务 Cookie')
tap('学号密码登录')
find('2026000000')
tap('上一步')
tap('同意并继续')
find('2026000000')
tap('稍后登录')
find('权限与服务')
snap('permissions')
tap('通知权限')
time.sleep(.5)
adb('shell', 'input', 'keyevent', 4)  # Dismiss system dialog; never grant automatically.
time.sleep(.5)
find('权限与服务')
tap('继续')
time.sleep(2)
find('一切准备就绪')
snap('complete')
tap('开始使用')
time.sleep(1)
find('今日课程')
snap('home')
(out / f'{args.tag}-result.json').write_text(json.dumps({
    'serial': args.serial, 'welcomeReverse': True, 'consentGate': True,
    'loginModesAndDraft': True, 'passwordSubmitted': False, 'keyboard': True,
    'notificationReturn': True, 'completionToHome': True,
}, indent=2), encoding='utf-8')
print('Guide checks passed; no account login submitted.', flush=True)
