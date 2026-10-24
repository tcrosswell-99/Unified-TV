"""docs/ACCEPTANCE_REGISTER.csv, the evidence register of spec 27.6: exact columns, allowed status values, one row
per requirement, and no hardware claim without the model, firmware and evidence behind it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import csv
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTER = ROOT / "docs" / "ACCEPTANCE_REGISTER.csv"
COLUMNS = ["requirement_id", "feature", "owner_agent", "branch_commit", "build_hash", "device_model", "firmware",
           "test_level", "observed_result", "evidence_path", "status", "severity", "next_action"]
STATUSES = {"not_started", "implemented_unverified", "automated_pass", "hardware_pass", "hardware_fail", "blocked",
            "unsupported", "deferred_preview_only"}
# One vocabulary (spec v3.3, docs/SPEC.md section 5): how far a result has actually been tested.
TEST_LEVELS = {"none", "unit", "simulated", "ci_build", "pc_network", "physical_tcl", "physical_sony", "physical_frame"}


def rows():
    with open(REGISTER, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader)
        return header, [dict(zip(header, r)) for r in reader if r]


class Register(unittest.TestCase):
    def test_header_is_exactly_the_spec_columns(self):
        self.assertEqual(rows()[0], COLUMNS)

    def test_every_row_has_every_column(self):
        with open(REGISTER, newline="", encoding="utf-8") as f:
            for n, r in enumerate(csv.reader(f), 1):
                if r:
                    self.assertEqual(len(r), len(COLUMNS), f"line {n}")

    def test_status_values_are_allowed(self):
        for r in rows()[1]:
            self.assertIn(r["status"], STATUSES, r["requirement_id"])

    def test_test_levels_are_allowed(self):
        for r in rows()[1]:
            self.assertIn(r["test_level"], TEST_LEVELS, r["requirement_id"])

    def test_a_hardware_result_needs_a_physical_test_level(self):
        for r in rows()[1]:
            if r["status"] in ("hardware_pass", "hardware_fail"):
                self.assertTrue(r["test_level"].startswith("physical_"), f"{r['requirement_id']} claims {r['status']} at {r['test_level']}")

    def test_requirement_ids_are_unique(self):
        ids = [r["requirement_id"] for r in rows()[1]]
        self.assertEqual(len(ids), len(set(ids)), [i for i in ids if ids.count(i) > 1])

    def test_preview_0_5_journeys_are_all_listed(self):
        ids = {r["requirement_id"] for r in rows()[1]}
        for n in range(1, 11):
            self.assertIn(f"P05-{n:02d}", ids)

    def test_hardware_results_name_their_device_firmware_and_evidence(self):
        for r in rows()[1]:
            if r["status"] in ("hardware_pass", "hardware_fail"):
                for column in ("device_model", "firmware", "evidence_path", "build_hash"):
                    self.assertTrue(r[column].strip(), f"{r['requirement_id']} claims {r['status']} without {column}")


if __name__ == "__main__":
    unittest.main()
