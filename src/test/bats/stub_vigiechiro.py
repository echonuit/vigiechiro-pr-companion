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
import os
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


# Statut de refus a servir sur les routes de DEPOT, ou 0 pour ne rien refuser.
#
# Le depot est le seul endroit du produit ou un refus est DEFINITIF - l archive ne repartira pas telle
# quelle - et c est ce comportement que la recette doit pouvoir jouer a la main (#3983). Sans ce
# levier, une case « le bouton cesse de proposer la reprise » ne serait pas rejouable, donc pas
# terminee au sens de #3510.
#
# 403 : droits refuses, reparable par une reconnexion. 422 : contenu refuse, que rien ne repare.
REFUS_DEPOT = int(os.environ.get("VIGIECHIRO_STUB_REFUS", "0"))

# #4867 : le DETAIL d une participation, servi seulement quand on le demande. Sans ces variables, le
# bouchon rend la collection vide comme avant, et les six cas d origine ne bougent pas d un pouce.
#
# Pourquoi un detail et non un refus HTTP : la garde de concurrence de `metadonnees-passage` est COTE
# CLIENT. Elle relit la participation juste avant d ecrire et renonce si la configuration a bouge
# depuis sa reference (#4552, deplacee par #4707). Il n y a donc aucun 412 a servir, et il ne FAUDRAIT
# pas : #4523 a mesure que `PATCH /participations/{id}` rend 200 avec ou sans `If-Match`, meme faux.
# Enseigner ici une garde que la plateforme n a pas rendrait ce bouchon menteur.
PARTICIPATION = os.environ.get("VIGIECHIRO_STUB_PARTICIPATION", "")

# La configuration servie, et celle servie a partir du SECOND appel. Deux valeurs differentes font
# renoncer le client ; une seule le laisse ecrire. C est la succession qui porte le cas, pas un statut.
CONFIG = os.environ.get("VIGIECHIRO_STUB_CONFIG", "{}")
CONFIG_RELECTURE = os.environ.get("VIGIECHIRO_STUB_CONFIG_RELECTURE", "")

# Le statut a servir sur un PATCH de participation, pour eprouver le refus d ECRITURE. Distinct de
# `VIGIECHIRO_STUB_REFUS`, qui ne vise que les routes de depot d archive : les melanger ferait refuser
# un depot quand on voulait refuser une metadonnee.
REFUS_PATCH = int(os.environ.get("VIGIECHIRO_STUB_REFUS_PATCH", "0"))

# Combien de fois la participation a deja ete lue. Le client lit DEUX fois par envoi, et c est cette
# seconde lecture que la garde compare : la relecture est le coeur du mecanisme.
lectures = [0]


def detail_de_participation():
    """Le corps d une participation lisible, dont la configuration change a la relecture.

    `_id` est ce que `ParticipationsVigieChiro.detail` exige pour ne pas rendre un optionnel vide.
    `_etag` est servi parce que le client le renvoie dans son PATCH, non parce qu il le compare.
    """
    lectures[0] += 1
    brute = CONFIG_RELECTURE if (lectures[0] > 1 and CONFIG_RELECTURE) else CONFIG
    return json.dumps({
        "_id": PARTICIPATION,
        "_etag": "etag-de-test",
        "point": "A1",
        "date_debut": "2026-04-22T20:25:00Z",
        "date_fin": "2026-04-23T07:47:00Z",
        "configuration": json.loads(brute),
    }).encode()

# Les routes par lesquelles une archive part. Refuser ailleurs empecherait d ARRIVER au depot.
ROUTES_DE_DEPOT = ("/fichiers", "/multipart")


def refus_attendu(chemin):
    """Le statut a servir sur ce chemin, ou 0 pour servir normalement."""
    if REFUS_DEPOT == 0:
        return 0
    return REFUS_DEPOT if any(chemin.endswith(route) for route in ROUTES_DE_DEPOT) else 0


class Stub(BaseHTTPRequestHandler):
    def _servir(self):
        with open(JOURNAL, "a", encoding="utf-8") as f:
            f.write(f"{self.command} {self.path}\n")
        statut = refus_attendu(urlparse(self.path).path)
        if statut:
            corps = json.dumps({"_status": "ERR", "_error": {"code": statut}}).encode()
            self.send_response(statut)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(corps)))
            self.end_headers()
            self.wfile.write(corps)
            return
        chemin = urlparse(self.path).path
        if REFUS_PATCH and self.command == "PATCH" and "/participations/" in chemin:
            corps = json.dumps({"_status": "ERR", "_error": {"code": REFUS_PATCH}}).encode()
            self.send_response(REFUS_PATCH)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(corps)))
            self.end_headers()
            self.wfile.write(corps)
            return
        if PARTICIPATION and self.command == "GET" and chemin.endswith(PARTICIPATION):
            corps = detail_de_participation()
        elif chemin.endswith("/sites"):
            corps = page_de_sites(self.path)
        else:
            corps = VIDE
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
