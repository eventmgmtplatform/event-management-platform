"""Local GLPI REST V1 contract fixture. Separate SQLite storage from ServiceNow."""
import json
import os
import secrets
import sqlite3
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs

DB = os.getenv("GLPI_MOCK_DB", "/data/glpi.sqlite")
SESSIONS = set()
def connect():
    c = sqlite3.connect(DB, timeout=10)
    c.execute("CREATE TABLE IF NOT EXISTS tickets(id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL)")
    c.execute("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, ticket_id INTEGER, body TEXT)")
    return c

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def reply(self, status, value):
        data=json.dumps(value).encode(); self.send_response(status)
        self.send_header("Content-Type", "application/json"); self.send_header("Content-Length",str(len(data)))
        self.end_headers();self.wfile.write(data)
    def do_GET(self): self.handle_api("GET")
    def do_POST(self): self.handle_api("POST")
    def do_PUT(self): self.handle_api("PUT")
    def handle_api(self, method):
        url=urlsplit(self.path); path=url.path.removeprefix("/apirest.php"); query=parse_qs(url.query)
        if url.path=="/health": return self.reply(200,{"status":"UP","component":"glpi-mock"})
        if self.headers.get("App-Token")!=os.getenv("GLPI_APP_TOKEN","local-app"): return self.reply(401,["ERROR_WRONG_APP_TOKEN"])
        if path=="/initSession" and method=="GET":
            if self.headers.get("Authorization")!="user_token "+os.getenv("GLPI_USER_TOKEN","local-user"):return self.reply(401,["ERROR_GLPI_LOGIN"])
            token=secrets.token_hex(24);SESSIONS.add(token);return self.reply(200,{"session_token":token})
        token=self.headers.get("Session-Token")
        if token not in SESSIONS:return self.reply(401,["ERROR_SESSION_TOKEN_INVALID"])
        if path=="/killSession":SESSIONS.discard(token);return self.reply(200,True)
        try:
            body={}
            if method!="GET":
                length=int(self.headers.get("Content-Length","0"))
                if not 0<length<=65536:raise ValueError()
                body=json.loads(self.rfile.read(length))["input"]
            with connect() as c:
                if path=="/Ticket" and method=="POST":
                    if not str(body.get("name","")).strip():raise ValueError()
                    ticket={"status":1,"type":1,"urgency":3,"impact":3,"priority":3,"entities_id":0,"itilcategories_id":0,"content":"",**body,"date_mod":datetime.now(timezone.utc).isoformat()}
                    cur=c.execute("INSERT INTO tickets(body) VALUES (?)",(json.dumps(ticket),));id=cur.lastrowid
                    c.commit();return self.reply(201,{"id":id,"message":"Item successfully added"})
                if path in ("/Ticket","/search/Ticket") and method=="GET":
                    records=[{**json.loads(r[1]),"id":r[0]} for r in c.execute("SELECT id,body FROM tickets ORDER BY id")]
                    if path=="/search/Ticket":
                        marker=query.get("criteria[0][value]",[""])[0]
                        found=[r for r in records if marker in r["name"]]
                        return self.reply(200,{"totalcount":len(found),"data":[{"2":r["id"]} for r in found]})
                    start,end=map(int,query.get("range",["0-99"])[0].split("-"))
                    return self.reply(200,records[start:end+1])
                if path=="/ITILFollowup" and method=="GET":
                    notes=[]
                    for row in c.execute("SELECT id,ticket_id,body FROM notes WHERE kind='/ITILFollowup' ORDER BY id"):
                        item=json.loads(row[2]); item["id"]=row[0]; item["items_id"]=row[1]; notes.append(item)
                    return self.reply(200,notes)
                if path.startswith("/Ticket/"):
                    id=int(path.rsplit("/",1)[1]);row=c.execute("SELECT body FROM tickets WHERE id=?",(id,)).fetchone()
                    if not row:return self.reply(404,["ERROR_ITEM_NOT_FOUND"])
                    ticket=json.loads(row[0]);ticket["id"]=id
                    if method=="GET":return self.reply(200,ticket)
                    if method=="PUT":
                        ticket.update(body);ticket["date_mod"]=datetime.now(timezone.utc).isoformat()
                        c.execute("UPDATE tickets SET body=? WHERE id=?",(json.dumps(ticket),id));c.commit();return self.reply(200,[{str(id):True}])
                if path in ("/ITILSolution","/ITILFollowup") and method=="POST":
                    id=int(body["items_id"]);row=c.execute("SELECT body FROM tickets WHERE id=?",(id,)).fetchone()
                    if not row:return self.reply(404,["ERROR_ITEM_NOT_FOUND"])
                    cur=c.execute("INSERT INTO notes(kind,ticket_id,body) VALUES (?,?,?)",(path,id,json.dumps(body)))
                    if path=="/ITILSolution":
                        ticket=json.loads(row[0]);ticket["status"]=5;ticket["date_mod"]=datetime.now(timezone.utc).isoformat()
                        c.execute("UPDATE tickets SET body=? WHERE id=?",(json.dumps(ticket),id))
                    c.commit();return self.reply(201,{"id":cur.lastrowid})
                return self.reply(404,["ERROR_ITEMTYPE_NOT_FOUND"])
        except (ValueError,KeyError,TypeError):return self.reply(400,["ERROR_BAD_ARRAY"])

if __name__=="__main__":
    Path(DB).parent.mkdir(parents=True,exist_ok=True)
    with connect():pass
    ThreadingHTTPServer(("0.0.0.0",int(os.getenv("GLPI_MOCK_PORT","8080"))),Handler).serve_forever()
