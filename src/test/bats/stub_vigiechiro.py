#!/usr/bin/env python3
"""Serveur stub de l'API VigieChiro pour les E2E CLI réseau (#1592).

**Journalise** chaque requête reçue (méthode + chemin) dans un fichier, et répond selon le chemin :

- `/sites` : un **catalogue paginé** synthétique (#2999). Le nombre total de sites se passe en 3e
  argument ; le stub découpe en pages de 100 comme le vrai backend et annonce `_meta.total`. Les
  titres et localités suivent la forme relevée sur la collection réelle, ce qui permet d'exercer la
  **pagination bornée** et le **recensement des points** depuis le vrai fat-jar ;
- tout le reste : une **collection Eve vide** (`{"_items": [], "_meta": {...}}`, 200).

Il ne cherche pas à imiter fidèlement le backend : il prouve que le client, pointé sur lui via
`VIGIECHIRO_URL`, lui envoie bien ses requêtes (au lieu de l'API de production) et sait exploiter une
réponse Eve bien formée.

Séparé du JVM (processus Python), il **contourne** le blocage JPMS de `com.sun.net.httpserver` en test
in-process. Il se lie à un port éphémère (0) et écrit le port choisi dans `portfile` une fois prêt.

Usage : stub_vigiechiro.py <portfile> <journal> [total-sites]
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

PORTFILE = sys.argv[1]
JOURNAL = sys.argv[2]
TOTAL_SITES = int(sys.argv[3]) if len(sys.argv) > 3 else 0
TAILLE_PAGE = 100
VIDE = json.dumps({"_items": [], "_meta": {"max_results": 100, "total": 0, "page": 1}}).encode()


def site(rang):
    """Un site du catalogue, à la forme relevée sur la collection réelle.

    Deux sites sur trois portent le point « Z1 » : c'est ce que le recensement doit retrouver, et la
    proportion rend l'assertion insensible au découpage en pages.
    """
    carre = 130000 + rang
    points = ["Z1"] if rang % 3 else ["Z2", "Z3"]
    return {
        "_id": f"site-{rang}",
        "titre": f"Vigiechiro - Point Fixe-{carre}",
        "verrouille": rang % 2 == 0,
        "observateur": "obs-1",
        "localites": [
            {"nom": code, "geometries": {"geometries": [{"coordinates": [43.5, 5.4]}]}} for code in points
        ],
    }


def recherche_par_q(motif):
    """Répond à `q=<motif>` comme le vrai backend (#3769), mesuré le 2026-08-14.

    Le filtre est appliqué **côté serveur** et le total annoncé est celui de la recherche, pas celui du
    catalogue : c'est ce qui distingue `q` de `where=`, que ce backend accepte puis ignore. Le mot est
    entier, jamais un préfixe - `13071` ne ramène pas `130711`.
    """
    trouves = [
        site(rang) for rang in range(TOTAL_SITES) if motif in site(rang)["titre"].split("-")[-1:]
    ]
    return json.dumps(
        {"_items": trouves, "_meta": {"max_results": TAILLE_PAGE, "total": len(trouves), "page": 1}}
    ).encode()


def page_de_sites(requete):
    """Découpe le catalogue en pages de 100, comme le backend. Hors bornes : page vide (fin de collection)."""
    parametres = parse_qs(urlparse(requete).query)
    if "q" in parametres:
        return recherche_par_q(parametres["q"][0])
    page = int(parametres.get("page", ["1"])[0])
    debut = (page - 1) * TAILLE_PAGE
    items = [site(rang) for rang in range(debut, min(debut + TAILLE_PAGE, TOTAL_SITES))]
    return json.dumps(
        {"_items": items, "_meta": {"max_results": TAILLE_PAGE, "total": TOTAL_SITES, "page": page}}
    ).encode()


class Stub(BaseHTTPRequestHandler):
    def _servir(self):
        with open(JOURNAL, "a", encoding="utf-8") as f:
            f.write(f"{self.command} {self.path}\n")
        corps = page_de_sites(self.path) if urlparse(self.path).path.endswith("/sites") else VIDE
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

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
