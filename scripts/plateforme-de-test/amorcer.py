#!/usr/bin/env python3
"""Materialise l etat de depart DECLARE de la plateforme de test dans son Mongo (#5662, ADR 4406).

    python amorcer.py --declaration etat-de-depart.json --mongo mongodb://mongo:27017/vigiechiro
    python3 scripts/plateforme-de-test/amorcer.py --auto-test

Il tourne DANS le conteneur de l API : `pymongo` et `bson` y sont deja, et `bin/init_db.py` y pose
les index. Le depot n y gagne aucune dependance.

## Deux moities, et une seule touche Mongo

`documents()` est PURE : elle rend, pour chaque collection, les documents que la declaration decrit,
avec des identifiants DERIVES des cles et des horodatages DERIVES de la date de reference. Deux appels
rendent donc les memes documents, et c est ce que l auto-test verifie sans Mongo.

`materialise()` les ecrit, frappe les jetons et les rend. Les jetons sont la seule part aleatoire, et
ils ne sont ecrits nulle part hors de Mongo : `verifie_jeton.py` refuse un jeton versionne.

## Ce que la declaration ne repete pas

Le protocole d une participation se lit sur son site, et le proprietaire d une donnee sur sa
participation : l API exige les deux, et les ecrire deux fois ferait deux verites. Une inscription
validee devient l entree `protocoles` de l utilisateur, la seule chose que l API regarde avant
d accepter une participation (mesure sur #5662 : le verrou du site, lui, n est pas exige).

Un objet se cite par sa `cle`, jamais par `nom`, qui est un vrai champ de l API.
"""

from __future__ import annotations

import argparse
import csv
import datetime
import hashlib
import io
import json
import math
import pathlib
import secrets
import string
import subprocess
import sys

DUREE_DU_JETON = datetime.timedelta(days=14)
COLLECTIONS = (
    "utilisateurs",
    "taxons",
    "protocoles",
    "grille_stoc",
    "sites",
    "participations",
    "donnees",
    "fichiers",
)

# Le CSV d observations que le worker produit, et que la plateforme de test DECLARE faute de worker
# (ADR 5641) : memes colonnes, meme separateur, meme guillemetage que
# `vigiechiro/scripts/task_observations_csv.py` a la revision epinglee (#5747).
COLONNES_DU_CSV = (
    "nom du fichier",
    "temps_debut",
    "temps_fin",
    "frequence_mediane",
    "tadarida_taxon",
    "tadarida_probabilite",
    "tadarida_taxon_autre",
    "observateur_taxon",
    "observateur_probabilite",
    "validateur_taxon",
    "validateur_probabilite",
)
MIME_PROCESSING_EXTRA = "application/x-processing-extra"


def identifiant(collection: str, cle: str) -> str:
    """Un ObjectId stable, derive de la cle : 24 chiffres hexadecimaux."""
    return hashlib.sha1(f"plateforme-de-test:{collection}:{cle}".encode()).hexdigest()[:24]


def _oid(collection: str, cle: str) -> dict:
    return {"$oid": identifiant(collection, cle)}


def _date(texte: str) -> datetime.datetime:
    return datetime.datetime.fromisoformat(texte)


def documents(declaration: dict) -> dict[str, list[dict]]:
    """La declaration traduite en documents Mongo, identifiants en `{"$oid": ...}`. Pure."""
    cles = {c: {o["cle"] for o in declaration.get(c, [])} for c in COLLECTIONS}

    def ref(collection: str, cle: str, depuis: str) -> dict:
        if cle not in cles[collection]:
            raise ValueError(
                f"{depuis} cite {collection} « {cle} », que la declaration ne declare pas"
            )
        return _oid(collection, cle)

    reference = _date(declaration["date_de_reference"])

    def meta(collection: str, cle: str) -> dict:
        etag = hashlib.md5(f"{collection}:{cle}".encode()).hexdigest()
        return {
            "_id": _oid(collection, cle),
            "_created": reference,
            "_updated": reference,
            "_etag": etag,
        }

    def corps(objet: dict, sauf: tuple = ()) -> dict:
        return {k: v for k, v in objet.items() if k != "cle" and k not in sauf}

    rendu: dict[str, list[dict]] = {c: [] for c in COLLECTIONS}

    inscriptions: dict[str, list[dict]] = {}
    for i in declaration.get("inscriptions", []):
        inscriptions.setdefault(i["utilisateur"], []).append(
            {
                "protocole": ref(
                    "protocoles", i["protocole"], f"l inscription de {i['utilisateur']}"
                ),
                "valide": i["valide"],
                "date_inscription": reference,
            }
        )
    inconnus = set(inscriptions) - cles["utilisateurs"]
    if inconnus:
        raise ValueError(f"une inscription cite un utilisateur non declare : {sorted(inconnus)}")

    for u in declaration.get("utilisateurs", []):
        doc = meta("utilisateurs", u["cle"]) | corps(u)
        doc["protocoles"] = inscriptions.get(u["cle"], [])
        doc.setdefault("donnees_publiques", False)
        rendu["utilisateurs"].append(doc)

    for t in declaration.get("taxons", []):
        rendu["taxons"].append(meta("taxons", t["cle"]) | corps(t))

    for p in declaration.get("protocoles", []):
        doc = meta("protocoles", p["cle"]) | corps(p, ("taxon",))
        doc["taxon"] = ref("taxons", p["taxon"], f"le protocole {p['cle']}")
        rendu["protocoles"].append(doc)

    for g in declaration.get("grille_stoc", []):
        doc = meta("grille_stoc", g["cle"]) | corps(g, ("centre",))
        doc["centre"] = {"type": "Point", "coordinates": list(g["centre"])}
        rendu["grille_stoc"].append(doc)

    protocole_du_site = {}
    for s in declaration.get("sites", []):
        doc = meta("sites", s["cle"]) | corps(
            s, ("protocole", "observateur", "grille_stoc", "localites")
        )
        doc["localites"] = [_localite(loc) for loc in s.get("localites", [])]
        doc["protocole"] = ref("protocoles", s["protocole"], f"le site {s['cle']}")
        doc["observateur"] = ref("utilisateurs", s["observateur"], f"le site {s['cle']}")
        if "grille_stoc" in s:
            doc["grille_stoc"] = ref("grille_stoc", s["grille_stoc"], f"le site {s['cle']}")
        protocole_du_site[s["cle"]] = s["protocole"]
        rendu["sites"].append(doc)

    observateur_de = {}
    for p in declaration.get("participations", []):
        doc = meta("participations", p["cle"]) | corps(
            p, ("site", "observateur", "date_debut", "date_fin")
        )
        doc["site"] = ref("sites", p["site"], f"la participation {p['cle']}")
        doc["observateur"] = ref("utilisateurs", p["observateur"], f"la participation {p['cle']}")
        doc["protocole"] = _oid("protocoles", protocole_du_site[p["site"]])
        doc["date_debut"] = _date(p["date_debut"])
        # Les deux bornes, en dates : le schema de participation exige `date_fin` (#5746).
        doc["date_fin"] = _date(p["date_fin"])
        observateur_de[p["cle"]] = p["observateur"]
        rendu["participations"].append(doc)

    for d in declaration.get("donnees", []):
        doc = meta("donnees", d["cle"]) | corps(d, ("participation", "observations"))
        doc["participation"] = ref("participations", d["participation"], f"la donnee {d['cle']}")
        doc["proprietaire"] = _oid("utilisateurs", observateur_de[d["participation"]])
        doc["observations"] = [
            o | {"tadarida_taxon": ref("taxons", o["tadarida_taxon"], f"la donnee {d['cle']}")}
            for o in d.get("observations", [])
        ]
        rendu["donnees"].append(doc)

    for f in declaration.get("fichiers", []):
        participation = ref("participations", f["participation"], f"le fichier {f['cle']}")
        doc = meta("fichiers", f["cle"]) | {
            "titre": _titre_du_csv(f),
            "mime": MIME_PROCESSING_EXTRA,
            "proprietaire": _oid("utilisateurs", observateur_de[f["participation"]]),
            "disponible": True,
            "s3_id": _s3_id(f),
            "lien_participation": participation,
        }
        rendu["fichiers"].append(doc)

    return rendu


def _localite(declaree: dict) -> dict:
    """Une localite comme la plateforme la stocke : `[lat, lon]`, a rebours du GeoJSON.

    C est l ordre qu ecrit et relit `LocalitesVigieChiro` cote Companion. Une localite sans `point`
    reste sans geometrie, donc sans point lisible.
    """
    localite = {"nom": declaree["nom"], "representatif": False}
    if "point" in declaree:
        lat, lon = declaree["point"]
        localite["geometries"] = {
            "type": "GeometryCollection",
            "geometries": [{"type": "Point", "coordinates": [lat, lon]}],
        }
    return localite


def _titre_du_csv(fichier: dict) -> str:
    return (
        f"participation-{identifiant('participations', fichier['participation'])}-observations.csv"
    )


def _s3_id(fichier: dict) -> str:
    return f"plateforme-de-test/{fichier['cle']}"


def objets(declaration: dict) -> dict[str, str]:
    """Le contenu de chaque fichier declare, par `s3_id`. Pure.

    Le CSV d observations se derive des donnees declarees de sa participation, rangees par titre, au
    format du worker : `;`, guillemets sur ce qui n est pas un nombre, le taxon par son libelle court.
    """
    libelle = {t["cle"]: t["libelle_court"] for t in declaration.get("taxons", [])}
    rendu = {}
    for f in declaration.get("fichiers", []):
        tampon = io.StringIO()
        ecrivain = csv.writer(tampon, delimiter=";", quotechar='"', quoting=csv.QUOTE_NONNUMERIC)
        ecrivain.writerow(COLONNES_DU_CSV)
        donnees = sorted(
            (d for d in declaration.get("donnees", []) if d["participation"] == f["participation"]),
            key=lambda d: d["titre"],
        )
        for d in donnees:
            for o in d.get("observations", []):
                valeurs = {
                    "nom du fichier": d["titre"],
                    "temps_debut": o.get("temps_debut") or "0.0",
                    "temps_fin": o.get("temps_fin") or "0.0",
                    "tadarida_taxon": libelle[o["tadarida_taxon"]],
                }
                ecrivain.writerow([valeurs.get(c, o.get(c)) or "" for c in COLONNES_DU_CSV])
        rendu[_s3_id(f)] = tampon.getvalue()
    return rendu


def jeton() -> str:
    """Comme l API les frappe : trente-deux majuscules et chiffres."""
    return "".join(secrets.choice(string.ascii_uppercase + string.digits) for _ in range(32))


def materialise(declaration: dict, url_mongo: str, init_db: str | None) -> dict:
    """Ecrit les documents, pose les index, frappe un jeton par utilisateur. Rend jetons et ids."""
    from bson import ObjectId
    from pymongo import MongoClient

    def en_bson(valeur):
        if isinstance(valeur, dict):
            if set(valeur) == {"$oid"}:
                return ObjectId(valeur["$oid"])
            return {k: en_bson(v) for k, v in valeur.items()}
        if isinstance(valeur, list):
            return [en_bson(v) for v in valeur]
        return valeur

    base = MongoClient(url_mongo).get_default_database()
    if init_db:
        subprocess.run([sys.executable, init_db, "ensure_indexes"], check=True, capture_output=True)

    jetons = {}
    expiration = datetime.datetime.now(datetime.UTC).replace(tzinfo=None) + DUREE_DU_JETON
    for collection, docs in documents(declaration).items():
        for doc in docs:
            doc = en_bson(doc)
            if collection == "utilisateurs":
                cle = next(
                    u["cle"] for u in declaration["utilisateurs"] if u["email"] == doc["email"]
                )
                jetons[cle] = jeton()
                doc["tokens"] = {jetons[cle]: expiration}
            base[collection].replace_one({"_id": doc["_id"]}, doc, upsert=True)

    ids = {
        f"{c}:{o['cle']}": identifiant(c, o["cle"])
        for c in COLLECTIONS
        for o in declaration.get(c, [])
    }
    return {"jetons": jetons, "ids": ids, "objets": objets(declaration)}


def auto_test() -> int:
    """La moitie pure, sur la declaration du depot et sur des variantes fabriquees. Sans Mongo."""
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
    from _commun import cas_d_auto_test

    verifie, echecs = cas_d_auto_test()
    declaration = json.loads((pathlib.Path(__file__).parent / "etat-de-depart.json").read_text())
    docs = documents(declaration)

    verifie(
        "chaque role declare existe, une fois",
        lambda: sorted(u["role"] for u in docs["utilisateurs"]),
        ["Administrateur", "Observateur", "Validateur"],
    )
    verifie(
        "deux generations rendent les memes documents, octet pour octet",
        lambda: (
            json.dumps(documents(declaration), default=str, sort_keys=True)
            == json.dumps(docs, default=str, sort_keys=True)
        ),
        True,
    )
    (site,) = docs["sites"]
    vierge, traitee = docs["participations"]
    donnee = docs["donnees"][0]
    observatrice = next(u for u in docs["utilisateurs"] if u["role"] == "Observateur")
    verifie(
        "une participation porte le protocole de son site, sans qu on le repete",
        lambda: (vierge["protocole"], traitee["protocole"]),
        (site["protocole"], site["protocole"]),
    )
    verifie(
        "une donnee appartient a l observateur de sa participation, et la cite",
        lambda: (donnee["proprietaire"], donnee["participation"]),
        (traitee["observateur"], traitee["_id"]),
    )
    verifie(
        "l inscription validee devient l entree `protocoles` de l observatrice",
        lambda: [(p["protocole"], p["valide"]) for p in observatrice["protocoles"]],
        [(site["protocole"], True)],
    )
    verifie(
        "la participation traitee dit FINI, et porte au moins une observation",
        lambda: (traitee["traitement"]["etat"], len(donnee["observations"]) > 0),
        ("FINI", True),
    )
    verifie(
        "une participation porte ses deux bornes en dates, la fin apres le debut",
        lambda: [
            isinstance(x["date_fin"], datetime.datetime) and x["date_fin"] > x["date_debut"]
            for x in (vierge, traitee)
        ],
        [True, True],
    )
    lat, lon = site["localites"][0]["geometries"]["geometries"][0]["coordinates"]
    centre_lon, centre_lat = docs["grille_stoc"][0]["centre"]["coordinates"]
    verifie(
        "la localite porte son point en [lat, lon], dans son carre mais LOIN de son centre",
        # Un point AU centre rend son carre meme a r = 1 m, ce que la sonde de grille lit comme une
        # grille en polygones : mesure sur #5747. Un point de terrain en est a des centaines de metres.
        lambda: (
            300
            < math.hypot(
                (lat - centre_lat) * 111_320,
                (lon - centre_lon) * 111_320 * math.cos(math.radians(centre_lat)),
            )
            < 1_000
        ),
        True,
    )
    verifie(
        "la nuit traitee porte au moins deux donnees, pour la sonde du filtre",
        lambda: sum(d["participation"] == traitee["_id"] for d in docs["donnees"]) >= 2,
        True,
    )
    (fichier,) = docs["fichiers"]
    verifie(
        "le CSV est une piece jointe processing_extra de la nuit traitee, disponible",
        lambda: (fichier["mime"], fichier["lien_participation"], fichier["disponible"]),
        ("application/x-processing-extra", traitee["_id"], True),
    )
    verifie(
        "son titre est celui que le worker donne",
        lambda: fichier["titre"],
        f"participation-{traitee['_id']['$oid']}-observations.csv",
    )
    contenu = objets(declaration)
    lignes = contenu[fichier["s3_id"]].splitlines()
    verifie(
        "le contenu est range sous le s3_id du fichier", lambda: list(contenu), [fichier["s3_id"]]
    )
    verifie(
        "l entete est celle du worker, au separateur `;`",
        lambda: lignes[0].replace('"', "").split(";"),
        list(COLONNES_DU_CSV),
    )
    verifie(
        "une ligne par observation declaree, le taxon par son libelle court",
        lambda: [ligne.split(";")[4] for ligne in lignes[1:]],
        ['"Pippip"', '"Pippip"'],
    )
    verifie(
        "aucun jeton ne sort de la moitie pure",
        lambda: "tokens" in json.dumps(docs, default=str),
        False,
    )
    verifie(
        "aucune cle symbolique ne fuit dans un document",
        lambda: any("cle" in d for c in docs.values() for d in c),
        False,
    )

    fautive = json.loads(json.dumps(declaration))
    fautive["sites"][0]["protocole"] = "inexistant"

    def refus():
        try:
            documents(fautive)
        except ValueError as erreur:
            return "inexistant" in str(erreur)
        return False

    verifie("une reference a une cle non declaree est refusee, en la nommant", refus, True)
    return echecs()


def main(argv: list[str]) -> int:
    if "--auto-test" in argv[1:]:
        return auto_test()
    analyse = argparse.ArgumentParser(prog="amorcer.py", description=__doc__.splitlines()[0])
    analyse.add_argument("--declaration", required=True)
    analyse.add_argument("--mongo", required=True)
    analyse.add_argument("--init-db", default="/api/bin/init_db.py")
    options = analyse.parse_args(argv[1:])
    declaration = json.loads(pathlib.Path(options.declaration).read_text(encoding="utf-8"))
    init_db = options.init_db if pathlib.Path(options.init_db).is_file() else None
    print(json.dumps(materialise(declaration, options.mongo, init_db)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
