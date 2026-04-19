#!/usr/bin/env python3
import ssl
import time
import statistics
import argparse
import http.client
from urllib.parse import urlparse


def percentile(values, p):
    if not values:
        return 0.0
    values = sorted(values)
    k = (len(values) - 1) * (p / 100)
    f = int(k)
    c = min(f + 1, len(values) - 1)
    if f == c:
        return values[f]
    return values[f] * (c - k) + values[c] * (k - f)


def run_bench(url, warmup, n, timeout):
    parsed = urlparse(url)
    host = parsed.hostname
    port = parsed.port or (443 if parsed.scheme == 'https' else 80)
    path = parsed.path or '/'
    if parsed.query:
        path = f"{path}?{parsed.query}"

    if parsed.scheme == 'https':
        context = ssl.create_default_context()
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        conn = http.client.HTTPSConnection(host, port, context=context, timeout=timeout)
    else:
        conn = http.client.HTTPConnection(host, port, timeout=timeout)

    headers = {
        'Host': host,
        'User-Agent': 'bench-stable/1.0',
        'Accept': '*/*',
        'Connection': 'keep-alive',
    }

    last_token = None

    def one_request():
        nonlocal last_token
        start = time.perf_counter()
        conn.request('GET', path, headers=headers)
        resp = conn.getresponse()
        _ = resp.read()
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        token = resp.getheader('x-debug-token')
        if token:
            last_token = token
        return elapsed_ms, resp.status

    for _ in range(warmup):
        try:
            one_request()
        except Exception:
            try:
                conn.close()
            except Exception:
                pass
            if parsed.scheme == 'https':
                conn = http.client.HTTPSConnection(host, port, context=context, timeout=timeout)
            else:
                conn = http.client.HTTPConnection(host, port, timeout=timeout)

    values = []
    statuses = {}
    for _ in range(n):
        try:
            ms, status = one_request()
            values.append(ms)
            statuses[status] = statuses.get(status, 0) + 1
        except Exception:
            try:
                conn.close()
            except Exception:
                pass
            if parsed.scheme == 'https':
                conn = http.client.HTTPSConnection(host, port, context=context, timeout=timeout)
            else:
                conn = http.client.HTTPConnection(host, port, timeout=timeout)

    try:
        conn.close()
    except Exception:
        pass

    if not values:
        raise RuntimeError('Aucune mesure collectée.')

    avg = statistics.fmean(values)
    mn = min(values)
    mx = max(values)
    p95 = percentile(values, 95)

    trim = sorted(values)
    cut = max(1, int(len(trim) * 0.05))
    core = trim[cut:-cut] if len(trim) > 2 * cut else trim
    trimmed_avg = statistics.fmean(core)

    return {
        'avg': avg,
        'min': mn,
        'max': mx,
        'p95': p95,
        'trimmed_avg': trimmed_avg,
        'n': len(values),
        'statuses': statuses,
        'token': last_token,
        'base': f"{parsed.scheme}://{host}:{port}",
    }


def print_result(title, url, result):
    print(f"=== {title} ===")
    print(f"URL: {url}")
    print(
        f"Avg: {result['avg']:.2f} ms | TrimmedAvg: {result['trimmed_avg']:.2f} ms | "
        f"P95: {result['p95']:.2f} ms | Min: {result['min']:.2f} ms | Max: {result['max']:.2f} ms | N: {result['n']}"
    )
    print("HTTP status:", result['statuses'])
    if result['token']:
        print(f"Profiler (mémoire): {result['base']}/_profiler/{result['token']}?panel=time")
    print()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Stable benchmark with keep-alive connection.')
    parser.add_argument('--home', default='https://santea.tn:8443/', help='Home URL')
    parser.add_argument('--feature', default='https://santea.tn:8443/messages/api/conversations', help='Feature URL')
    parser.add_argument('-n', type=int, default=30, help='Measured requests per URL')
    parser.add_argument('--warmup', type=int, default=8, help='Warmup requests per URL')
    parser.add_argument('--timeout', type=int, default=20, help='Socket timeout seconds')
    args = parser.parse_args()

    home_result = run_bench(args.home, args.warmup, args.n, args.timeout)
    feature_result = run_bench(args.feature, args.warmup, args.n, args.timeout)

    print_result('Page d\'accueil', args.home, home_result)
    print_result('Fonctionnalité principale', args.feature, feature_result)
