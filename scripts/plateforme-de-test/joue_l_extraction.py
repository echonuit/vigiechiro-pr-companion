#!/usr/bin/env python3
"""Joue l extraction des archives d une participation, par le code du serveur lui-meme (#5970).

    python joue_l_extraction.py --participation <id> --archives /tmp/archives [--prefixe P]
    python3 scripts/plateforme-de-test/joue_l_extraction.py --auto-test

Il tourne DANS le conteneur de l API, comme `amorcer.py`, et pour la meme raison : le code du serveur
y est deja, a la revision epinglee.

## La question qu il sert

Le repli manuel (ADR 5867) fait deposer des archives de TOUTE la nuit, sequences deja en ligne
comprises. Que fait le serveur d un son qu il recoit deux fois ? La plateforme de test n a pas de
worker (ADR 5641) : une archive deposee y reste une archive. Ce script lance la moitie du worker qui
repond, `extract_zipped_files_in_participation`, puis `Participation.load_pjs`, qui regroupe les
fichiers pour l analyse.

## Ce qui est du serveur, et ce qui est remplace

Le code joue est celui du serveur, sans retouche. Deux acces au stockage sont remplaces, parce qu ils
ne decident rien de ce qu on observe et que le conteneur de l API ne joint pas le faux S3 :

- `get_file_from_s3` rend l archive deposee dans `--archives` sous le titre du fichier, au lieu de la
  telecharger. Une archive absente de ce dossier est rendue introuvable, comme le ferait S3 ;
- `delete_fichier_and_s3` retire le document de l archive sans appeler S3.

## Ce qu il rend

Sur la sortie standard, le `bilan` en JSON : combien de fichiers WAV la participation portait avant
et apres, combien de titres distincts, lesquels sont en double, et combien de donnees l analyse en
tirerait. `bilan` est PUR, et c est lui que l auto-test eprouve, sans serveur ni Mongo.
"""

from __future__ import annotations

import argparse
import collections
import json
import pathlib
import sys


def bilan(
    titres_avant: list[str],
    titres_apres: list[str],
    bases_des_donnees: list[str],
    prefixe: str = "",
) -> dict:
    """Ce que l extraction a fait des sons dont le titre commence par `prefixe`.

    Le prefixe borne le compte aux sons d un seul banc : la participation d essai de la plateforme de
    test sert a plusieurs, et un compte de TOUS ses fichiers dependrait de l ordre des classes.
    """

    def du_banc(titre: str) -> bool:
        return titre.startswith(prefixe) and titre.endswith(".wav")

    wav_avant = [titre for titre in titres_avant if du_banc(titre)]
    wav_apres = collections.Counter(titre for titre in titres_apres if du_banc(titre))
    return {
        "wav_avant": len(wav_avant),
        "wav_apres": sum(wav_apres.values()),
        "titres_distincts": len(wav_apres),
        "en_double": sorted(titre for titre, nombre in wav_apres.items() if nombre > 1),
        "donnees": len({base for base in bases_des_donnees if base.startswith(prefixe)}),
    }


def joue(participation: str, archives: pathlib.Path, prefixe: str) -> dict:
    """Lance l extraction du serveur sur `participation`, et rend son bilan."""
    from bson import ObjectId
    from flask import g
    from vigiechiro import app
    from vigiechiro.scripts import task_participation as serveur

    identifiant = ObjectId(participation)

    class Reponse:
        def __init__(self, statut: int):
            self.status_code = statut

    def rend_l_archive(fichier, data_path=None):
        source = archives / fichier["titre"]
        if not source.is_file():
            return Reponse(404)
        pathlib.Path(data_path).write_bytes(source.read_bytes())
        return Reponse(200)

    with app.app_context(), app.test_request_context():
        g.request_user = {"role": "Administrateur"}
        fichiers = app.data.db.fichiers
        serveur.get_file_from_s3 = rend_l_archive
        serveur.delete_fichier_and_s3 = lambda fichier: fichiers.delete_one({"_id": fichier["_id"]})

        def titres() -> list[str]:
            return [
                fichier["titre"] for fichier in fichiers.find({"lien_participation": identifiant})
            ]

        avant = titres()
        en_cours = serveur.Participation(participation, [], True)
        serveur.extract_zipped_files_in_participation(en_cours)
        apres = titres()
        en_cours.load_pjs()
        bases = [base for base, donnee in en_cours.donnees.items() if donnee.wav]
        return bilan(avant, apres, bases, prefixe)


def auto_test() -> int:
    """La moitie pure, sur des titres fabriques. Sans serveur ni Mongo."""
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
    from _commun import cas_d_auto_test

    verifie, echecs = cas_d_auto_test()
    nuit = [f"Car130711-2026-Pass1-Z1-s{i}_000.wav" for i in range(4)]
    archive = "Car130711-2026-Pass1-Z1-1.zip"

    verifie(
        "rien en ligne avant l archive : chaque son une fois, aucun en double",
        lambda: bilan([archive], nuit, [t[:-4] for t in nuit]),
        {"wav_avant": 0, "wav_apres": 4, "titres_distincts": 4, "en_double": [], "donnees": 4},
    )
    verifie(
        "deux sons deja en ligne et rendus par l archive : ces deux-la sont en double, et nommes",
        lambda: bilan(nuit[:2] + [archive], nuit[:2] + nuit, [t[:-4] for t in nuit]),
        {
            "wav_avant": 2,
            "wav_apres": 6,
            "titres_distincts": 4,
            "en_double": nuit[:2],
            "donnees": 4,
        },
    )
    verifie(
        "un son en double ne fait qu une donnee : le compte des donnees ne suit pas celui des fichiers",
        lambda: bilan(nuit, nuit + nuit, [t[:-4] for t in nuit] * 2)["donnees"],
        4,
    )
    verifie(
        "une archive et un journal ne sont pas des sons : ils ne comptent nulle part",
        lambda: bilan([archive, "participation-1-logs"], [archive, "participation-1-logs"], []),
        {"wav_avant": 0, "wav_apres": 0, "titres_distincts": 0, "en_double": [], "donnees": 0},
    )
    verifie(
        "un prefixe borne le compte aux sons d un banc, fichiers et donnees",
        lambda: bilan(
            ["autre_0.wav"],
            ["autre_0.wav", "autre_0.wav", "mien_0.wav"],
            ["autre_0", "mien_0"],
            prefixe="mien_",
        ),
        {"wav_avant": 0, "wav_apres": 1, "titres_distincts": 1, "en_double": [], "donnees": 1},
    )
    return echecs()


def main(argv: list[str]) -> int:
    if "--auto-test" in argv[1:]:
        return auto_test()
    analyse = argparse.ArgumentParser(
        prog="joue_l_extraction.py", description=__doc__.splitlines()[0]
    )
    analyse.add_argument("--participation", required=True)
    analyse.add_argument("--archives", required=True)
    analyse.add_argument("--prefixe", default="")
    options = analyse.parse_args(argv[1:])
    print(json.dumps(joue(options.participation, pathlib.Path(options.archives), options.prefixe)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
