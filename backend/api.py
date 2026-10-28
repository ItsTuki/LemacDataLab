"""API local del MVP académico. Python 3.10+, únicamente biblioteca estándar."""
from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import re
import secrets
import sqlite3
import time
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

SCOPES = {"NEURODESARROLLO", "DBT", "ANIMO", "ADICCIONES"}
TYPES = {"TEXTO", "EJERCICIO", "HABILIDAD", "ESTRATEGIA", "ACCION", "PARAMETRO"}


class ApiError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


def password_hash(password, salt):
    return hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), 120_000).hex()


def validate_credentials(body):
    alias, password = body.get("alias"), body.get("password")
    if not isinstance(alias, str) or not re.fullmatch(r"[a-zA-Z0-9_]{3,24}", alias):
        raise ApiError(400, "Alias de prueba no válido.")
    if not isinstance(password, str) or not 8 <= len(password) <= 128:
        raise ApiError(400, "La contraseña debe tener entre 8 y 128 caracteres.")
    return alias.lower(), password


def text(item, key, minimum=0, maximum=200):
    value = item.get(key, "")
    if not isinstance(value, str) or not minimum <= len(value.strip()) <= maximum:
        raise ApiError(400, f"Campo {key} no válido.")
    return value.strip()


def integer(item, key, minimum, maximum):
    value = item.get(key)
    if type(value) is not int or not minimum <= value <= maximum:
        raise ApiError(400, f"Campo {key} fuera de rango.")
    return value


def validate_item(kind, item):
    if not isinstance(item, dict):
        raise ApiError(400, "Cada elemento debe ser un objeto.")
    item_id = text(item, "id", 1, 100)
    mutation_id = text(item, "mutationId", 1, 250)
    if type(item.get("eliminado", False)) is not bool:
        raise ApiError(400, "Bandera de eliminación no válida.")
    clean = {"id": item_id, "eliminado": item.get("eliminado", False)}
    if kind == "objetivos":
        clean.update(descripcion=text(item, "descripcion", 3), meta=integer(item, "meta", 1, 100),
                     fecha=integer(item, "fecha", 0, int(time.time() * 1000) + 60_000))
        if item.get("estado") not in {"En curso", "Cumplido", "No cumplido"}:
            raise ApiError(400, "Estado de objetivo no válido.")
        clean["estado"] = item["estado"]
    else:
        if item.get("ambito") not in SCOPES:
            raise ApiError(400, "Ámbito no válido.")
        clean["ambito"] = item["ambito"]
        if kind == "recursos":
            if item.get("tipo") not in TYPES:
                raise ApiError(400, "Tipo de recurso no válido.")
            required = {"HABILIDAD": "DBT", "ESTRATEGIA": "NEURODESARROLLO", "ACCION": "ADICCIONES"}
            if item["tipo"] in required and required[item["tipo"]] != item["ambito"]:
                raise ApiError(400, "El tipo no corresponde al ámbito.")
            clean.update(tipo=item["tipo"], titulo=text(item, "titulo", 3, 100), contenido=text(item, "contenido", 1 if item["tipo"] == "PARAMETRO" else 10, 1000 if item["tipo"] == "PARAMETRO" else 5000))
            if item["tipo"] == "PARAMETRO":
                lines = clean["contenido"].splitlines()
                if item_id != "animo-categorias" or clean["ambito"] != "ANIMO" or not 1 <= len(lines) <= 20 or any(not 1 <= len(s.strip()) <= 40 for s in lines) or len(set(s.strip().lower() for s in lines)) != len(lines):
                    raise ApiError(400, "Revisa las categorías de ánimo: de 1 a 20 opciones únicas, una por línea.")
        else:
            clean.update(fecha=integer(item, "fecha", 0, int(time.time() * 1000) + 60_000),
                         intensidad=integer(item, "intensidad", 1, 5), categoria=text(item, "categoria", 1),
                         contexto=text(item, "contexto", 1), estrategia=text(item, "estrategia"), nota=text(item, "nota", maximum=1000))
            if clean["ambito"] != "ANIMO" and not clean["estrategia"]:
                raise ApiError(400, "Falta la habilidad, estrategia o acción protectora.")
            low, high = (1, 3) if clean["ambito"] == "ADICCIONES" else (1, 5) if clean["ambito"] in {"DBT", "NEURODESARROLLO"} else (0, 5)
            clean["resultado"] = integer(item, "resultado", low, high)
            goal = item.get("objetivoId")
            if goal is not None and (not isinstance(goal, str) or not 1 <= len(goal) <= 100):
                raise ApiError(400, "Identificador de objetivo no válido.")
            if goal and clean["ambito"] != "ADICCIONES":
                raise ApiError(400, "El objetivo solo puede asociarse a Adicciones.")
            clean["objetivoId"] = goal
    return clean, mutation_id


class Store:
    def __init__(self, path):
        self.path = str(path)
        with self.connect() as conn:
            conn.executescript("""
                CREATE TABLE IF NOT EXISTS users(alias TEXT PRIMARY KEY, salt TEXT NOT NULL, hash TEXT NOT NULL, admin INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, owner TEXT NOT NULL, expires REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS items(kind TEXT NOT NULL, id TEXT NOT NULL, owner TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(kind,id));
                CREATE TABLE IF NOT EXISTS mutations(owner TEXT NOT NULL, id TEXT NOT NULL, PRIMARY KEY(owner,id));
            """)
            for alias, admin in [("demo", 0), ("contenidos_demo", 1)]:
                if not conn.execute("SELECT 1 FROM users WHERE alias=?", (alias,)).fetchone():
                    self.create_user(conn, alias, "Demo12345!", admin)
            seeds = Path(__file__).with_name("catalogo.json")
            for resource in json.loads(seeds.read_text(encoding="utf-8")):
                conn.execute("INSERT OR IGNORE INTO items VALUES('recursos', ?, 'catalogo', ?)",
                             (resource["id"], json.dumps(resource, ensure_ascii=False)))

    @contextmanager
    def connect(self):
        conn = sqlite3.connect(self.path, timeout=15)
        conn.row_factory = sqlite3.Row
        try:
            with conn:
                yield conn
        finally:
            conn.close()

    def create_user(self, conn, alias, password, admin=0):
        salt = secrets.token_hex(16)
        conn.execute("INSERT INTO users VALUES(?,?,?,?)", (alias, salt, password_hash(password, salt), admin))

    def auth(self, body, registering=False):
        alias, password = validate_credentials(body)
        with self.connect() as conn:
            user = conn.execute("SELECT * FROM users WHERE alias=?", (alias,)).fetchone()
            if registering:
                if user:
                    raise ApiError(409, "Ese alias ya está registrado en la API.")
                self.create_user(conn, alias, password)
                user = conn.execute("SELECT * FROM users WHERE alias=?", (alias,)).fetchone()
            elif not user:
                raise ApiError(404, "Cuenta de prueba no registrada en la API.")
            if not hmac.compare_digest(user["hash"], password_hash(password, user["salt"])):
                raise ApiError(401, "Alias o contraseña incorrectos.")
            token = secrets.token_urlsafe(32)
            conn.execute("DELETE FROM sessions WHERE expires<?", (time.time(),))
            conn.execute("INSERT INTO sessions VALUES(?,?,?)", (hashlib.sha256(token.encode()).hexdigest(), alias, time.time() + 86400))
            return {"token": token, "alias": alias, "admin": bool(user["admin"])}

    def sync(self, token, body):
        if not token:
            raise ApiError(401, "Se requiere una sesión de prueba.")
        with self.connect() as conn:
            # Una transacción de escritura serializa comprobaciones de propiedad e idempotencia.
            conn.execute("BEGIN IMMEDIATE")
            user = conn.execute("SELECT users.* FROM sessions JOIN users ON sessions.owner=users.alias WHERE token_hash=? AND expires>?",
                                (hashlib.sha256(token.encode()).hexdigest(), time.time())).fetchone()
            if not user:
                raise ApiError(401, "La sesión de la API venció. Vuelve a vincularla.")
            alias = user["alias"]
            for kind in ("objetivos", "registros", "recursos"):
                items = body.get(kind, [])
                if not isinstance(items, list) or len(items) > 500:
                    raise ApiError(400, "Lote no válido: máximo 500 elementos por tipo.")
                if kind == "recursos" and items and not user["admin"]:
                    raise ApiError(403, "Solo el responsable de contenidos puede editar catálogos.")
                for item in items:
                    clean, mutation = validate_item(kind, item)
                    existing = conn.execute("SELECT owner FROM items WHERE kind=? AND id=?", (kind, clean["id"])).fetchone()
                    owner = "catalogo" if kind == "recursos" else alias
                    if existing and existing["owner"] != owner:
                        raise ApiError(403, "Solo puedes modificar tus propios registros y objetivos.")
                    if conn.execute("SELECT 1 FROM mutations WHERE owner=? AND id=?", (alias, mutation)).fetchone():
                        continue
                    if kind == "registros" and clean.get("objetivoId"):
                        goal = conn.execute("SELECT owner FROM items WHERE kind='objetivos' AND id=?", (clean["objetivoId"],)).fetchone()
                        if not goal or goal["owner"] != alias:
                            raise ApiError(403, "El objetivo no pertenece a esta cuenta.")
                    conn.execute("INSERT INTO items VALUES(?,?,?,?) ON CONFLICT(kind,id) DO UPDATE SET payload=excluded.payload",
                                 (kind, clean["id"], owner, json.dumps(clean, ensure_ascii=False)))
                    conn.execute("INSERT INTO mutations VALUES(?,?)", (alias, mutation))
            return {kind: [json.loads(row["payload"]) for row in conn.execute("SELECT payload FROM items WHERE kind=? AND owner=? ORDER BY id",
                       (kind, "catalogo" if kind == "recursos" else alias))] for kind in ("registros", "objetivos", "recursos")}


def create_server(path, host="127.0.0.1", port=8080):
    store = Store(path)

    class Handler(BaseHTTPRequestHandler):
        def reply(self, status, body):
            raw = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(raw)

        def do_GET(self):
            self.reply(200, {"status": "ok", "servicio": "LemacDataLab API de prueba"}) if self.path == "/health" else self.reply(404, {"error": "Ruta no encontrada."})

        def do_POST(self):
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= 4_000_000:
                    raise ApiError(413, "Cuerpo ausente o demasiado grande.")
                body = json.loads(self.rfile.read(size))
                if not isinstance(body, dict):
                    raise ApiError(400, "Se requiere un objeto JSON.")
                if self.path in {"/auth/login", "/auth/register"}:
                    result = store.auth(body, self.path == "/auth/register")
                elif self.path == "/sync":
                    header = self.headers.get("Authorization", "")
                    token = header[7:] if header.startswith("Bearer ") else ""
                    result = store.sync(token, body)
                else:
                    raise ApiError(404, "Ruta no encontrada.")
                self.reply(200, result)
            except ApiError as exc:
                self.reply(exc.status, {"error": exc.message})
            except (ValueError, UnicodeError):
                self.reply(400, {"error": "JSON o longitud no válidos."})
            except sqlite3.IntegrityError:
                self.reply(409, {"error": "El alias o identificador ya existe."})
            except sqlite3.OperationalError:
                self.reply(503, {"error": "La base de prueba está ocupada. Reintenta."})

    return ThreadingHTTPServer((host, port), Handler)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--db", type=Path, default=Path(__file__).with_name("lemac-demo.sqlite3"))
    args = parser.parse_args()
    server = create_server(args.db, args.host, args.port)
    print(f"API de prueba: http://{args.host}:{args.port} | solo datos sintéticos", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
