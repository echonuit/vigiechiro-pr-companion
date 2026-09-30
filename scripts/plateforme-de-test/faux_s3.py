#!/usr/bin/env python3
"""Un faux stockage d objets pour la plateforme de test : il garde les octets et dit ce qu il a recu (#5660).

    python3 scripts/plateforme-de-test/faux_s3.py --port 8443 --certificat C --cle K
    python3 scripts/plateforme-de-test/faux_s3.py --auto-test

## Pourquoi pas celui de l API amont

L API Vigie-Chiro fournit `tests/tadarida/fake_s3.py`, et le banc d etalonnage le lancait tel quel.
Il ne peut pas servir la plateforme de test, pour quatre raisons lues dans son code :

- il parle HTTP seul, et `UrlSigneeAdmise` refuse toute URL de depot qui n est pas en `https` ;
- il ne rend jamais d `ETag`, et repond `200` meme quand l ecriture echoue ;
- il lit le corps ligne a ligne et jette la premiere et la derniere : un binaire en sort corrompu ;
- il lit `content-type` sans verifier sa presence, et plante sur un PUT qui n en porte pas. Or
  Companion envoie EXPRES chaque partie d un multipart sans ce type (#5597) : tout depot en parties
  echouait.

## Ce qu il fait, et ce qu il ne fait pas

Un PUT ecrit les octets tels quels, rend `ETag: "<md5>"` comme S3, et n exige aucun `Content-Type`.
Un GET les relit. `GET /_journal` rend la liste des PUT recus, chemin, taille et type, pour qu un test
affirme ce que Companion a envoye, pas seulement ce qu il a obtenu.

Il ne verifie AUCUNE signature : en mode `DEV_FAKE_S3_URL`, l API n en calcule pas, et toutes les
parties d un multipart arrivent sur la meme URL nue. Ce que ce mode ne prouve pas est ecrit en #5658.

Un corps sans `Content-Length` est refuse en `411` plutot que lu jusqu a la fermeture : un client qui
enverrait en morceaux ne ressemblerait pas a ce que S3 accepte d un PUT signe, et le dire vaut mieux
que de l avaler.

Il ne depend que de la bibliotheque standard, parce qu il tourne seul dans son conteneur. Le module
commun des gardes n est importe que par l auto-test.
"""

from __future__ import annotations

import argparse
import hashlib
import http.client
import http.server
import json
import pathlib
import ssl
import sys
import threading

JOURNAL = "/_journal"


class Depot:
    """Les objets recus, et le journal des PUT, partages entre les fils du serveur."""

    def __init__(self) -> None:
        self.objets: dict[str, bytes] = {}
        self.journal: list[dict] = []
        self.verrou = threading.Lock()

    def ecrit(self, chemin: str, corps: bytes, type_de_contenu: str | None) -> str:
        etag = hashlib.md5(corps).hexdigest()
        with self.verrou:
            self.objets[chemin] = corps
            self.journal.append(
                {"chemin": chemin, "taille": len(corps), "type": type_de_contenu, "etag": etag}
            )
        return etag


def gestionnaire(depot: Depot) -> type[http.server.BaseHTTPRequestHandler]:
    class Gestionnaire(http.server.BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def _repond(self, code: int, corps: bytes = b"", entetes: dict | None = None) -> None:
            self.send_response(code)
            for nom, valeur in (entetes or {}).items():
                self.send_header(nom, valeur)
            self.send_header("Content-Length", str(len(corps)))
            self.end_headers()
            if corps and self.command != "HEAD":
                self.wfile.write(corps)

        def _draine_les_morceaux(self) -> None:
            while True:
                taille = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if taille == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    return
                self.rfile.read(taille + 2)

        def do_PUT(self) -> None:
            longueur = self.headers.get("Content-Length")
            if longueur is None:
                # Le corps en morceaux se LIT avant le refus, puis la connexion se ferme. Refuser sans
                # le lire laissait ses morceaux dans le flux : le serveur les prenait pour la requete
                # suivante, fermait, et le client qui ecrivait encore recevait un tuyau casse. Vert sur
                # un poste, rouge sur le runner de CI (#5674).
                if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
                    self._draine_les_morceaux()
                self.close_connection = True
                self._repond(411, b"Content-Length requis\n", {"Connection": "close"})
                return
            corps = self.rfile.read(int(longueur))
            etag = depot.ecrit(self.path, corps, self.headers.get("Content-Type"))
            self._repond(200, entetes={"ETag": f'"{etag}"'})

        def do_GET(self) -> None:
            if self.path == JOURNAL:
                with depot.verrou:
                    corps = json.dumps(depot.journal).encode("utf-8")
                self._repond(200, corps, {"Content-Type": "application/json"})
                return
            with depot.verrou:
                objet = depot.objets.get(self.path)
            if objet is None:
                self._repond(404, b"objet inconnu\n")
                return
            etag = hashlib.md5(objet).hexdigest()
            self._repond(
                200, objet, {"ETag": f'"{etag}"', "Content-Type": "application/octet-stream"}
            )

        def log_message(self, format: str, *args) -> None:
            sys.stderr.write("faux-s3 " + (format % args) + "\n")

    return Gestionnaire


def serveur(
    port: int, certificat: str | None = None, cle: str | None = None
) -> tuple[http.server.ThreadingHTTPServer, Depot]:
    """Un serveur pret a servir, en TLS si on lui donne un certificat et sa cle."""
    depot = Depot()
    rendu = http.server.ThreadingHTTPServer(("0.0.0.0", port), gestionnaire(depot))
    if certificat:
        contexte = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        contexte.load_cert_chain(certificat, cle)
        rendu.socket = contexte.wrap_socket(rendu.socket, server_side=True)
    return rendu, depot


def auto_test() -> int:
    """Les proprietes que le faux S3 promet, eprouvees par de vraies requetes HTTP en processus.

    La couche TLS n est pas eprouvee ici : elle demanderait `openssl` sur le poste qui joue ce test.
    Elle l est dans l image, et par l extension JUnit de #5663, ou `UrlSigneeAdmise` refuse le `http`.
    """
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
    from _commun import cas_d_auto_test

    verifie, echecs = cas_d_auto_test()
    rendu, _ = serveur(0)
    fil = threading.Thread(target=rendu.serve_forever, daemon=True)
    fil.start()
    port = rendu.server_address[1]

    def requete(
        methode: str, chemin: str, corps: bytes | None = None, entetes=None, morceaux=False
    ):
        connexion = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
        connexion.request(
            methode, chemin, body=corps, headers=entetes or {}, encode_chunked=morceaux
        )
        reponse = connexion.getresponse()
        rendu_ = (reponse.status, dict(reponse.getheaders()), reponse.read())
        connexion.close()
        return rendu_

    # Un binaire qui porte des retours a la ligne en tete et en queue : c est exactement ce que le
    # faux S3 amont amputait.
    binaire = b"\n" + bytes(range(256)) * 64 + b"\r\n"
    md5 = hashlib.md5(binaire).hexdigest()
    try:
        statut, entetes, _ = requete("PUT", "/zip/pa-1/nuit.zip", binaire)
        verifie("un PUT reussit", lambda: statut, 200)
        verifie(
            "l ETag rendu est le MD5 du corps, entre guillemets",
            lambda: entetes.get("ETag"),
            f'"{md5}"',
        )
        verifie(
            "un GET relit les octets a l identique",
            lambda: hashlib.sha256(requete("GET", "/zip/pa-1/nuit.zip")[2]).hexdigest(),
            hashlib.sha256(binaire).hexdigest(),
        )
        verifie("un objet inconnu rend 404", lambda: requete("GET", "/absent")[0], 404)

        verifie(
            "une partie SANS Content-Type est acceptee",
            lambda: requete("PUT", "/", b"partie-1")[0],
            200,
        )
        requete("PUT", "/zip/pa-1/type.zip", b"x", {"Content-Type": "application/zip"})
        journal = json.loads(requete("GET", JOURNAL)[2])
        verifie(
            "le journal garde chaque PUT dans l ordre, avec son type ou son absence",
            lambda: [(e["chemin"], e["taille"], e["type"]) for e in journal],
            [
                ("/zip/pa-1/nuit.zip", len(binaire), None),
                ("/", 8, None),
                ("/zip/pa-1/type.zip", 1, "application/zip"),
            ],
        )
        verifie(
            "un corps envoye en morceaux, sans longueur, est refuse en 411",
            lambda: requete("PUT", "/morceaux", iter([b"a", b"b"]), morceaux=True)[0],
            411,
        )
    finally:
        rendu.shutdown()
        rendu.server_close()
    return echecs()


def main(argv: list[str]) -> int:
    if "--auto-test" in argv[1:]:
        return auto_test()
    analyse = argparse.ArgumentParser(prog="faux_s3.py", description=__doc__.splitlines()[0])
    analyse.add_argument("--port", type=int, default=8443)
    analyse.add_argument("--certificat")
    analyse.add_argument("--cle")
    options = analyse.parse_args(argv[1:])
    if bool(options.certificat) != bool(options.cle):
        analyse.error("--certificat et --cle vont ensemble")
    rendu, _ = serveur(options.port, options.certificat, options.cle)
    rendu.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
