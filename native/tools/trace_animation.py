"""Bounded FrameTimeline capture on the isolated benchmark package."""
from pathlib import Path
collector=Path(__file__).with_name('performance.py')
exec(compile(collector.read_text(encoding='utf-8').split('\nsize = adb(')[0],str(collector),'exec'))
assert args.package.endswith('.benchmark')
assert screen_size in ((1216,2640),(1220,2656))
guard()
if args.suite=='hotpaths':
    tap(545,2430);time.sleep(1)
    assert any('第1周' in (n.get('text','')+n.get('content-desc','')) for n in inspect().iter('node'))
    point=(200,750)
else:
    root_page('profile')
    point=find('设置')
tap(*point)
time.sleep(1)
adb('shell','input','keyevent',4)
time.sleep(1)
guard()
config='''buffers { size_kb: 32768 fill_policy: RING_BUFFER }
duration_ms: 25000
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
data_sources { config { name: "linux.ftrace" ftrace_config {
 atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "input"
 atrace_apps: "com.pluvist.xachet.benchmark"
 ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_waking"
} } }
data_sources { config { name: "linux.process_stats" process_stats_config { scan_all_processes_on_start: true } } }
'''
path=output/(args.tag+'.pbtxt');path.write_text(config)
record=subprocess.Popen(command+['shell','perfetto','--txt','-c','-','-o','/data/misc/perfetto-traces/xachet.trace'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
record.stdin.write(config.encode());record.stdin.close()
time.sleep(1)
opens(point,4)
record.wait(timeout=45)
record_error=record.stderr.read()
assert record.returncode==0,record_error.decode(errors='replace')
adb('pull','/data/misc/perfetto-traces/xachet.trace',output/(args.tag+'.perfetto-trace'))
print('Captured',args.tag,flush=True)
