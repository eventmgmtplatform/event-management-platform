import unittest
from server import classify, service_record, CATALOG

class InventoryTest(unittest.TestCase):
    def test_health_mapping(self):
        cases = [({'Status':'running','Health':{'Status':'healthy'}},False,'healthy'),
                 ({'Status':'running','Health':{'Status':'unhealthy'}},False,'error'),
                 ({'Status':'running'},False,'unknown'),
                 ({'Status':'exited','ExitCode':143},False,'stopped'),
                 ({'Status':'exited','ExitCode':1},False,'error'),
                 ({'Status':'exited','ExitCode':0},True,'completed'),
                 ({'Status':'exited','ExitCode':0},False,'stopped'),
                 ({'Status':'restarting'},False,'error'),
                 ({'Status':'running','Health':{'Status':'starting'}},False,'degraded')]
        for state, job, expected in cases:
            with self.subTest(state=state, job=job):
                self.assertEqual(classify(state, job)[0], expected)
    def test_missing_and_daemon_failure(self):
        self.assertEqual(service_record(CATALOG[0], lambda _:None)['reason'],'missing')
        def unavailable(_): raise OSError('private socket details')
        record=service_record(CATALOG[0], unavailable)
        self.assertEqual(record['status'],'unknown')
        self.assertNotIn('private',str(record))
    def test_projection_excludes_secrets(self):
        record=service_record(CATALOG[0], lambda _: {'State':{'Status':'running','Health':{'Status':'healthy','Log':['secret'] }},'Config':{'Env':['PASSWORD=secret'],'Image':'vendor/kafka:3.9'},'NetworkSettings':{'Ports':{'9092/tcp':[{'HostPort':'9092'}]} }})
        self.assertEqual(record['version'],'3.9')
        self.assertEqual(record['port'],9092)
        self.assertNotIn('secret',str(record))
    def test_inherited_os_version_is_not_service_version(self):
        record=service_record(CATALOG[0], lambda _: {'State':{'Status':'running'}, 'Config':{'Image':'app:1.0.0','Labels':{'org.opencontainers.image.version':'26.04'}}})
        self.assertEqual(record['version'],'1.0.0')
if __name__=='__main__': unittest.main()
