#!/usr/bin/env python3
"""Runs the self-test of a Rocket Show unit and prints its checks.

Before the initial setup of a unit (e.g. at the factory), no password is needed. Afterwards, the self-test needs the
admin password (--password or the environment variable RS_ADMIN_PASSWORD).

    ./selftest.py http://rocketshow.local
    ./selftest.py http://192.168.1.50 --expect midi-loopback gpio-loopback --gpio-loopback 22:27 --output results/

Exits with 0 when the self-test passed, 1 when a check failed and 2 when the unit refused it (e.g. a composition is
playing) or couldn't be reached. Only the Python standard library is needed, e.g. on a Raspberry Pi.
"""

import argparse
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.request


def parse_gpio_loopback(value):
    try:
        out_pin, in_pin = value.split(":")
        return {"out": int(out_pin), "in": int(in_pin)}
    except ValueError:
        raise argparse.ArgumentTypeError(f"{value} is not OUT:IN, e.g. 22:27")


def post(opener, url, body, timeout):
    request = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    with opener.open(request, timeout=timeout) as response:
        return json.loads(response.read() or b"null")


def error_message(error):
    try:
        return json.loads(error.read()).get("message") or error.reason
    except (ValueError, AttributeError):
        return error.reason


def print_result(result):
    print(f"Self-test {result['result']} in {result['durationMillis'] / 1000:.1f} s, started {result['startedAt']}")
    print(f"Software {result.get('softwareVersion', 'unknown')}, serial {result.get('serial', 'unknown')}")
    print()

    rows = [(check["name"], check.get("target", ""), check["result"], " ".join(check.get("details", "").split()))
            for check in result["checks"]]
    widths = [max(len(row[column]) for row in rows + [("Check", "Target", "Result", "")]) for column in range(3)]
    print(f"{'Check':<{widths[0]}}  {'Target':<{widths[1]}}  {'Result':<{widths[2]}}  Details")
    for name, target, outcome, details in rows:
        print(f"{name:<{widths[0]}}  {target:<{widths[1]}}  {outcome:<{widths[2]}}  {details}")


def main():
    parser = argparse.ArgumentParser(description="Runs the self-test of a Rocket Show unit and prints its checks.")
    parser.add_argument("url", help="the unit, e.g. http://rocketshow.local")
    parser.add_argument("--password", default=os.environ.get("RS_ADMIN_PASSWORD"),
                        help="the admin password, once the unit is set up (default: $RS_ADMIN_PASSWORD)")
    parser.add_argument("--expect", nargs="+", default=[], metavar="CHECK",
                        help="checks which must pass, e.g. midi-loopback: skipping them fails the self-test")
    parser.add_argument("--gpio-loopback", nargs="+", default=[], type=parse_gpio_loopback, metavar="OUT:IN",
                        help="GPIO pins connected by a loopback cable, e.g. 22:27")
    parser.add_argument("--output", metavar="DIRECTORY", help="keep the result as JSON, named by the serial number")
    parser.add_argument("--timeout", type=float, default=120, help="seconds to wait for the unit (default: 120)")
    arguments = parser.parse_args()

    api = arguments.url.rstrip("/") + "/api/"
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    try:
        if arguments.password:
            try:
                post(opener, api + "auth/login", {"password": arguments.password}, arguments.timeout)
            except urllib.error.HTTPError as error:
                print(f"Could not log in ({error.code}): wrong admin password?", file=sys.stderr)
                return 2

        result = post(opener, api + "system/test", {"expect": arguments.expect, "gpioLoopback": arguments.gpio_loopback},
                      arguments.timeout)
    except urllib.error.HTTPError as error:
        reason = "the unit is set up, log in with --password" if error.code in (401, 403) else error_message(error)
        print(f"The unit refused the self-test ({error.code}): {reason}", file=sys.stderr)
        return 2
    except (urllib.error.URLError, TimeoutError) as error:
        print(f"Could not reach the unit: {error}", file=sys.stderr)
        return 2

    print_result(result)

    if arguments.output:
        os.makedirs(arguments.output, exist_ok=True)
        name = (result.get("serial") or arguments.url.split("//")[-1].strip("/").replace(":", "_")) + "-" + result["startedAt"].replace(":", "")
        path = os.path.join(arguments.output, name + ".json")
        with open(path, "w") as file:
            json.dump(result, file, indent=2, ensure_ascii=False)
        print()
        print(f"Kept in {path}")

    return 0 if result["result"] == "PASSED" else 1


if __name__ == "__main__":
    sys.exit(main())
