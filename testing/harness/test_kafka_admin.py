"""Isolated Kafka installation boundaries; no Docker daemon or broker mutations."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("kafka_admin", ROOT / "scripts/kafka-admin.py")
admin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(admin)


class KafkaAdminTest(unittest.TestCase):
    def test_portable_bundle_and_cluster_identity_not_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "candidate"
            admin.prepare(target, "em-kafka-test", 19092, 18085)
            original = (target / "runtime.env").read_text()
            self.assertIn("@sha256:", original)
            self.assertIn(str(target / "runtime.env"), list(map(str, admin.bundle_config(target))))
            with self.assertRaises(FileExistsError):
                admin.prepare(target, "em-kafka-test", 19092, 18085)
            self.assertEqual(original, (target / "runtime.env").read_text())
            (target / "runtime.env").write_text(original.replace("19092", "29093"))
            with self.assertRaisesRegex(ValueError, "paquete cambió"):
                admin.bundle_config(target)

    def test_active_ports_and_nonisolated_projects_rejected(self):
        for project, broker, ui in (("infrastructure", 19092, 18085),
                                    ("em-kafka-test", 9092, 18085),
                                    ("em-kafka-test", 19092, 8085),
                                    ("em-kafka-test", 19092, 19092)):
            with self.subTest(project=project, broker=broker, ui=ui):
                with tempfile.TemporaryDirectory() as temp:
                    target = Path(temp) / "candidate"
                    with self.assertRaises(ValueError):
                        admin.prepare(target, project, broker, ui)
                    self.assertFalse(target.exists())

    def test_drift_detects_missing_partitions_replication_retention_cleanup(self):
        expected = [{"name": "events.raw", "partitions": "3", "replication": "1",
                     "cleanup": "delete", "retention": "604800000"}]
        good = "Topic: events.raw TopicId: xxx PartitionCount: 3 ReplicationFactor: 1 Configs: retention.ms=604800000,cleanup.policy=delete"
        self.assertEqual([], admin.compare_topics(expected, good))
        self.assertEqual(1, len(admin.compare_topics(expected, "")))
        for old, new in (("PartitionCount: 3", "PartitionCount: 1"),
                         ("ReplicationFactor: 1", "ReplicationFactor: 2"),
                         ("604800000", "1000"), ("cleanup.policy=delete", "cleanup.policy=compact")):
            self.assertEqual(1, len(admin.compare_topics(expected, good.replace(old, new))))

    def test_inventory_does_not_need_kafka_or_docker(self):
        topics = admin.inventory(ROOT / "infrastructure/kafka/create-topics.sh")
        self.assertEqual(9, len(topics))
        self.assertEqual(9, len({t["name"] for t in topics}))

    def test_install_without_bundle_cannot_reach_docker(self):
        with patch("sys.argv", ["kafka-admin.py", "install"]), patch.object(admin, "run") as run:
            with self.assertRaises(SystemExit):
                admin.main()
            run.assert_not_called()

    def test_shell_cannot_override_bundle_settings(self):
        with patch.dict(os.environ, {"KAFKA_IMAGE": "wrong", "COMPOSE_PROJECT_NAME": "infrastructure"}):
            self.assertNotIn("KAFKA_IMAGE", admin.docker_environment())
            self.assertNotIn("COMPOSE_PROJECT_NAME", admin.docker_environment())

    def test_rejects_surviving_volume_from_another_cluster_before_start(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "candidate"
            admin.prepare(target, "em-kafka-test", 19092, 18085)
            with patch.object(admin, "run", side_effect=["", "", "em-kafka-test_kafka-data\n", "other-cluster\n"]) as calls:
                with self.assertRaisesRegex(ValueError, "otra identidad"):
                    admin.install(admin.bundle_config(target), target)
                self.assertFalse(any("up" in c.args[0] for c in calls.call_args_list))

    def test_rejects_project_owned_by_another_directory_before_start(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "candidate"
            admin.prepare(target, "em-kafka-test", 19092, 18085)
            with patch.object(admin, "run", side_effect=["", "existing-id\n", "/somewhere/else\n"]) as calls:
                with self.assertRaisesRegex(ValueError, "otro directorio"):
                    admin.install(admin.bundle_config(target), target)
                self.assertFalse(any("up" in c.args[0] for c in calls.call_args_list))

    def test_recipe_has_no_global_resources_and_valid_compose(self):
        recipe = json.loads((ROOT / "infrastructure/kafka/compose.json").read_text())
        self.assertEqual({"kafka", "kafka-init", "kafka-ui"}, set(recipe["services"]))
        for service in recipe["services"].values():
            self.assertNotIn("container_name", service)
        self.assertNotIn("name", recipe["volumes"]["kafka-data"])
        self.assertNotIn("name", recipe["networks"]["event-management-net"])
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "candidate"
            admin.prepare(target, "em-kafka-test", 19092, 18085)
            subprocess.run([str(x) for x in admin.bundle_config(target)] + ["config", "--quiet"],
                           check=True, env=admin.docker_environment(), capture_output=True)

    def test_broker_recipe_preserves_current_configuration(self):
        current = json.loads(admin.run(admin.current_config() + ["config", "--format", "json"]))
        recipe = json.loads((ROOT / "infrastructure/kafka/compose.json").read_text())
        excluded = {"CLUSTER_ID", "KAFKA_ADVERTISED_LISTENERS"}
        for key in ("kafka", "kafka-ui"):
            omissions = excluded if key == "kafka" else {"DYNAMIC_CONFIG_ENABLED", "KAFKA_CLUSTERS_0_NAME"}
            env = lambda config: {k: v for k, v in config["services"][key]["environment"].items() if k not in omissions}
            self.assertEqual(env(current), env(recipe))
            if key == "kafka-ui":
                self.assertEqual(current["services"][key]["healthcheck"], recipe["services"][key]["healthcheck"])
        self.assertIn("--bootstrap-server kafka:29092", recipe["services"]["kafka"]["healthcheck"]["test"][1])


if __name__ == "__main__":
    unittest.main()
