import json
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path

from api import create_server


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=Path.cwd(), ignore_cleanup_errors=True)
        self.server = create_server(Path(self.temp.name) / 'test.sqlite3', port=0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.temp.cleanup()

    def request(self, path, body, token=None, expected=200):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = f'Bearer {token}'
        req = urllib.request.Request(self.url + path, json.dumps(body).encode(), headers)
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                status, result = response.status, json.load(response)
        except urllib.error.HTTPError as e:
            status, result = e.code, json.load(e)
        self.assertEqual(status, expected, result)
        return result

    def login(self, alias='demo'):
        return self.request('/auth/login', {'alias': alias, 'password': 'Demo12345!'})['token']

    def record(self, id='registro-1', mutation='device:registro-1:1', scope='ANIMO', **overrides):
        return dict(id=id, mutationId=mutation, ambito=scope, fecha=int(time.time()*1000), intensidad=3,
                    categoria='Neutral', contexto='Situación ficticia', estrategia='' if scope == 'ANIMO' else 'Habilidad de prueba',
                    resultado=0 if scope == 'ANIMO' else 1, nota='', objetivoId=None, eliminado=False) | overrides

    def test_registration_and_credentials(self):
        new = self.request('/auth/register', {'alias': 'test_user', 'password': 'Password123', 'admin': True})
        self.assertFalse(new['admin'])
        self.request('/auth/register', {'alias': 'TEST_USER', 'password': 'Password123'}, expected=409)
        self.request('/auth/login', {'alias': 'test_user', 'password': 'BadPassword'}, expected=401)
        self.request('/auth/register', {'alias': 'real@email.com', 'password': 'Password123'}, expected=400)

    def test_requires_authentication(self):
        self.request('/sync', {}, expected=401)
        self.request('/sync', {}, token='invalid', expected=401)

    def test_all_four_modules_and_idempotent_retry(self):
        token = self.login()
        records = [self.record(id=f'r-{scope}', mutation=f'm-{scope}', scope=scope) for scope in ['NEURODESARROLLO', 'DBT', 'ANIMO', 'ADICCIONES']]
        first = self.request('/sync', {'registros': records}, token)
        second = self.request('/sync', {'registros': records}, token)
        self.assertEqual(len(first['registros']), 4)
        self.assertEqual(first, second)

    def test_owner_boundary_and_transaction_rollback(self):
        a, b = self.login(), self.login('contenidos_demo')
        self.request('/sync', {'registros': [self.record()]}, a)
        self.assertEqual(self.request('/sync', {}, b)['registros'], [])
        self.request('/sync', {'registros': [self.record(id='fresh', mutation='new'), self.record(mutation='foreign-change', eliminado=True)]}, b, expected=403)
        self.assertEqual(self.request('/sync', {}, b)['registros'], [])
        self.assertFalse(self.request('/sync', {}, a)['registros'][0]['eliminado'])

    def test_deleted_record_does_not_resurrect_on_retry(self):
        token = self.login()
        original = self.record()
        self.request('/sync', {'registros': [original]}, token)
        self.request('/sync', {'registros': [self.record(mutation='device:registro-1:2', eliminado=True)]}, token)
        result = self.request('/sync', {'registros': [original]}, token)
        self.assertTrue(result['registros'][0]['eliminado'])

    def test_catalog_admin_role(self):
        resource = dict(id='skill-test', mutationId='skill:1', ambito='DBT', tipo='HABILIDAD', titulo='Habilidad ficticia', contenido='Contenido de ejemplo académico', eliminado=False)
        self.request('/sync', {'recursos': [resource]}, self.login(), expected=403)
        result = self.request('/sync', {'recursos': [resource]}, self.login('contenidos_demo'))
        self.assertIn('skill-test', [r['id'] for r in result['recursos']])
        self.assertIn('skill-test', [r['id'] for r in self.request('/sync', {}, self.login())['recursos']])

    def test_goals_and_foreign_goal_rejected(self):
        a, b = self.login(), self.login('contenidos_demo')
        goal = dict(id='goal-1', mutationId='goal:1', descripcion='Objetivo sintético', meta=3, estado='En curso', fecha=int(time.time()*1000), eliminado=False)
        record = self.record(scope='ADICCIONES', objetivoId='goal-1')
        response = self.request('/sync', {'objetivos': [goal], 'registros': [record]}, a)
        self.assertEqual(response['registros'][0]['objetivoId'], 'goal-1')
        self.request('/sync', {'registros': [self.record(id='foreign-goal-record', mutation='foreign-goal', scope='ADICCIONES', objetivoId='goal-1')]}, b, expected=403)

    def test_catalog_parameters(self):
        token = self.login('contenidos_demo')
        parameter = dict(id='animo-categorias', mutationId='param:1', ambito='ANIMO', tipo='PARAMETRO', titulo='Categorías de ánimo', contenido='Bajo\nNeutral\nBueno', eliminado=False)
        result = self.request('/sync', {'recursos': [parameter]}, token)
        self.assertEqual(next(r for r in result['recursos'] if r['id'] == parameter['id'])['contenido'], parameter['contenido'])
        self.request('/sync', {'recursos': [parameter | {'mutationId': 'param:2', 'contenido': 'Bien\nBien'}]}, token, expected=400)

    def test_invalid_fields_do_not_persist(self):
        token = self.login()
        for overrides in [{'intensidad': 0}, {'nota': 'x'*1001}, {'contexto': ''}, {'intensidad': True}, {'ambito': 'OTHER'}]:
            self.request('/sync', {'registros': [self.record(**overrides)]}, token, expected=400)
        self.assertEqual(self.request('/sync', {}, token)['registros'], [])

    def test_persisted_data_survives_new_server_instance(self):
        token = self.login()
        self.request('/sync', {'registros': [self.record()]}, token)
        from api import Store
        second_store = Store(Path(self.temp.name) / 'test.sqlite3')
        self.assertEqual(len(second_store.sync(token, {})['registros']), 1)


if __name__ == '__main__':
    unittest.main(verbosity=2)
