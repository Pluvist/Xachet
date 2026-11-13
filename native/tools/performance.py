"""Repeatable portrait UI checks on validated OnePlus and Xiaomi test devices.

Run from any directory with Python 3 and adb. Requires an unlocked USB device.
--suite focus uses the existing preview data; --suite controls performs settled
navigation checks using the isolated benchmark APK with synthetic query data.
No recording, screenshots or UI inspection runs inside the measured windows.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default=str(Path(os.environ.get('LOCALAPPDATA', '')) / 'Android/Sdk/platform-tools/adb.exe'))
parser.add_argument('--serial')
parser.add_argument('--package', default='com.pluvist.xachet.benchmark')
parser.add_argument('--suite', choices=['focus', 'controls', 'hotpaths', 'menus', 'profile'], default='focus')
parser.add_argument('--max-missed-percent', type=float, default=3.0)
parser.add_argument('--tag', default=datetime.datetime.now().strftime('portrait-%Y%m%d-%H%M%S'))
parser.add_argument('--trace', action='store_true', help='Collect presented-frame classifications with Perfetto.')
parser.add_argument('--only', help='Comma-separated controls keys for bounded functional rechecks.')
args = parser.parse_args()
if not re.fullmatch(r'[\w.-]+', args.tag) or '..' in args.tag:
    parser.error('Use a simple result tag without path separators.')
if args.suite == 'controls' and not args.package.endswith('.benchmark'):
    parser.error('The populated-controls suite requires the isolated benchmark package.')
devices = subprocess.check_output([args.adb, 'devices'], text=True)
available = re.findall(r'^([^\s]+)\s+device$', devices, re.M)
if args.serial is None:
    if len(available) != 1:
        parser.error('Connect one device or specify --serial.')
    args.serial = available[0]
command = [args.adb, '-s', args.serial]
output = Path(__file__).resolve().parents[1] / 'artifacts'
output.mkdir(exist_ok=True)
results = {}
if args.suite=='controls' and args.only and (output/f'{args.tag}-results.json').exists():
    results=json.loads((output/f'{args.tag}-results.json').read_text(encoding='utf-8'))


def adb(*items):
    return subprocess.check_output(command + list(map(str, items)), text=True, encoding='utf-8', errors='replace')


def guard():
    windows = adb('shell', 'dumpsys', 'window').splitlines()
    focused = next((line for line in windows if 'mCurrentFocus=' in line), '')
    own_popup = ('Popup' in focused or '\u5f39\u51fa\u5f0f\u7a97\u53e3' in focused) and any(
        args.package in line and 'mFocusedApp=' in line for line in windows)
    assert args.package in focused or own_popup, 'App lost focus; stopped.'


def inspect():
    guard()
    adb('shell', 'uiautomator', 'dump', '/sdcard/sachet-performance-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/sachet-performance-ui.xml'))


screen_size = tuple(map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1]))
sx, sy = screen_size[0]/1216, screen_size[1]/2640

def tap(x, y):
    adb('shell', 'input', 'tap', round(x*sx), round(y*sy))


up = (600, 1850, 600, 750, 350)
down = (600, 750, 600, 1850, 350)


def swipe(motion):
    adb('shell', 'input', 'swipe', round(motion[0]*sx), round(motion[1]*sy), round(motion[2]*sx), round(motion[3]*sy), motion[4])


def motions(items):
    for motion in items:
        swipe(motion)
        time.sleep(.15)


def find(title):
    for _ in range(9):
        root = inspect()
        parents = {child: parent for parent in root.iter() for child in parent}
        for node in root.iter('node'):
            if node.get('text') != title and node.get('content-desc') != title:
                continue
            while node.get('clickable') != 'true' and node in parents:
                node = parents[node]
            bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
            if len(bounds) == 4 and node.get('clickable') == 'true' and 300 < (bounds[1]+bounds[3])/2 < 2100:
                return (bounds[0]+bounds[2])/2/sx, (bounds[1]+bounds[3])/2/sy
        guard()
        swipe(up)
        time.sleep(.3)
    raise AssertionError('Unreachable target: ' + title)


def root_page(key):
    tap(245 if key == 'home' else 1035, 2420)
    time.sleep(.8)
    for _ in range(4 if key == 'home' else 2):
        swipe(down)
        time.sleep(.2)


def opens(point, repeats, back_steps=1):
    for hold in (.08, .15, .3):
        for _ in range(repeats):
            tap(*point)
            time.sleep(hold)
            for _ in range(back_steps):
                adb('shell', 'input', 'keyevent', 4)
            time.sleep(.08)


def require_root_navigation():
    nodes=list(inspect().iter('node'))
    # The selected glass indicator draws its label and removes duplicate semantics.
    # These unselected controls remain semantic on both home and profile roots.
    assert all(any(n.get('text')==label or n.get('content-desc')==label for n in nodes)
               for label in ('课程表','我')), 'Root navigation did not return; discard scenario.'


def return_to_root():
    # Result screens own an additional BackHandler. During an interrupted open
    # it may not exist yet, so a fixed number of BACK events can exit the app.
    # Inspect only outside performance windows; never infer the navigation depth.
    for step in range(4):
        try:
            require_root_navigation()
            return step
        except AssertionError:
            if step == 3:
                raise
        adb('shell', 'input', 'keyevent', 4)
        time.sleep(.8)


def within_frame_target():
    # Keep the stricter presented-frame result whenever available. A vendor's
    # lax HWUI deadline must not make a failing FrameTimeline scenario pass.
    return all(max(r['rate'],r.get('frametimeline',{}).get('janky_percent',0)) < args.max_missed_percent
               for r in results.values())


def measure(label, action):
    guard()
    pid = adb('shell', 'pidof', args.package).strip()
    log_start = adb('shell','date','+%s.%N').strip()
    trace_pid = None
    trace_remote='/data/misc/perfetto-traces/xachet-measure.trace'
    if args.trace:
        config='''buffers { size_kb: 8192 fill_policy: RING_BUFFER }
duration_ms: 60000
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
'''
        tracing=subprocess.run(command+['shell','perfetto','--background-wait','--txt','-c','-','-o',trace_remote],input=config,text=True,capture_output=True,check=True)
        trace_pid=int(tracing.stdout.strip())
    adb('shell', 'dumpsys', 'gfxinfo', args.package, 'reset')
    try:
        action()
    finally:
        if trace_pid is not None:
            adb('shell','kill','-TERM',trace_pid)
    time.sleep(.6)
    stats = adb('shell', 'dumpsys', 'gfxinfo', args.package, 'framestats')
    (output / f'{args.tag}-{label}-gfxinfo.txt').write_text(stats, encoding='utf-8')

    def count(pattern):
        match = re.search(pattern, stats, re.M)
        return int(match[1]) if match else None

    total = count(r'^Total frames rendered:\s*(\d+)')
    missed = count(r'^Number Frame deadline missed:\s*(\d+)')
    assert total and missed is not None, 'No current Android deadline data.'
    header = next((line for line in stats.splitlines() if line.startswith('Flags,')), None)
    assert header is not None, 'No live window frame data; discard this scenario.'
    columns = header.strip(',').split(',')
    rows = [dict(zip(columns, map(int, line.strip(',').split(','))))
            for line in stats.splitlines() if re.match(r'^\d+,\d', line)]
    flags = {str(flag): sum(r.get('Flags')==flag for r in rows) for flag in set(r.get('Flags') for r in rows)}
    rows = [r for r in rows if not r.get('Flags',0)&9]  # first draw / skipped; retain and report vendor bits
    intervals = [row['FrameInterval'] for row in rows if row.get('FrameInterval', 0) > 0]
    durations = sorted((r['FrameCompleted']-r['IntendedVsync'])/1e6 for r in rows
                       if 0 < r.get('FrameCompleted', 0)-r.get('IntendedVsync', 0) < 10_000_000_000)
    # Frame-processing latency proxy, not presentation-based FPS or input-to-photon latency.
    slowest = durations[-max(1, (len(durations)+99)//100):]
    tail = statistics.mean(slowest) if slowest else None
    results[label] = dict(flag_counts=flags, sampled_frames=len(durations), sample_scope='gfxinfo recent per-window ring buffer, not complete run', slowest_1pct_mean_ms=tail,
                          processing_1pct_low_proxy_fps=1000/tail if tail else None,
                          max_frame_ms=durations[-1] if durations else None,
                          device=args.serial, resolution=screen_size,
                          frames=total, missed=missed, rate=round(100*missed/total, 3),
                          legacy_janky=count(r'^Janky frames \(legacy\):\s*(\d+)'),
                          p95_ms=count(r'^95th percentile:\s*(\d+)'),
                          p99_ms=count(r'^99th percentile:\s*(\d+)'),
                          median_interval_ns=statistics.median(intervals) if intervals else None,
                          same_process=pid == adb('shell', 'pidof', args.package).strip())
    logs=adb('logcat','-d','-v','epoch','--pid='+pid,'XachetInputFrame:I','*:S')
    response=[int(m[2])/1e6 for line in logs.splitlines() if (m:=re.match(r'^\s*(\d+\.\d+).*dispatch_to_callback_ns=(\d+)',line)) and float(m[1])>=float(log_start)]
    results[label]['dispatch_to_frame_callback_p95_ms']=sorted(response)[min(len(response)-1,int(len(response)*.95))] if response else None
    if trace_pid is not None:
        trace_path=output/f'{args.tag}-{label}.perfetto-trace'
        adb('pull',trace_remote,trace_path)
        from perfetto.trace_processor import TraceProcessor
        with TraceProcessor(trace=str(trace_path)) as processor:
            assert re.fullmatch(r'[a-zA-Z0-9_.]+',args.package)
            # Vendor traces omit process names. Surface identity resolves the owner;
            # all its Popup windows are included, rather than only the main surface.
            records=list(processor.query(f"""select jank_type,present_type,count(*) as frames from actual_frame_timeline_slice
where dur>0 and upid in (select distinct upid from actual_frame_timeline_slice where layer_name like '%{args.package}%')
group by jank_type,present_type"""))
            assert records,'No app FrameTimeline data; do not accept this run.'
            presented=sum(r.frames for r in records if r.jank_type!='Non Animating')
            bad=sum(r.frames for r in records if r.jank_type not in ('None','Buffer Stuffing','Non Animating'))
            stuffing=sum(r.frames for r in records if r.jank_type=='Buffer Stuffing')
            results[label]['frametimeline']=dict(frames=presented,janky=bad,janky_percent=round(100*bad/presented,3),
                buffer_stuffing_percent=round(100*stuffing/presented,3),
                classifications=[dict(jank_type=r.jank_type,present_type=r.present_type,frames=r.frames) for r in records])
    (output / f'{args.tag}-results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    print(label, json.dumps(results[label]), flush=True)
    assert results[label]['same_process'], 'Process restarted.'
    guard()


size = adb('shell', 'wm', 'size')
if args.suite not in ('focus', 'controls'):
    parser.error('Use performance_regression.py for hotpaths, menus and profile.')
assert screen_size in ((1216,2640),(1220,2656)), 'Validate this device layout before testing.'
rotation = adb('shell', 'settings', 'get', 'system', 'user_rotation').strip()
auto = adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').strip()
try:
    adb('shell', 'wm', 'user-rotation', 'lock', 0)
    adb('shell', 'am', 'force-stop', args.package)
    launch = ['shell', 'am', 'start', '-W', '-n', args.package+'/com.pluvist.sachet.next.MainActivity']
    if args.package.endswith('.benchmark'):
        launch += ['--ez', 'load_fixture', 'true']
    adb(*launch)
    time.sleep(1.6)
    guard()
    if args.suite == 'focus':
        root_page('home')
        point = find('绩点排名')
        measure('home_fast_controls', lambda: opens(point, 6))
        measure('home_scroll', lambda: motions([up, down]*10))
        for key, title in [('appearance', '课表外观'), ('settings', '设置'), ('files', '导入与导出课表')]:
            root_page('profile')
            point = find(title)
            measure(key+'_fast_controls', lambda: opens(point, 6))
        root_page('profile')
        measure('profile_scroll', lambda: motions([up, down]*10))

        def switch():
            for index in range(24):
                tap((245, 1035)[index % 2], 2420)
                time.sleep(.08)
        measure('home_profile_fast_tabs', switch)
    else:
        groups = [('home', [('cultivation', '培养方案'), ('textbooks', '教材预订'), ('exams', '考试时间'),
                            ('grades', '成绩查询'), ('gpa', '绩点排名'), ('transcript', '成绩单'), ('classrooms', '空闲教室')]),
                  ('profile', [('login', '登录教务账号'), ('appearance', '课表外观'), ('settings', '设置'),
                               ('permissions', '后台运行与通知权限'), ('files', '导入与导出课表'),
                               ('file_manager', '数据文件'), ('about', 'Xachet')])]
        for page, targets in groups:
            for key, title in targets:
                if args.only and key not in args.only.split(','):
                    continue
                root_page(page)
                point = find(title)
                tap(*point)
                time.sleep(1)
                tree=inspect()
                texts=[n.get('text') for n in tree.iter('node') if n.get('text')]
                expected={'permissions':'后台与通知','files':'导入与导出','about':'关于'}.get(key,title)
                assert any(expected in t for t in texts), 'Destination missing: '+expected
                shot=output/f'{args.tag}-{page}-{key}.png'
                adb('shell','screencap','-p','/sdcard/xachet-navigation.png')
                adb('pull','/sdcard/xachet-navigation.png',shot)
                steps=return_to_root()
                results[page+'_'+key]=dict(destination=expected,returned=True,back_steps=steps,
                                         screenshot=str(shot),scope='settled functional navigation; not performance')
                (output/f'{args.tag}-results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
                print(page+'_'+key,results[page+'_'+key],flush=True)
    if args.suite!='controls':
        assert within_frame_target(), f'Some scenarios miss the {args.max_missed_percent}% target; see JSON.'
finally:
    adb('shell', 'wm', 'user-rotation', 'free' if auto == '1' else 'lock', *([] if auto == '1' else [rotation]))
