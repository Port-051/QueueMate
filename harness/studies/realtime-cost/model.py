"""Monthly scenario calculator, not a performance benchmark. Python standard library only."""
from dataclasses import asdict, dataclass, replace
import argparse
import json
import math
from pathlib import Path

RATES = json.loads(Path(__file__).with_name("rates.json").read_text())
MILLION = 1_000_000


def tiered(quantity, tiers):
    """Tiers contain cumulative upper bounds; None means unbounded."""
    total, previous = 0.0, 0.0
    for upper, rate in tiers:
        end = quantity if upper is None else min(quantity, upper)
        total += max(0, end - previous) * rate
        if upper is None or quantity <= upper:
            break
        previous = upper
    return total


def blocks(quantity, included, unit_price):
    # DO billing rounds excess to whole million-unit blocks (2026-09-15 docs).
    return math.ceil(max(0, quantity - included) / MILLION) * unit_price


@dataclass(frozen=True)
class Scenario:
    concurrent: int = 100
    hours_per_day: float = 1
    days: int = 30
    connection_lifetime_minutes: float = 60
    subscriptions: int = 2
    board_events_per_second: float = 0.1
    board_audience_fraction: float = 1
    private_events_per_user_hour: float = 6
    signals_per_user_hour: float = 20
    payload_bytes: int = 1024
    cf_shard_size: int = 1000
    cf_handler_ms: float = 10
    cf_send_ms: float = 0.02
    cf_worker_cpu_ms: float = 2
    remaining_aws_free_gb: float = 0


def calculate(s):
    if (s.concurrent < 1 or not 0 < s.hours_per_day <= 24 or s.days < 1
            or s.connection_lifetime_minutes <= 0 or s.subscriptions < 1
            or not 0 <= s.board_audience_fraction <= 1 or s.payload_bytes < 1
            or s.cf_shard_size < 1 or any(value < 0 for value in [
                s.board_events_per_second, s.private_events_per_user_hour,
                s.signals_per_user_hour, s.cf_handler_ms, s.cf_send_ms,
                s.cf_worker_cpu_ms, s.remaining_aws_free_gb])):
        raise ValueError("Invalid scenario")
    hours = s.hours_per_day * s.days
    user_hours = hours * s.concurrent
    minutes = user_hours * 60
    connections = s.concurrent * s.days * math.ceil(
        s.hours_per_day * 60 / s.connection_lifetime_minutes)
    board = hours * 3600 * s.board_events_per_second
    recipients = s.concurrent * s.board_audience_fraction
    private = user_hours * (s.private_events_per_user_hour + s.signals_per_user_hour)
    published = board + private
    delivered = board * recipients + private
    # AWS GB conversion is explicitly 2**30 bytes in this model; protocol overhead excluded.
    delivery_gb = delivered * s.payload_bytes / 2**30

    def egress(gb):
        return tiered(max(0, gb - s.remaining_aws_free_gb), RATES["aws_egress_tiers"])

    app_units = math.ceil(s.payload_bytes / (5 * 1024))
    # One onSubscribe authorization handler per subscription is explicitly budgeted.
    app_control = connections * (1 + 2 * s.subscriptions)
    app_ops = (published + delivered) * app_units + app_control
    app_base = app_ops * RATES["appsync_operation"] + minutes * RATES["appsync_minute"]
    app_egress = egress(delivery_gb)
    # Upper sensitivity: one additional billable keepalive operation per minute.
    # This is NOT a claim that server-originated ka is billed.
    app_keepalive_sensitivity = minutes * RATES["appsync_operation"]

    # Signals enter Spring over authenticated REST for both proposed managed relays.
    # Each checked signal is then a server-to-client private publish, included above.
    # API Gateway has no channel fanout: each outbound delivery is sent separately.
    gateway_units = math.ceil(s.payload_bytes / (32 * 1024))
    gateway = tiered(delivered * gateway_units, RATES["gateway_message_tiers"])
    gateway += minutes * RATES["gateway_minute"] + app_egress

    shards = math.ceil(s.concurrent / s.cf_shard_size)
    # Conservative: send each board change to all shards, filter recipients there.
    cf_publishes = private + board * shards
    cf_requests = connections + cf_publishes
    cf_active_seconds = min(shards * hours * 3600,
                            (cf_requests * s.cf_handler_ms + delivered * s.cf_send_ms) / 1000)
    cf_gb_seconds = cf_active_seconds * 0.128
    cf_do_cost = blocks(cf_requests, 1_000_000, RATES["cf_do_million_requests"])
    cf_do_cost += blocks(cf_gb_seconds, 400_000, RATES["cf_do_million_gb_seconds"])
    cf_worker_cost = max(0, cf_requests - 10_000_000) / MILLION * RATES["cf_worker_million_requests"]
    cf_worker_cost += max(0, cf_requests * s.cf_worker_cpu_ms - 30_000_000) / MILLION * RATES["cf_worker_million_cpu_ms"]
    # AWS->Cloudflare publisher traffic is separate from Cloudflare client egress.
    cf_origin_gb = cf_publishes * s.payload_bytes / 2**30
    cf_cost = RATES["cf_monthly_base"] + cf_do_cost + cf_worker_cost + egress(cf_origin_gb)
    # Quota arithmetic only: not a measured guarantee of per-request CPU or availability.
    cf_free_fits = (cf_requests / s.days <= 100_000
                    and cf_gb_seconds / s.days <= 13_000 and s.cf_worker_cpu_ms <= 10)

    def polling(interval, duration_ms):
        requests = user_hours * 3600 / interval
        api = tiered(requests, RATES["http_request_tiers"])
        execution = tiered(requests * duration_ms / 1000 * 0.5, RATES["lambda_gb_second_tiers"])
        return api + requests * RATES["lambda_request"] + execution

    return {
        "scenario": asdict(s),
        "usage": {"user_hours": user_hours, "connections": connections,
                  "connection_minutes": minutes, "published": published,
                  "delivered": delivered, "delivery_gb": delivery_gb,
                  "cf_requests": cf_requests, "cf_gb_seconds": cf_gb_seconds,
                  "cf_origin_gb": cf_origin_gb,
                  "cf_free_daily_quotas_fit_assumptions": cf_free_fits,
                  "match_poll_3s_requests": user_hours * 3600 / 3,
                  "board_poll_15s_requests_one_page": user_hours * 3600 / 15 * 2},
        "monthly_usd": {
            "appsync_transport": app_base,
            "appsync_with_egress": app_base + app_egress,
            "appsync_with_egress_and_keepalive_sensitivity": app_base + app_egress + app_keepalive_sensitivity,
            "api_gateway_with_egress_excluding_registry": gateway,
            "cloudflare_hibernation_with_origin_egress": cf_cost,
            "cloudflare_platform_only": cf_cost - egress(cf_origin_gb),
            "cloudflare_free_with_origin_egress_if_quotas_fit": egress(cf_origin_gb) if cf_free_fits else None,
            "lambda_sse_execution_only": tiered(user_hours * 3600 * 0.5, RATES["lambda_gb_second_tiers"]),
            "lambda_poll_1s_20ms_excluding_egress": polling(1, 20),
            "lambda_poll_1s_100ms_excluding_egress": polling(1, 100),
            "lambda_poll_1s_955ms_excluding_egress": polling(1, 955),
        },
    }


def suite():
    base = [Scenario(concurrent=n, hours_per_day=h)
            for h in (1, 8, 24) for n in (100, 1000, 10000)]
    return {
        "kind": "calculation_with_assumed_workload_not_benchmark",
        "verified_date": RATES["verified_date"],
        "baseline": [calculate(s) for s in base],
        "sensitivity_1000_users_8h": [
            {"name": name, **calculate(replace(Scenario(concurrent=1000, hours_per_day=8), **change))}
            for name, change in [
                ("private_only", {"board_events_per_second": 0}),
                ("board_audience_10_percent", {"board_audience_fraction": .1}),
                ("board_1_event_per_second", {"board_events_per_second": 1}),
                ("connections_15_minutes", {"connection_lifetime_minutes": 15}),
                ("payload_6KiB", {"payload_bytes": 6144}),
                ("cf_handler_100ms", {"cf_handler_ms": 100}),
                ("cf_one_object_per_user_100ms", {"cf_shard_size": 1, "cf_handler_ms": 100}),
                ("aws_free_100GB_remaining", {"remaining_aws_free_gb": 100}),
            ]
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", type=Path, help="JSON object with Scenario overrides")
    args = parser.parse_args()
    output = calculate(Scenario(**json.loads(args.scenario.read_text()))) if args.scenario else suite()
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
