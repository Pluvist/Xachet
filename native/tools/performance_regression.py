from pathlib import Path

# Bounded synthetic-data regression suite. Never runs against a user account.
collector = Path(__file__).resolve().parents[1] / 'tools' / 'performance.py'
exec(compile(collector.read_text(encoding='utf-8').split('\nsize = adb(')[0], str(collector), 'exec'))

assert args.package.endswith('.benchmark')
assert screen_size in ((1216,2640),(1220,2656))
rotation = adb('shell', 'settings', 'get', 'system', 'user_rotation').strip()
auto = adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').strip()
def reset():
    adb('shell', 'am', 'force-stop', args.package)
    adb('shell', 'am', 'start', '-W', '-n', args.package + '/com.pluvist.sachet.next.MainActivity', '--ez', 'load_fixture', 'true')
    time.sleep(2)
    root_page('home')


def require_text(text):
    assert any(text in (n.get('text', '') + n.get('content-desc', '')) for n in inspect().iter('node')), 'Wrong page: ' + text


assert args.suite in ('hotpaths', 'menus', 'profile'), 'Choose --suite hotpaths, menus or profile'
try:
    if args.suite == 'hotpaths':
        adb('shell', 'wm', 'user-rotation', 'lock', 0)
        reset()
        require_text('快捷功能')
        measure('home_scroll', lambda: motions([up, down] * 5))
        require_text('快捷功能')
        reset()
        measure('status_panel', lambda: opens((600, 2200), 3))
        require_text('快捷功能')
        reset()
        tap(545, 2430)
        time.sleep(1)
        require_text('第1周')
        measure('schedule_paging', lambda: motions([(1050, 1300, 180, 1300, 350), (180, 1300, 1050, 1300, 350)] * 5))
        require_text('第1周')
        measure('course_panel', lambda: opens((200, 750), 3))
        require_text('第1周')
        assert within_frame_target(), 'Frame target not reached; see JSON.'
    elif args.suite == 'menus':
        adb('shell', 'wm', 'user-rotation', 'lock', 0)
        reset()
        tap(545, 2430)
        time.sleep(1)
        require_text('第1周')
        # Toolbar bounds have been checked against a fresh UIAutomator tree.
        tap(835, 225)
        time.sleep(1)
        require_text('月视图')
        adb('shell', 'input', 'keyevent', 4)
        time.sleep(1)
        def regular():
            for _ in range(9):
                tap(835, 225)
                time.sleep(.7)
                adb('shell', 'input', 'keyevent', 4)
                time.sleep(.7)
        measure('menu_regular', regular)
        require_text('第1周')
        def interrupt():
            for hold in (.08, .15, .3):
                for _ in range(3):
                    tap(835, 225)
                    time.sleep(hold)
                    # Empty right time axis: closes a popup without navigating the page
                    # if the next source tap arrives before its previous closing flight.
                    tap(1195, 1200)
                    time.sleep(.08)
        measure('menu_interrupt', interrupt)
        require_text('第1周')
        assert within_frame_target(), 'Menu misses target; see JSON.'
    elif args.suite == 'profile':
        adb('shell', 'wm', 'user-rotation', 'lock', 0)
        reset()
        root_page('profile')
        for title,label in [('设置','settings'),('课表外观','appearance')]:
            point=find(title)
            tap(*point)
            time.sleep(1)
            adb('shell','input','keyevent',4)
            time.sleep(1)
            point=find(title)
            measure(label,lambda:opens(point,3))
            require_text('设置')
        measure('profile_scroll', lambda: motions([up, down]*5))
        require_text('设置')
        assert within_frame_target(), 'Target not reached; see JSON.'
finally:
    adb('shell', 'wm', 'user-rotation', 'free' if auto == '1' else 'lock', *([] if auto == '1' else [rotation]))
