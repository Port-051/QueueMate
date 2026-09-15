import unittest
from dataclasses import replace

from model import RATES, Scenario, blocks, calculate, tiered


class CostModelTest(unittest.TestCase):
    def test_published_workload_conversion(self):
        result = calculate(Scenario(private_events_per_user_hour=0, signals_per_user_hour=0))
        self.assertEqual(result["usage"]["connection_minutes"], 180_000)
        self.assertEqual(result["usage"]["connections"], 3_000)
        self.assertEqual(result["usage"]["published"], 10_800)
        self.assertEqual(result["usage"]["delivered"], 1_080_000)
        self.assertAlmostEqual(result["monthly_usd"]["lambda_sse_execution_only"], 90, places=3)

    def test_rate_tiers_charge_only_the_excess(self):
        self.assertEqual(tiered(0, [[10, 2], [None, 1]]), 0)
        self.assertEqual(tiered(10, [[10, 2], [None, 1]]), 20)
        self.assertEqual(tiered(12, [[10, 2], [None, 1]]), 22)
        self.assertAlmostEqual(tiered(1_100_000_000, RATES["gateway_message_tiers"]), 1234)

    def test_cloudflare_documented_billing_blocks(self):
        self.assertEqual(blocks(400_000, 400_000, 12.5), 0)
        self.assertEqual(blocks(400_001, 400_000, 12.5), 12.5)
        self.assertEqual(blocks(552_960, 400_000, 12.5), 12.5)
        self.assertAlmostEqual(blocks(21_610_000, 1_000_000, .15), 3.15)

    def test_payload_crosses_appsync_billing_boundary(self):
        small = calculate(Scenario(payload_bytes=5120))["monthly_usd"]["appsync_transport"]
        large = calculate(Scenario(payload_bytes=5121))["monthly_usd"]["appsync_transport"]
        usage = calculate(Scenario())["usage"]
        self.assertAlmostEqual(large - small, (usage["published"] + usage["delivered"]) / 1e6)

    def test_egress_allowance_does_not_make_cost_negative(self):
        result = calculate(Scenario(remaining_aws_free_gb=100))
        cost = result["monthly_usd"]
        self.assertEqual(cost["appsync_with_egress"], cost["appsync_transport"])
        self.assertEqual(cost["cloudflare_hibernation_with_origin_egress"], 5)

    def test_reconnections_and_narrower_audience(self):
        base = Scenario(concurrent=1000, hours_per_day=8)
        usual = calculate(base)
        reconnect = calculate(replace(base, connection_lifetime_minutes=15))
        narrow = calculate(replace(base, board_audience_fraction=.1))
        self.assertEqual(reconnect["usage"]["connections"], usual["usage"]["connections"] * 4)
        self.assertEqual(reconnect["usage"]["delivered"], usual["usage"]["delivered"])
        self.assertLess(narrow["monthly_usd"]["appsync_transport"], usual["monthly_usd"]["appsync_transport"])

    def test_aws_rate_values_match_captured_price_list(self):
        evidence = RATES["aws_evidence"]
        for usage, key in [("APN2-EventAPI-Operation", "appsync_operation"),
                           ("APN2-EventAPI-ConnectionDuration", "appsync_minute"),
                           ("APN2-ApiGatewayMinute", "gateway_minute"),
                           ("APN2-Request", "lambda_request")]:
            self.assertEqual(RATES[key], float(next(x for x in evidence if x["usage"] == usage)["usd"]))
        for usage, key in [("APN2-ApiGatewayMessage", "gateway_message_tiers"),
                           ("APN2-ApiGatewayHttpRequest", "http_request_tiers"),
                           ("APN2-Lambda-GB-Second", "lambda_gb_second_tiers"),
                           ("APN2-DataTransfer-Out-Bytes", "aws_egress_tiers")]:
            rows = sorted((x for x in evidence if x["usage"] == usage), key=lambda x: float(x["begin"]))
            expected = [[None if x["end"] == "Inf" else float(x["end"]), float(x["usd"])] for x in rows]
            self.assertEqual(RATES[key], expected)

    def test_free_tier_daily_ceiling(self):
        small = calculate(Scenario(concurrent=100, hours_per_day=8))
        large = calculate(Scenario(concurrent=1000, hours_per_day=8))
        self.assertTrue(small["usage"]["cf_free_daily_quotas_fit_assumptions"])
        self.assertFalse(large["usage"]["cf_free_daily_quotas_fit_assumptions"])
        self.assertIsNone(large["monthly_usd"]["cloudflare_free_with_origin_egress_if_quotas_fit"])

    def test_invalid_workload_rejected(self):
        for change in [{"concurrent": 0}, {"hours_per_day": 25}, {"cf_handler_ms": -1},
                       {"board_audience_fraction": 2}, {"connection_lifetime_minutes": 0}]:
            with self.assertRaises(ValueError):
                calculate(replace(Scenario(), **change))


if __name__ == "__main__":
    unittest.main()
