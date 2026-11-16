"""Phone-only integration check; creates one labeled, loopback-published container."""
import json
import time
import urllib.request
from pathlib import Path

BASE = 'http://127.0.0.1:9473/v1/'

def call(method, path, data=None):
    auth = Path('/etc/kela/token').read_text().strip()
    body = json.dumps(data).encode() if data is not None else None
    request = urllib.request.Request(BASE + path, data=body, method=method,
        headers={'Authorization': 'Bearer ' + auth, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=50) as response:
        return json.load(response)

if __name__ == '__main__':
    print('Engine:', call('GET', 'overview')['version'])
    existing = call('GET', 'containers')
    matches = [c for c in existing if '/kela-smoke' in c['Names']]
    if matches:
        assert matches[0].get('Labels', {}).get('com.krashkanter.kela.managed') == 'true'
    result = {'Id': matches[0]['Id']} if matches else call('POST', 'containers', {'name': 'kela-smoke', 'image': 'nginx:stable-alpine',
        'ports': ['127.0.0.1:18081:80'], 'restart': 'no', 'network': 'phone-edge'})
    target = 'containers/' + result['Id']
    assert not result.get('warning'), result.get('warning')
    if not call('GET', target)['State']['Running']:
        call('POST', target + '/start')
    assert call('GET', target)['State']['Running']
    for attempt in range(20):
        try:
            with urllib.request.urlopen('http://127.0.0.1:18081/', timeout=5) as response:
                assert response.status == 200
            break
        except OSError:
            if attempt == 19:
                raise
            time.sleep(0.5)
    print('PASS create, inspect, published HTTP port')
    assert 'text' in call('GET', target + '/logs')
    call('POST', target + '/stop')
    assert not call('GET', target)['State']['Running']
    call('POST', target + '/start')
    call('POST', target + '/restart')
    assert call('GET', target)['State']['Running']
    print('PASS logs, stop, start, restart')