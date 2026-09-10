import unittest
from unittest.mock import patch
import middleware
class MiddlewareTest(unittest.TestCase):
    def test_missing_and_drift_are_not_healthy(self):
        def read(path):
            if path=='':return [{'name':middleware.CLUSTER,'status':'ONLINE','brokerCount':1}]
            return {'pageCount':1,'topics':[{'name':'integration.commands','partitionCount':3,'replicationFactor':1,'messagesCount':0,'underReplicatedPartitions':0}]}
        with patch.object(middleware,'read',side_effect=read): result=middleware.snapshot()
        by_name={x['name']:x for x in result['topics']}
        self.assertEqual(by_name['integration.commands']['status'],'drift')
        self.assertEqual(by_name['events.state.requested']['status'],'missing')
        self.assertIsNone(by_name['events.state.requested']['messages'])
    def test_under_replicated_is_error(self):
        with patch.object(middleware,'read',side_effect=[[{'name':middleware.CLUSTER,'status':'ONLINE'}],{'topics':[{'name':'events.raw','partitionCount':3,'underReplicatedPartitions':1}]}]):
            self.assertEqual(middleware.snapshot()['topics'][0]['status'],'error')
    def test_missing_cluster_fails(self):
        with patch.object(middleware,'read',return_value=[]):
            with self.assertRaises(ValueError):middleware.snapshot()
