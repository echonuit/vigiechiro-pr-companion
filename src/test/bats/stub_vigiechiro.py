#!/usr/bin/env python3
"""Serveur stub de l'API VigieChiro pour les E2E CLI réseau (#1592).

Sert une **collection Eve vide** (`{"_items": [], "_meta": {...}}`, 200) pour toute requête et
**journalise** chaque requête reçue (méthode + chemin) dans un fichier. Il ne cherche pas à imiter
fidèlement le backend : il prouve que le client, pointé sur lui via `VIGIECHIRO_URL`, lui envoie bien
ses requêtes (au lieu de l'API de production) et sait exploiter une réponse Eve bien formée.

Séparé du JVM (processus Python), il **contourne** le blocage JPMS de `com.sun.net.httpserver` en test
in-process. Il se lie à un port éphémère (0) et écrit le port choisi dans `portfile` une fois prêt.

Usage : stub_vigiechiro.py <portfile> <journal>
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORTFILE = sys.argv[1]
JOURNAL = sys.argv[2]
CORPS = json.dumps({"_items": [], "_meta": {"max_results": 100, "total": 0, "page": 1}}).encode()


class Stub(BaseHTTPRequestHandler):
    def _servir(self):
        with open(JOURNAL, "a", encoding="utf-8") as f:
            f.write(f"{self.command} {self.path}\n")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(CORPS)))
        self.end_headers()
        self.wfile.write(CORPS)

    do_GET = _servir
    do_POST = _servir
    do_PATCH = _servir
    do_PUT = _servir

    def log_message(self, *args):  # silence : le journal des requêtes suffit
        pass


serveur = HTTPServer(("127.0.0.1", 0), Stub)
with open(PORTFILE, "w", encoding="utf-8") as f:
    f.write(str(serveur.server_address[1]))
serveur.serve_forever()
