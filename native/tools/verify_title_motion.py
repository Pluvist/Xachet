"""Visual-only title flight checks. Recordings are never performance evidence."""
from pathlib import Path
collector=Path(__file__).with_name('performance.py')
exec(compile(collector.read_text(encoding='utf-8').split('\nsize = adb(')[0],str(collector),'exec'))
assert args.package.endswith('.benchmark'), 'Only use the synthetic isolated package.'
adb('shell','am','force-stop',args.package)
adb('shell','am','start','-W','-n',args.package+'/com.pluvist.sachet.next.MainActivity','--ez','load_fixture','true')
time.sleep(2)
timings={}
for page,title in [('home','绩点排名'),('profile','设置')]:
    root_page(page)
    point=find(title)
    remote='/sdcard/xachet-title-motion.mp4'
    recorder=subprocess.Popen(command+['shell','screenrecord','--time-limit','8',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    start=time.monotonic()
    time.sleep(.7)
    before=time.monotonic()-start
    tap(*point)
    after=time.monotonic()-start
    time.sleep(1.1)
    close_before=time.monotonic()-start
    adb('shell','input','keyevent',4)
    close_after=time.monotonic()-start
    time.sleep(.8)
    tap(*point)
    time.sleep(.04)
    adb('shell','input','keyevent',4)
    time.sleep(.08)
    tap(*point)
    time.sleep(.9)
    adb('shell','input','keyevent',4)
    time.sleep(.9)
    recorder.wait(timeout=15)
    assert recorder.returncode==0,recorder.stderr.read()
    adb('pull',remote,output/f'{args.tag}-{page}.mp4')
    require_root_navigation()
    timings[page]=dict(title=title,tap_start=before,tap_return=after,close_start=close_before,
                      close_return=close_after,returned=True)
    print(page,'recorded and returned',flush=True)
root_page('profile')
point=find('课表外观')
tap(*point);time.sleep(1)
swipe(up);time.sleep(1)
guard()
remote='/sdcard/xachet-title-motion.mp4'
recorder=subprocess.Popen(command+['shell','screenrecord','--time-limit','4',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
start=time.monotonic();time.sleep(.7)
before=time.monotonic()-start
adb('shell','input','keyevent',4)
after=time.monotonic()-start
recorder.wait(timeout=10)
assert recorder.returncode==0,recorder.stderr.read()
adb('pull',remote,output/f'{args.tag}-collapsed.mp4')
require_root_navigation()
timings['collapsed']=dict(title='课表外观',close_start=before,close_return=after,returned=True)
(output/f'{args.tag}-video-timings.json').write_text(json.dumps(timings,indent=2),encoding='utf-8')
